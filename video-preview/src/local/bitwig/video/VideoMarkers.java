package local.bitwig.video;

import bitwig.obf.*;
import com.bitwig.flt.document.core.iface.bMt;
import com.bitwig.flt.document.core.master.CmH;
import com.bitwig.ramona.core.dEy;
import java.util.*;

/** Native project markers; snapshots contain no native objects outside the UI thread. */
final class VideoMarkers {
    private static final String LOCK="patchbay-cue-absolute-v1:\n";
    private static bMt document;
    private static final IdentityHashMap<Aqt,Long> IDS=new IdentityHashMap<>();
    private static long serial;
    private static volatile List<VideoMarkerInfo> state=List.of();
    static List<VideoMarkerInfo> state(){return state;}
    static void refresh(bMt doc){
        if(doc!=document){document=doc;IDS.clear();}
        if(doc==null){state=List.of();return;}
        List<VideoMarkerInfo> next=new ArrayList<>();Set<Aqt> live=Collections.newSetFromMap(new IdentityHashMap<>());
        for(Object value:doc.XzC().G1p().beI())if(value instanceof Aqt marker){
            live.add(marker);long id=IDS.computeIfAbsent(marker,k->++serial);
            next.add(new VideoMarkerInfo(id,marker.beI(),doc.XzC().BBg(marker.ie()),locked(marker)));
        }
        IDS.keySet().retainAll(live);next.sort(Comparator.comparingDouble(VideoMarkerInfo::seconds));state=List.copyOf(next);
    }
    static void add(){add("");}
    static void add(String label){
        add(BitwigBridge.snapshot(),label);
    }
    static void add(BitwigBridge.Snapshot origin,String label){
        add(origin,label,true);
    }
    static boolean locked(Aqt marker){Object value=marker.Anf().getValue();return value instanceof String text&&text.startsWith(LOCK);}
    private static void lock(Aqt marker,boolean value){String text=(String)marker.Anf().getValue();if(text==null)text="";if(value&&!text.startsWith(LOCK))marker.Anf().setValue(LOCK+text);else if(!value&&text.startsWith(LOCK))marker.Anf().setValue(text.substring(LOCK.length()));}
    static void setLocked(bMt origin,long id,boolean value){run(origin,()->{
        refresh(origin);if(!(origin instanceof CmH master))return;
        for(var entry:IDS.entrySet())if(entry.getValue()==id){VideoTimeline.transaction(master,value?"Lock Video Cue Time":"Use Musical Cue Position",api->lock(entry.getKey(),value));return;}
        throw new IllegalStateException("标记已经删除或工程已切换。");
    });}
    static void add(BitwigBridge.Snapshot origin,String label,boolean absolute){
        double seconds=origin.seconds();
        run(origin.document(),()->{
            var doc=origin.document();var t=doc.XzC();
            Set<String> names=new HashSet<>();for(Object value:t.G1p().beI())if(value instanceof Aqt marker)names.add(marker.beI());
            int number=1;while(names.contains("Cue "+number))number++;
            String name=label==null||label.isBlank()?"Cue "+number:label.strip();
            if(!(doc instanceof CmH master))throw new IllegalStateException("当前工程不支持标记编辑。");
            if(!master.beI("Add Video Cue",new dEy[0]))throw new IllegalStateException("工程正在编辑，请稍后重试。");
            try{t.G1p().beI(aoP.beI(t.MgQ(seconds)),null,(java.util.function.Consumer<Object>)(value->{if(value instanceof Aqt marker){marker.beI(name);lock(marker,absolute);}}));}
            finally{master.xgz();}
        });
    }
    static void previous(){navigate(-1);}
    static void next(){navigate(1);}
    private static void navigate(int direction){
        var s=BitwigBridge.snapshot();run(s.document(),()->{
            refresh(s.document());VideoMarkerInfo found=null;
            for(var m:state){
                if(direction<0&&m.seconds()<s.seconds()-0.001)found=m;
                if(direction>0&&m.seconds()>s.seconds()+0.001){found=m;break;}
            }
            if(found!=null)s.document().XzC().sCA(s.document().XzC().MgQ(found.seconds()));
        });
    }
    static void edit(bMt origin,long id,String name,boolean locate){run(origin,()->{
        refresh(origin);
        for(var entry:IDS.entrySet())if(entry.getValue()==id){
            if(locate)origin.XzC().sCA(entry.getKey().ie());
            else if(name!=null&&!name.isBlank())entry.getKey().beI(name.strip());
            return;
        }
        throw new IllegalStateException("标记已经删除或工程已切换。");
    });}
    static void step(int direction){
        var s=BitwigBridge.snapshot();double fps=BitwigBridge.frameRate();
        if(s.playback().file().isEmpty() || !(fps>0)){BitwigBridge.error(new IllegalStateException("请先在 Video 面板载入视频，再逐帧定位。"));return;}
        run(s.document(),()->{
            var t=s.document().XzC();
            double source=s.seconds()-s.playback().offset();
            // Exact seeks display the first frame at/after the requested time.
            double target=s.playback().offset()+(Math.ceil(source*fps-0.00001)+direction)/fps;
            t.sCA(t.MgQ(Math.max(0,target)));
        });
    }
    private static void run(bMt origin,Runnable task){drf.EO().asyncExec(()->{
        try{if(origin==null||BitwigBridge.currentDocument()!=origin)return;task.run();refresh(origin);}
        catch(Throwable e){BitwigBridge.error(e);}
    });}
}
