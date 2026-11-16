package local.bitwig.video;

import bitwig.obf.*;
import java.util.*;

/** Four fixed native splitters: changing placement never detaches/recreates the DAW's widgets. */
final class VideoLayout {
    private static final List<Edge> EDGES=new ArrayList<>();
    private static final Map<aCZ,dBz> BUTTONS=new WeakHashMap<>();

    static dwF wrap(dwF original,aCZ window){
        dwF result=original;
        for(PreviewPlacement.Position side:new PreviewPlacement.Position[]{PreviewPlacement.Position.RIGHT,PreviewPlacement.Position.LEFT,PreviewPlacement.Position.BOTTOM,PreviewPlacement.Position.TOP})
            result=Edge.create(result,window,side);
        return result;
    }
    static dwF button(dwF original,aCZ window){
        dXL row=dXL.fpX();row.unz(0.5f);row.byf(4);
        if(original!=null)row.IvP(original);
        dBz button=dBz.wLf("Video");button.sCA(PreviewPlacement::toggle);row.IvP(button);BUTTONS.put(window,button);
        return row;
    }
    static void refresh(){for(Edge edge:List.copyOf(EDGES))edge.refresh();}
    static void resize(double value){for(Edge edge:List.copyOf(EDGES))if(edge.active())edge.beI(value);}
    static List<Map<String,Object>> inspect(){
        List<Map<String,Object>> result=new ArrayList<>();
        for(Edge e:List.copyOf(EDGES))if(e.window.dcC().Anf()==BitwigBridge.currentDocument())
            result.add(Map.of("position",e.side.key(),"active",e.active(),"collapsed",e.first?e.JTf():e.nhI(),"size",e.VsA(),"width",e.rtK(),"height",e.cBV(),"minimumWidth",e.cye().MgQ(),"minimumHeight",e.cye().BBg()));
        return result;
    }
    private static final class Edge extends dXi {
        final aCZ window;final PreviewPlacement.Position side;final boolean first;
        private boolean applying;
        private long appliedRevision=-1;
        Edge(aCZ window,PreviewPlacement.Position side){this.window=window;this.side=side;first=side==PreviewPlacement.Position.TOP||side==PreviewPlacement.Position.LEFT;}
        static Edge create(dwF main,aCZ window,PreviewPlacement.Position side){
            Edge e=new Edge(window,side);e.Anf(side==PreviewPlacement.Position.LEFT||side==PreviewPlacement.Position.RIGHT);e.I_();
            VideoWidget preview=VideoWidget.create(window,side);
            e.beI((dAc)(e.first?preview:main));e.Gef((dAc)(e.first?main:preview));
            e.beI(e.first?dXg.beI:dXg.Gef);
            // Collapsed edges occupy no strip, matching the native framework's view stacks.
            e.unz(0);e.wLf(e.first);e.BCC(!e.first);
            EDGES.add(e);e.refresh();return e;
        }
        boolean active(){return PreviewPlacement.shown()&&PreviewPlacement.position()==side&&window.BEX()==0;}
        void refresh(){
            if(M_())return;
            applying=true;
            try{
                boolean collapsed=!active();if(first)beI(collapsed);else Gef(collapsed);
                if(appliedRevision!=PreviewPlacement.revision()){
                    // Direct property update avoids clamping against the previous orientation's stale geometry.
                    sCA((double)PreviewPlacement.size(side));appliedRevision=PreviewPlacement.revision();
                }
            }finally{applying=false;}
        }
        @Override public void Gef(double previous){super.Gef(previous);if(!applying&&active())PreviewPlacement.size(side,VsA());}
        @Override public boolean BBg(double x,double y){return active()&&super.BBg(x,y);}
        @Override public void iz_(){EDGES.remove(this);super.iz_();}
    }
}
