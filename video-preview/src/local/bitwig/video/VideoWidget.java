package local.bitwig.video;

import bitwig.obf.*;
import com.bitwig.base.geom.uXa;
import com.bitwig.graphics.ijY;
import java.util.*;

final class VideoWidget extends dwF {
    private static final List<VideoWidget> WIDGETS=new ArrayList<>();
    private static dwF lastRoot;
    private final aCZ window;
    private final PreviewPlacement.Position side;
    private long repaintAt;
    private boolean hadVideo;
    private VideoWidget(aCZ window,PreviewPlacement.Position side){this.window=window;this.side=side;}
    static VideoWidget create(aCZ window,PreviewPlacement.Position side){VideoWidget w=new VideoWidget(window,side);w.I_();WIDGETS.add(w);return w;}
    @Override public dwF neH(){return this;}
    @Override public doI BZt(){return this;}
    @Override public uXa mM_(){return BBg(280,200);}
    @Override public uXa iI_(){return BBg(640,320);}
    @Override public uXa nk_(){return uXa.Gef();}
    @Override public void iz_(){WIDGETS.remove(this);super.iz_();}
    private int logicalWidth(){return Math.max(1,(int)(rtK()/Lna()));}
    private long header(){return wGt(VideoToolbar.height(logicalWidth()));}

    static boolean refresh(DockHost dock,boolean haveVideo){
        VideoLayout.refresh();
        if(dock.takeParentRepaint()&&lastRoot!=null&&!lastRoot.M_()&&lastRoot.vTn())lastRoot.OCx();
        PreviewPlacement.Position position=PreviewPlacement.position();
        for(VideoWidget w:List.copyOf(WIDGETS)){
            if(w.M_()||!w.window.vTn()||w.window.BEX()!=0||w.window.dcC().Anf()!=BitwigBridge.currentDocument())continue;
            if(!(w.window.Uxd() instanceof dwe top))continue;
            var handle=top.CnT();if(handle==null)continue;
            lastRoot=top;
            if(position==PreviewPlacement.Position.FLOAT){
                dock.update(new DockHost.Bounds(handle.sCA(),0,0,0,0,haveVideo&&PreviewPlacement.shown(),true));return true;
            }
            if(w.side!=position||!PreviewPlacement.shown()||!w.vTn()||w.jh_()==null)continue;
            var r=epr.beI(w,top);long header=w.header(),footer=w.wGt(24);
            var px=handle.beI(r.MgQ(),r.BBg()+header,r.unz(),Math.max(1,r.Anf()-header-footer));
            dock.update(new DockHost.Bounds(handle.sCA(),(int)px.MgQ(),(int)px.BBg(),(int)px.unz(),(int)px.Anf(),haveVideo&&r.Anf()>header+footer,false));
            if(w.hadVideo!=haveVideo){w.hadVideo=haveVideo;w.OCx();}
            long now=System.nanoTime();if(now-w.repaintAt>150_000_000){
                w.repaintAt=now;w.Gef(0,0,w.rtK(),Math.min(w.cBV(),header));
                w.Gef(0,Math.max(0,w.cBV()-footer),w.rtK(),Math.min(w.cBV(),footer));
            }
            return true;
        }
        dock.update(null);return false;
    }
    @Override public void beI(ijY g){
        double s=Lna();SZB text=new SZB(.88,.88,.88),button=new SZB(.17,.17,.17);
        g.beI(0,0,rtK(),cBV(),new SZB(.075,.075,.075));g.MgQ(12*s);
        for(VideoToolbar.Item item:VideoToolbar.items(logicalWidth())){
            g.beI(item.x()*s,item.y()*s,item.width()*s,item.height()*s,button);
            g.beI((item.x()+7)*s,(item.y()+18)*s,item.label(),text);
        }
        if(!hadVideo){
            String empty=BitwigBridge.snapshot().document()==null?"打开工程后拖入视频":VideoTimeline.active()?"此处没有可播放的视频":"拖入视频，或点击“选择 / 设置”";
            g.beI(12*s,header()+26*s,empty,text);
        }
        String status=BitwigBridge.playerStatus()+"   "+BitwigBridge.playerTime();
        int max=Math.max(8,(logicalWidth()-24)/10);if(status.length()>max)status=status.substring(0,max-1)+"…";
        g.beI(12*s,cBV()-7*s,status,text);
    }
    private VideoToolbar.Item hit(double x,double y){
        x/=Lna();y/=Lna();for(VideoToolbar.Item item:VideoToolbar.items(logicalWidth()))if(x>=item.x()&&x<item.x()+item.width()&&y>=item.y()&&y<item.y()+item.height())return item;return null;
    }
    @Override public boolean beI(double x,double y,eqv mods){return hit(x,y)!=null;}
    @Override public void beI(dLf event){VideoToolbar.Item item=hit(event.wLf(),event.dvk());if(item!=null)VideoToolbar.run(item.id());}
}
