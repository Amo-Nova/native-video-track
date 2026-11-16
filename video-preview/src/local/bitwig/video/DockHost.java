package local.bitwig.video;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.*;
import com.sun.jna.win32.StdCallLibrary;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentLinkedQueue;

/** One stable render HWND, reparented between a native dock and the owned floating frame. */
final class DockHost implements AutoCloseable {
    interface Windows extends StdCallLibrary {
        Windows API = Native.load("user32", Windows.class);
        WinDef.HWND CreateWindowExW(int ex, WString cls, WString title, int style,
            int x, int y, int width, int height, WinDef.HWND parent, Pointer menu, Pointer instance, Pointer param);
        boolean IsIconic(WinDef.HWND window);
    }
    record Bounds(long parent, int x, int y, int width, int height, boolean visible,boolean floating) {}
    private static final int CLIP_CHILDREN=0x02000000;
    private static final int NO_Z_ORDER=0x0004, NO_ACTIVATE=0x0010, NO_COPY_BITS=0x0100;
    private static final int SHOW=0x0040, HIDE=0x0080;
    private volatile Bounds bounds;
    private volatile long handle;
    private volatile boolean presented;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean repaintParent = new AtomicBoolean();
    private final Thread thread;
    private final ConcurrentLinkedQueue<Runnable> tasks=new ConcurrentLinkedQueue<>();
    private FloatingPreview floating;
    private WinDef.HWND child;
    private long parent;
    private volatile Bounds applied;
    private boolean addedClipping;
    private long lastFailure;

    DockHost() {
        thread = new Thread(this::run, "Video-docked-window"); thread.setDaemon(true); thread.start();
    }
    void update(Bounds value) { bounds = value; }
    long handle() { return handle; }
    boolean visible() { return presented && handle!=0; }
    boolean takeParentRepaint() { return repaintParent.getAndSet(false); }
    static WinDef.HWND hwnd(long value) { return new WinDef.HWND(new Pointer(value)); }
    void positionMenu(){tasks.add(()->{Bounds b=bounds;long owner=floating!=null&&b!=null&&b.floating()?floating.handle():b==null?parent:b.parent();FloatingPreview.popup(owner);});}
    void resetFloating(){tasks.add(()->{if(floating!=null)floating.restore();});}
    java.util.Map<String,Object> inspect(){
        Bounds b=applied;return java.util.Map.of("handle",handle,"presented",presented,"parent",parent,"bounds",b==null?"hidden":b.toString(),"floatingHandle",floating==null?0:floating.handle());
    }
    private void repaint(Bounds old) {
        if(old==null || !old.visible() || !User32.INSTANCE.IsWindow(hwnd(old.parent())))return;
        WinDef.RECT r=new WinDef.RECT();
        r.left=old.x();r.top=old.y();r.right=old.x()+old.width();r.bottom=old.y()+old.height();
        // Schedule the uncovered host area, without recursively painting the GPU child.
        User32.INSTANCE.RedrawWindow(hwnd(old.parent()),r,null,new WinDef.DWORD(0x0001|0x0040));
        repaintParent.set(true);
    }
    private static void restoreClipping(long parent,boolean added) {
        if(added && parent!=0 && User32.INSTANCE.IsWindow(hwnd(parent))) {
            int style=User32.INSTANCE.GetWindowLong(hwnd(parent),WinUser.GWL_STYLE);
            User32.INSTANCE.SetWindowLong(hwnd(parent),WinUser.GWL_STYLE,style&~CLIP_CHILDREN);
        }
    }
    private void run() {
        User32 user=User32.INSTANCE; WinUser.MSG message=new WinUser.MSG();
        try {
            while (!closed.get()) {
              try {
                while (user.PeekMessage(message,null,0,0,1)) { user.TranslateMessage(message); user.DispatchMessage(message); }
                for(Runnable task;(task=tasks.poll())!=null;)task.run();
                Bounds b=bounds;
                if(b!=null && b.parent()!=0 && user.IsWindow(hwnd(b.parent()))) {
                    FloatingPreview.Windows.API.SetThreadDpiAwarenessContext(FloatingPreview.Windows.API.GetWindowDpiAwarenessContext(hwnd(b.parent())));
                    if(b.floating()){
                        if(floating!=null&&!user.IsWindow(hwnd(floating.handle()))){floating.close();floating=null;}
                        if(floating==null)floating=new FloatingPreview(b.parent(),this::resizeFloating);
                        floating.update(b.parent(),PreviewPlacement.shown(),b.visible());
                        WinDef.RECT rect=floating.client();
                        b=new Bounds(floating.handle(),0,0,rect.right,rect.bottom,b.visible(),true);
                    }else if(floating!=null)floating.hide();
                    if(child!=null&&!user.IsWindow(child)){child=null;handle=0;presented=false;}
                    if(child==null || parent!=b.parent()) {
                        presented=false;
                        if(child!=null)user.ShowWindow(child,0);
                        repaint(applied);applied=null;
                        restoreClipping(parent,addedClipping);
                        long nextParent=b.parent();addedClipping=false;
                        int style=user.GetWindowLong(hwnd(b.parent()),WinUser.GWL_STYLE);
                        addedClipping=(style&CLIP_CHILDREN)==0;
                        if(addedClipping) {
                            user.SetWindowLong(hwnd(nextParent),WinUser.GWL_STYLE,style|CLIP_CHILDREN);
                            if((user.GetWindowLong(hwnd(nextParent),WinUser.GWL_STYLE)&CLIP_CHILDREN)==0)
                                throw new IllegalStateException("无法设置视频父窗口裁剪："+Kernel32.INSTANCE.GetLastError());
                        }
                        if(child==null)child=Windows.API.CreateWindowExW(0x08000000,new WString("STATIC"),new WString("Bitwig Video Surface"),
                            0x40000000|0x04000000|CLIP_CHILDREN|0x0004, b.x(),b.y(),Math.max(1,b.width()),Math.max(1,b.height()),hwnd(nextParent),null,null,null);
                        else {
                            user.SetParent(child,hwnd(nextParent));
                            if(!hwnd(nextParent).equals(user.GetParent(child)))throw new IllegalStateException("视频窗口切换父容器失败："+Kernel32.INSTANCE.GetLastError());
                        }
                        if(child==null)throw new IllegalStateException("无法建立视频面板："+Kernel32.INSTANCE.GetLastError());
                        parent=nextParent;handle=Pointer.nativeValue(child.getPointer());
                        BitwigBridge.MOD.log("Video child HWND="+handle+" parent="+parent+" parentClipAdded="+addedClipping);
                    }
                    WinDef.RECT client=new WinDef.RECT();
                    boolean fits=user.GetClientRect(hwnd(parent),client) && b.x()>=0 && b.y()>=0
                        && b.width()>0 && b.height()>0 && (long)b.x()+b.width()<=client.right
                        && (long)b.y()+b.height()<=client.bottom;
                    // A host resize can temporarily leave yesterday's layout in the snapshot.
                    // Never present that surface over a different part of the restored window.
                    if(!fits || Windows.API.IsIconic(hwnd(parent)) || !user.IsWindowVisible(hwnd(parent)))
                        b=new Bounds(b.parent(),b.x(),b.y(),b.width(),b.height(),false,b.floating());
                    if(!b.equals(applied)) {
                        presented=false;
                        // This is the HWND's own thread: apply geometry + visibility together.
                        // Copying saved client pixels here can carry stale GPU content into the host.
                        int flags=NO_Z_ORDER|NO_ACTIVATE|NO_COPY_BITS|(b.visible()?SHOW:HIDE);
                        if(!fits)flags|=0x0001|0x0002; // hide without applying invalid dimensions
                        if(!user.SetWindowPos(child,null,b.x(),b.y(),b.width(),b.height(),flags))
                            throw new IllegalStateException("无法更新视频面板："+Kernel32.INSTANCE.GetLastError());
                        repaint(applied);
                        applied=b;presented=b.visible();
                        BitwigBridge.MOD.log("Video dock bounds="+b);
                    }
                } else {
                    if(floating!=null)floating.hide();
                    if(child!=null && applied!=null){presented=false;user.ShowWindow(child,0);repaint(applied);applied=null;BitwigBridge.MOD.log("Video dock hidden");}
                }
              }catch(Throwable failure){
                presented=false;if(child!=null)user.ShowWindow(child,0);
                long now=System.nanoTime();if(now-lastFailure>2_000_000_000L){lastFailure=now;BitwigBridge.MOD.log("Video placement failed; restoring bottom dock",failure);PreviewPlacement.select("bottom");}
              }
                Thread.sleep(12);
            }
        } catch(InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } catch(Throwable e) { BitwigBridge.MOD.log("Video dock failed",e); }
        finally {
            presented=false;handle=0;
            if(child!=null) {user.DestroyWindow(child);repaint(applied);}
            if(floating!=null)floating.close();
            restoreClipping(parent,addedClipping);
        }
    }
    private void resizeFloating(WinDef.RECT rect){
        if(child==null||floating==null||parent!=floating.handle())return;
        Bounds b=bounds;if(b==null||!b.floating())return;
        User32.INSTANCE.SetWindowPos(child,null,0,0,Math.max(1,rect.right),Math.max(1,rect.bottom),NO_Z_ORDER|NO_ACTIVATE|NO_COPY_BITS);
        applied=null; // recheck visibility after the native move/size modal loop
    }
    public void close(){closed.set(true);thread.interrupt();}
}
