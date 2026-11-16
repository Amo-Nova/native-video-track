package local.bitwig.video;

import bitwig.obf.*;
import com.bitwig.flt.document.core.master.CmH;
import java.util.*;

/** Maintains seconds semantics inside the host's existing undo transaction.
 * No timer mutates the document. Saved native geometry plus the mode flag is
 * the persistent state; undo restores both the tempo and the native geometry. */
final class AbsoluteTime {
    private static final Map<dgR,State> STATES=new WeakHashMap<>();
    private static final Map<TKq,ClipState> COPIES=new WeakHashMap<>();
    private static boolean applying;
    private static long failures,flushes;
    private static String failure="";
    record ClipState(CmH doc,tIh map,double beat,double length,double offset,
                     double start,double end,double source,double audioEnd) {}
    record State(CmH doc,Map<TKq,ClipState> clips,Map<Aqt,Double> markers,tIh map) {}
    record Adjustment(TKq clip,tRn timeline,VideoTimeline.Media media,ClipState old,double source,double beat,double length,double offset) {}
    static String failure(){return failure;}
    static void end(dgR manager){if(!manager.PJ3())STATES.remove(manager);}
    static void undo(dgR manager){STATES.remove(manager);}

    static void begin(dgR manager){
        if(applying||manager.PJ3()||manager.GwM()||!(manager.BBg() instanceof CmH doc))return;
        try{STATES.put(manager,capture(doc));}catch(Throwable e){fail(e);}
    }
    static void flush(dgR manager){
        if(applying||!manager.PJ3()||manager.GwM())return;
        State state=STATES.get(manager);if(state==null)return;
        apply(manager,state);
        try{STATES.put(manager,capture(state.doc()));}catch(Throwable e){fail(e);}
    }
    private static void apply(dgR manager,State state){
        if(applying||manager.GwM())return;
        applying=true;dgR.ogS();
        try{
            CmH doc=state.doc();var transport=doc.jPe();
            // Automation points can already be committed while the transport's
            // integrated map is waiting for end-of-event invalidation. Refresh
            // that native cache in this same undo transaction before converting
            // seconds back to beats. Never snapshot a new curve with old geometry.
            transport.mBe();transport.lTh();
            List<Adjustment> adjustments=new ArrayList<>();
            for(var entry:VideoTimeline.timedEntries(doc)){
                TKq clip=entry.clip();if(!entry.media().absolute())continue;
                ClipState old=state.clips().get(clip);if(old==null)old=COPIES.remove(clip);
                if(old==null||old.doc()!=doc)continue;
                double beat=clip.ie(),length=clip.ckt(),offset=clip.dD_();
                double start=old.map().beI(beat);
                boolean move=near(length,old.length())&&near(offset,old.offset());
                double end=move?start+old.end()-old.start():old.map().beI(beat+length);
                double source=old.source()+old.map().beI(old.beat()+offset-old.offset())-old.start();
                if(!(Double.isFinite(start)&&Double.isFinite(end)&&Double.isFinite(source)&&end>start))throw new IllegalStateException("绝对时间范围无效");
                double targetBeat=transport.MgQ(start),targetLength=transport.MgQ(end)-targetBeat;
                // Nothing to change for ordinary edits within a constant tempo.
                if(near(beat,targetBeat)&&near(length,targetLength)&&near(VideoTimeline.sourceStart(doc,clip,entry.media()),source))continue;
                poA note=rawNote(clip);
                double base=note==null?0:note.beI(0,clip.beI(0).lZ_(),clip.beI(0).sCA(note.ie()));
                double noteBeat=note==null?0:note.ie();
                double targetOffset=targetBeat+noteBeat-transport.MgQ(start-(source-base));
                adjustments.add(new Adjustment(clip,(tRn)clip.byf().byf(),entry.media(),old,source,targetBeat,targetLength,targetOffset));
            }
            // Use the same detach/edit/reinsert pattern as native bulk stretching.
            // Identity survives continuous mouse gestures; undo tracks the originals.
            for(var r:adjustments)r.timeline().beI((gZu)r.clip(),false);
            for(var r:adjustments){
                TKq clip=r.clip();
                if(clip instanceof DyF&&clip.zVC().sCA().beI()>1)clip.beI(clip.zVC().wBT());
                clip.scr(aoP.beI(r.beat()));clip.Bqk(aoP.unz(r.length()));clip.LRq(r.offset());
                r.timeline().sCA((gZu)clip,er.Gef(r.timeline().beI(),clip.ie(),true));
                poA note=rawNote(clip);
                if(note!=null&&Double.isFinite(r.old().audioEnd())){
                    var context=clip.beI(0);
                    double duration=note.Gef(r.old().audioEnd(),context.lZ_(),context.sCA(note.ie()));
                    if(duration>0&&!near(note.ckt(),duration))note.Bqk(aoP.unz(duration));
                }
                double actual=VideoTimeline.sourceStart(doc,clip,r.media());
                if(Math.abs(actual-r.source())>0.00002)throw new IllegalStateException("音画源入点未能保持："+actual+" / "+r.source());
            }
            for(var value:doc.XzC().G1p().beI())if(value instanceof Aqt marker&&VideoMarkers.locked(marker)){
                Double before=state.markers().get(marker);if(before==null)continue;
                double seconds=state.map().beI(marker.ie());
                double beat=transport.MgQ(seconds);
                if(!near(marker.ie(),beat))((gZu)marker).scr(aoP.beI(beat));
            }
            flushes++;failure="";
        }catch(Throwable e){fail(e);}finally{dgR.LRq();applying=false;}
    }
    static void copied(TKq source,TKq target){
        if(applying||source==target)return;
        try{
            var media=VideoTimeline.media(source);if(media==null||!media.absolute())return;
            transfer(source,target);
        }catch(Throwable e){fail(e);}
    }
    static void transfer(TKq source,TKq target){
        var doc=source.QfO();if(doc==null)return;
        var media=VideoTimeline.media(source);if(media==null)return;
        tIh map=copyMap(doc);
        COPIES.put(target,snapshot(doc,source,media,map));
    }
    private static State capture(CmH doc){
        Map<TKq,ClipState> clips=new IdentityHashMap<>();Map<Aqt,Double> markers=new IdentityHashMap<>();
        var entries=VideoTimeline.timedEntries(doc);
        if(entries.stream().noneMatch(e->e.media().absolute())&&doc.XzC().G1p().beI().stream().noneMatch(v->v instanceof Aqt a&&VideoMarkers.locked(a)))return new State(doc,clips,markers,null);
        tIh map=copyMap(doc);
        for(var e:entries)if(e.media().absolute())clips.put(e.clip(),snapshot(doc,e.clip(),e.media(),map));
        for(var v:doc.XzC().G1p().beI())if(v instanceof Aqt a&&VideoMarkers.locked(a))markers.put(a,map.beI(a.ie()));
        return new State(doc,clips,markers,map);
    }
    private static ClipState snapshot(CmH doc,TKq clip,VideoTimeline.Media media,tIh map){
        poA note=rawNote(clip);double audioEnd=Double.NaN;
        if(note!=null){var context=clip.beI(0);audioEnd=note.beI(note.ckt(),context.lZ_(),context.sCA(note.ie()));}
        return new ClipState(doc,map,clip.ie(),clip.ckt(),clip.dD_(),map.beI(clip.ie()),map.beI(clip.ie()+clip.ckt()),VideoTimeline.sourceStart(doc,clip,media),audioEnd);
    }
    private static tIh copyMap(CmH doc){tIh map=tIh.unz();map.beI(doc.jPe().KrW(),doc.jPe().iqe());return map;}
    static poA rawNote(TKq clip){
        if(clip.Xit())throw new IllegalStateException("绝对时间模式暂不支持片段循环，请先关闭片段循环。");
        if(!(clip instanceof DyF audio))return null;
        poA found=null;
        for(Object lane:audio.EDy().scr())if(lane instanceof B1 events)for(Object value:events.beI())if(value instanceof poA note){
            if(found!=null||note.jTs()!=j2X.beI||note.gNZ())throw new IllegalStateException("绝对时间模式需要单个原速、正向音频事件；原片段保留。");
            found=note;
        }
        if(found==null)throw new IllegalStateException("片段没有可锁定的原声音频。");return found;
    }
    private static boolean near(double a,double b){return Math.abs(a-b)<1e-8;}
    private static void fail(Throwable e){failures++;failure="时间锁定未完成："+(e.getMessage()==null?e.toString():e.getMessage());BitwigBridge.MOD.log(failure,e);}
}
