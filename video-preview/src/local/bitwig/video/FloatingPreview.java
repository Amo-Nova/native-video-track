package local.bitwig.video;

import com.sun.jna.*;
import com.sun.jna.platform.win32.*;
import com.sun.jna.win32.StdCallLibrary;
import java.util.function.Consumer;

/** Native owned window. All access stays on DockHost's message thread. */
final class FloatingPreview implements AutoCloseable {
    interface Windows extends StdCallLibrary {
        Windows API=Native.load("user32",Windows.class);
        WinDef.HMENU CreateMenu();WinDef.HMENU CreatePopupMenu();
        boolean AppendMenuW(WinDef.HMENU menu,int flags,BaseTSD.ULONG_PTR id,WString text);
        boolean DestroyMenu(WinDef.HMENU menu);boolean SetMenu(WinDef.HWND window,WinDef.HMENU menu);
        int TrackPopupMenu(WinDef.HMENU menu,int flags,int x,int y,int reserved,WinDef.HWND owner,Pointer rect);
        int CheckMenuItem(WinDef.HMENU menu,int id,int flags);
        boolean SetWindowTextW(WinDef.HWND window,WString text);
        int GetDpiForWindow(WinDef.HWND window);
        Pointer GetWindowDpiAwarenessContext(WinDef.HWND window);
        Pointer SetThreadDpiAwarenessContext(Pointer context);
        WinDef.HCURSOR LoadCursorW(WinDef.HINSTANCE instance,Pointer name);
        boolean IsZoomed(WinDef.HWND window);
    }
    @Structure.FieldOrder({"reserved","maxSize","maxPosition","minTrack","maxTrack"})
    public static class MinMax extends Structure {
        public WinDef.POINT reserved,maxSize,maxPosition,minTrack,maxTrack;
        public MinMax(Pointer pointer){super(pointer);super.read();}
    }
    private static final String CLASS_NAME="PatchbayVideoPreviewWindow050";
    private static final User32 USER=User32.INSTANCE;
    private final WinUser.WindowProc procedure=this::message;
    private final Consumer<WinDef.RECT> resized;
    private WinDef.HWND window,label;
    private WinDef.HMENU menu,positions;
    private final WinDef.HINSTANCE instance=Kernel32.INSTANCE.GetModuleHandle(null);
    private long owner;
    private boolean positioned,closing;
    private long textAt;
    private String text="";
    FloatingPreview(long owner,Consumer<WinDef.RECT> resized){
        this.owner=owner;this.resized=resized;
        WinUser.WNDCLASSEX cls=new WinUser.WNDCLASSEX();cls.lpfnWndProc=procedure;cls.hInstance=instance;cls.lpszClassName=CLASS_NAME;
        cls.hbrBackground=new WinDef.HBRUSH(Pointer.createConstant(5));cls.hCursor=Windows.API.LoadCursorW(null,Pointer.createConstant(32512));
        if(USER.RegisterClassEx(cls).intValue()==0)throw new IllegalStateException("视频浮窗注册失败："+Kernel32.INSTANCE.GetLastError());
        try {
        window=DockHost.Windows.API.CreateWindowExW(0x80,new WString(CLASS_NAME),new WString("Bitwig Video"),0x00CF0000|0x02000000,
            100,100,960,620,DockHost.hwnd(owner),null,instance.getPointer(),null);
        if(window==null)throw new IllegalStateException("视频浮窗创建失败："+Kernel32.INSTANCE.GetLastError());
        menu=Windows.API.CreateMenu();positions=positionMenu();
        Windows.API.AppendMenuW(menu,0x10,new BaseTSD.ULONG_PTR(Pointer.nativeValue(positions.getPointer())),new WString("位置"));
        WinDef.HMENU markers=Windows.API.CreatePopupMenu();
        item(markers,201,"插入标记");item(markers,202,"上一标记");item(markers,203,"下一标记");item(markers,204,"标记列表");item(markers,205,"−1帧");item(markers,206,"+1帧");
        Windows.API.AppendMenuW(menu,0x10,new BaseTSD.ULONG_PTR(Pointer.nativeValue(markers.getPointer())),new WString("打点 / 逐帧"));
        item(menu,200,"选择 / 设置");Windows.API.SetMenu(window,menu);
        label=DockHost.Windows.API.CreateWindowExW(0,new WString("STATIC"),new WString("打开工程后拖入视频"),0x40000000|0x10000000|1|0x200,
            0,0,600,300,window,null,instance.getPointer(),null);
        restore();
        }catch(Throwable failure){close();throw failure;}
    }
    static void item(WinDef.HMENU menu,int id,String name){Windows.API.AppendMenuW(menu,0,new BaseTSD.ULONG_PTR(id),new WString(name));}
    static WinDef.HMENU positionMenu(){
        WinDef.HMENU menu=Windows.API.CreatePopupMenu();
        for(var p:PreviewPlacement.Position.values()){
            item(menu,100+p.ordinal(),p.label);
            if(p==PreviewPlacement.position())Windows.API.CheckMenuItem(menu,100+p.ordinal(),8);
        }
        Windows.API.AppendMenuW(menu,0x800,new BaseTSD.ULONG_PTR(0),null);item(menu,106,"恢复默认布局");item(menu,107,"隐藏视频画面");return menu;
    }
    static void popup(long owner){
        if(owner==0)return;WinDef.HMENU menu=positionMenu();WinDef.POINT point=new WinDef.POINT();USER.GetCursorPos(point);
        try{int command=Windows.API.TrackPopupMenu(menu,0x100|2,point.x,point.y,0,DockHost.hwnd(owner),null);if(command!=0)command(command);}
        finally{Windows.API.DestroyMenu(menu);}
    }
    static void command(int command){PreviewPlacement.onUi(()->{
        if(command>=100&&command<105)PreviewPlacement.select(PreviewPlacement.Position.values()[command-100].key());
        else switch(command){
            case 106 -> PreviewPlacement.reset();case 107 -> BitwigBridge.hidePreview();
            case 200,204 -> BitwigBridge.showControls();case 201 -> VideoMarkers.add();
            case 202 -> VideoMarkers.previous();case 203 -> VideoMarkers.next();
            case 205 -> VideoMarkers.step(-1);case 206 -> VideoMarkers.step(1);
        }
    });}
    long handle(){return window==null?0:Pointer.nativeValue(window.getPointer());}
    WinDef.RECT client(){WinDef.RECT r=new WinDef.RECT();USER.GetClientRect(window,r);return r;}
    void update(long newOwner,boolean shown,boolean haveVideo){
        if(owner!=newOwner){owner=newOwner;USER.SetWindowLongPtr(window,-8,new Pointer(owner));}
        boolean visible=shown&&USER.IsWindow(DockHost.hwnd(owner))&&USER.IsWindowVisible(DockHost.hwnd(owner))&&!DockHost.Windows.API.IsIconic(DockHost.hwnd(owner))&&USER.IsWindowEnabled(DockHost.hwnd(owner));
        if(USER.IsWindowVisible(window)!=visible)USER.ShowWindow(window,visible?4:0);
        for(var p:PreviewPlacement.Position.values())Windows.API.CheckMenuItem(positions,100+p.ordinal(),p==PreviewPlacement.position()?8:0);
        WinDef.RECT r=client();USER.SetWindowPos(label,null,0,0,Math.max(1,r.right),Math.max(1,r.bottom),0x4|0x10|(haveVideo?0x80:0x40));
        long now=System.nanoTime();if(now-textAt>250_000_000){
            textAt=now;String next=BitwigBridge.playerStatus();if(!next.equals(text)){text=next;Windows.API.SetWindowTextW(label,new WString(next));}
        }
    }
    void hide(){if(window!=null)USER.ShowWindow(window,0);}
    void restore(){
        int x=Math.max(-1_000_000,Math.min(1_000_000,PreviewPlacement.floating("x",100))),y=Math.max(-1_000_000,Math.min(1_000_000,PreviewPlacement.floating("y",100))),w=PreviewPlacement.floating("width",960),h=PreviewPlacement.floating("height",620);
        WinDef.RECT rect=new WinDef.RECT();rect.left=x;rect.top=y;rect.right=x+Math.max(352,Math.min(7680,w));rect.bottom=y+Math.max(240,Math.min(4320,h));
        clamp(rect);USER.SetWindowPos(window,null,rect.left,rect.top,rect.right-rect.left,rect.bottom-rect.top,0x4|0x10|0x100);positioned=true;
    }
    private void clamp(WinDef.RECT rect){
        WinUser.MONITORINFO info=new WinUser.MONITORINFO();
        if(!USER.GetMonitorInfo(USER.MonitorFromRect(rect,1),info).booleanValue())return;
        WinDef.RECT work=info.rcWork;int w=Math.min(rect.right-rect.left,work.right-work.left),h=Math.min(rect.bottom-rect.top,work.bottom-work.top);
        rect.left=Math.max(work.left,Math.min(rect.left,work.right-w));rect.top=Math.max(work.top,Math.min(rect.top,work.bottom-h));rect.right=rect.left+w;rect.bottom=rect.top+h;
    }
    void remember(){
        if(!positioned||window==null||DockHost.Windows.API.IsIconic(window)||Windows.API.IsZoomed(window))return;
        WinDef.RECT r=new WinDef.RECT();if(USER.GetWindowRect(window,r))PreviewPlacement.floating(r.left,r.top,r.right-r.left,r.bottom-r.top);
    }
    private WinDef.LRESULT message(WinDef.HWND hwnd,int message,WinDef.WPARAM w,WinDef.LPARAM l){
        try{
            switch(message){
                case 0x10:
                    remember();USER.ShowWindow(hwnd,0);PreviewPlacement.show(false);return new WinDef.LRESULT(0);
                case 0x111:command(w.intValue()&0xffff);return new WinDef.LRESULT(0);
                case 0x24:{MinMax limits=new MinMax(new Pointer(l.longValue()));int dpi=Windows.API.GetDpiForWindow(hwnd);limits.minTrack.x=352*Math.max(96,dpi)/96;limits.minTrack.y=240*Math.max(96,dpi)/96;((Structure)limits).write();break;}
                case 0x5:if(window!=null&&!closing)resized.accept(client());break;
                case 0x232:remember();break;
                case 0x2e0:{Pointer r=new Pointer(l.longValue());int x=r.getInt(0),y=r.getInt(4);USER.SetWindowPos(hwnd,null,x,y,r.getInt(8)-x,r.getInt(12)-y,0x4|0x10|0x100);break;}
                case 0x7e:if(window!=null){remember();restore();}break;
            }
        }catch(Throwable e){BitwigBridge.MOD.log("Video floating window event failed",e);}
        return USER.DefWindowProc(hwnd,message,w,l);
    }
    public void close(){closing=true;remember();if(window!=null){USER.DestroyWindow(window);window=null;}USER.UnregisterClass(CLASS_NAME,instance);}
}
