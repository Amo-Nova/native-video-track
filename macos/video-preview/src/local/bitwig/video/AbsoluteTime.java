package local.bitwig.video;

import bitwig.obf.*;
import com.bitwig.flt.document.core.master.CmH;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Maintains seconds semantics for absolute-time clips.
 * No timer mutates the document. Saved native geometry plus the mode flag is
 * the persistent state; undo restores both the tempo and the native geometry.
 *
 * 0.5.0-dev.5/dev.6 bisect modes, read live from
 * %LOCALAPPDATA%/BitwigVideo/mods/video-preview/absolute-mode.txt:
 *   off      = simulate only, never mutate the document;
 *   clips    = move clips, skip audio-note resize and marker moves;
 *   full     = complete behavior inside the host's time-edit transaction;
 *   deferred = complete behavior, but posted to the UI thread to run AFTER the
 *              current event finishes, outside the time-edit transaction.
 * Missing or empty file means off.
 *
 * Crash history: mutating the document from inside the time-edit transaction
 * (dgR hooks) made Bitwig defer its tempo-widget commit to runAtEndOfCurrentEvent;
 * that deferred path (CmH.unz -> TzU.PqZ -> ClS.Gef) runs with no current event
 * and Bitwig 6.1.7 NPE-crashes there. deferred moves our mutations out of the
 * transaction to avoid triggering the deferral. */
final class AbsoluteTime {
    private static final Map<dgR,State> STATES=new WeakHashMap<>();
    private static final Map<TKq,ClipState> COPIES=new WeakHashMap<>();
    private static boolean applying;
    private static final AtomicBoolean PENDING=new AtomicBoolean();
    private static long failures,flushes;
    private static String failure="";
    record ClipState(CmH doc,tIh map,double beat,double length,double offset,
                     double start,double end,double source,double audioEnd) {}
    record State(CmH doc,Map<TKq,ClipState> clips,Map<Aqt,Double> markers,tIh map) {}
    record Adjustment(TKq clip,tRn timeline,VideoTimeline.Media media,ClipState old,double source,
                      double beat,double length,double offset,poA note,double noteDuration) {}
    static String failure(){return failure;}
    static void end(dgR manager){
        if(!manager.PJ3()){
            State state=STATES.remove(manager);
            // deferred: one adjustment per gesture, computed once from the ORIGINAL
            // pre-change capture, posted to run after the current event finishes.
            // Running it inside the transaction (full) made Bitwig defer its
            // tempo-widget commit and crash in the no-event context (see header).
            if(state!=null&&"deferred".equals(mode())&&!manager.GwM())
                drf.EO().asyncExec(()->{try{applyDirect(state);}catch(Throwable e){fail(e);}});
        }
    }
    static void undo(dgR manager){STATES.remove(manager);}

    static void begin(dgR manager){
        if(applying||manager.PJ3()||manager.GwM()||!(manager.BBg() instanceof CmH doc))return;
        try{STATES.put(manager,capture(doc));}catch(Throwable e){fail(e);}
    }
    static void flush(dgR manager){
        if(applying||!manager.PJ3()||manager.GwM())return;
        State state=STATES.get(manager);if(state==null)return;
        if("deferred".equals(mode()))return; // adjusted once at end(), from this original capture
        apply(manager,state);
        try{STATES.put(manager,capture(state.doc()));}catch(Throwable e){fail(e);}
    }
    /** Inside the host's time-edit transaction: suspend notifications while mutating. */
    private static void apply(dgR manager,State state){
        if(applying||manager.GwM())return;
        applying=true;dgR.ogS();
        try{applyDirect(state);}finally{dgR.LRq();applying=false;}
    }
    /** The adjustment pass itself. Per-clip atomic with rollback: a failing clip is
     * restored to its old geometry and reinserted before the pass continues, so the
     * document never holds a detached, half-mutated clip. Every step is logged. */
    private static void applyDirect(State state){
        long moved=0,rolledBack=0,skipped=0;
        String mode=mode();
        boolean doNote=!mode.equals("clips"),doMarkers=!mode.equals("clips");
        try{
            CmH doc=state.doc();var transport=doc.jPe();
            List<Adjustment> adjustments=new ArrayList<>();
            for(var entry:VideoTimeline.timedEntries(doc)){
                TKq clip=entry.clip();if(!entry.media().absolute())continue;
                ClipState old=state.clips().get(clip);if(old==null)old=COPIES.remove(clip);
                if(old==null||old.doc()!=doc)continue;
                // Per-clip prechecks: one invalid clip is skipped with a reason,
                // it must not abort the whole tempo adjustment.
                try{
                    double beat=clip.ie(),length=clip.ckt(),offset=clip.dC_();
                    double start=old.map().beI(beat);
                    boolean move=near(length,old.length())&&near(offset,old.offset());
                    double end=move?start+old.end()-old.start():old.map().beI(beat+length);
                    double source=old.source()+old.map().beI(old.beat()+offset-old.offset())-old.start();
                    if(!(Double.isFinite(start)&&Double.isFinite(end)&&Double.isFinite(source)&&end>start))
                        throw new IllegalStateException("绝对时间范围无效");
                    double targetBeat=transport.MgQ(start),targetLength=transport.MgQ(end)-targetBeat;
                    // Nothing to change for ordinary edits within a constant tempo.
                    if(near(beat,targetBeat)&&near(length,targetLength)&&near(VideoTimeline.sourceStart(doc,clip,entry.media()),source))continue;
                    poA note=rawNote(clip);
                    double base=note==null?0:note.beI(0,clip.beI(0).lY_(),clip.beI(0).sCA(note.ie()));
                    double noteBeat=note==null?0:note.ie();
                    double targetOffset=targetBeat+noteBeat-transport.MgQ(start-(source-base));
                    // Pre-compute the note duration so the mutation below cannot fail halfway.
                    double noteDuration=Double.NaN;
                    if(note!=null&&Double.isFinite(old.audioEnd())){
                        var context=clip.beI(0);
                        noteDuration=note.Gef(old.audioEnd(),context.lY_(),context.sCA(note.ie()));
                    }
                    adjustments.add(new Adjustment(clip,(tRn)clip.byf().byf(),entry.media(),old,source,targetBeat,targetLength,targetOffset,note,noteDuration));
                }catch(Throwable skip){
                    skipped++;
                    BitwigBridge.MOD.log("时间锁定跳过片段："+(skip.getMessage()==null?skip.toString():skip.getMessage()));
                }
            }
            if(!adjustments.isEmpty())BitwigBridge.MOD.log("时间锁定 apply：候选片段="+adjustments.size()+" mode="+mode);
            if(mode.equals("off")){
                for(var r:adjustments)
                    BitwigBridge.MOD.log("时间锁定模拟：beat "+r.old().beat()+"→"+r.beat()+" length "+r.old().length()+"→"+r.length()+" offset→"+r.offset()+" audio→"+r.noteDuration());
                flushes++;failure="";
                return;
            }
            for(var r:adjustments){
                TKq clip=r.clip();
                r.timeline().beI((gZu)clip,false);
                try{
                    if(clip instanceof DyF&&clip.zVC().sCA().beI()>1)clip.beI(clip.zVC().wBT());
                    BitwigBridge.MOD.log("时间锁定移动片段：beat "+r.old().beat()+"→"+r.beat()+" length "+r.old().length()+"→"+r.length()+" offset→"+r.offset());
                    clip.scr(aoP.beI(r.beat()));clip.Bqk(aoP.unz(r.length()));clip.LRq(r.offset());
                    r.timeline().sCA((gZu)clip,er.Gef(r.timeline().beI(),clip.ie(),true));
                    if(doNote&&r.note()!=null&&Double.isFinite(r.noteDuration())&&r.noteDuration()>0&&!near(r.note().ckt(),r.noteDuration())){
                        r.note().Bqk(aoP.unz(r.noteDuration()));
                        BitwigBridge.MOD.log("时间锁定音频时长→"+r.noteDuration());
                    }
                    double actual=VideoTimeline.sourceStart(doc,clip,r.media());
                    if(Math.abs(actual-r.source())>0.00002)
                        throw new IllegalStateException("音画源入点未能保持："+actual+" / "+r.source());
                    moved++;
                }catch(Throwable clipFailure){
                    rolledBack++;
                    rollback(r);
                    BitwigBridge.MOD.log("时间锁定回滚片段："+(clipFailure.getMessage()==null?clipFailure.toString():clipFailure.getMessage()));
                }
            }
            // Move time-locked markers. Snapshot first: moving a marker mutates the
            // live collection that was being iterated (iteration-during-mutation).
            if(doMarkers){
                List<Object> locked=new ArrayList<>();
                for(var value:doc.XzC().G1p().beI())if(value instanceof Aqt marker&&lockedVideoMarker(marker))locked.add(marker);
                for(Object value:locked){
                    Aqt marker=(Aqt)value;
                    Double before=state.markers().get(marker);if(before==null)continue;
                    double seconds=state.map().beI(marker.ie());
                    double beat=transport.MgQ(seconds);
                    if(!near(marker.ie(),beat)){((gZu)marker).scr(aoP.beI(beat));BitwigBridge.MOD.log("时间锁定移动标记→beat "+beat);}
                }
            }
            flushes++;failure="";
            BitwigBridge.MOD.log("时间锁定 apply 完成：moved="+moved+" rolledBack="+rolledBack+" skipped="+skipped+" mode="+mode);
        }catch(Throwable e){fail(e);}
    }
    /** Best-effort restore of one clip to its pre-adjustment geometry, reinserted on its timeline. */
    private static void rollback(Adjustment r){
        try{
            TKq clip=r.clip();ClipState old=r.old();
            clip.scr(aoP.beI(old.beat()));clip.Bqk(aoP.unz(old.length()));clip.LRq(old.offset());
            r.timeline().sCA((gZu)clip,er.Gef(r.timeline().beI(),clip.ie(),true));
            poA note=r.note();
            if(note!=null&&Double.isFinite(old.audioEnd())){
                var context=clip.beI(0);
                double duration=note.Gef(old.audioEnd(),context.lY_(),context.sCA(note.ie()));
                if(duration>0)note.Bqk(aoP.unz(duration));
            }
        }catch(Throwable ignored){}
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
    /** A project cue marker carrying the absolute-time lock prefix (was VideoMarkers.locked). */
    private static boolean lockedVideoMarker(Aqt marker){
        Object value=marker.Anf().getValue();
        return value instanceof String text&&text.startsWith("patchbay-cue-absolute-v1:\n");
    }
    private static State capture(CmH doc){
        Map<TKq,ClipState> clips=new IdentityHashMap<>();Map<Aqt,Double> markers=new IdentityHashMap<>();
        var entries=VideoTimeline.timedEntries(doc);
        if(entries.stream().noneMatch(e->e.media().absolute())&&doc.XzC().G1p().beI().stream().noneMatch(v->v instanceof Aqt a&&lockedVideoMarker(a)))return new State(doc,clips,markers,null);
        tIh map=copyMap(doc);
        for(var e:entries)if(e.media().absolute())clips.put(e.clip(),snapshot(doc,e.clip(),e.media(),map));
        for(var v:doc.XzC().G1p().beI())if(v instanceof Aqt a&&lockedVideoMarker(a))markers.put(a,map.beI(a.ie()));
        return new State(doc,clips,markers,map);
    }
    private static ClipState snapshot(CmH doc,TKq clip,VideoTimeline.Media media,tIh map){
        poA note=rawNote(clip);double audioEnd=Double.NaN;
        if(note!=null){var context=clip.beI(0);audioEnd=note.beI(note.ckt(),context.lY_(),context.sCA(note.ie()));}
        return new ClipState(doc,map,clip.ie(),clip.ckt(),clip.dC_(),map.beI(clip.ie()),map.beI(clip.ie()+clip.ckt()),VideoTimeline.sourceStart(doc,clip,media),audioEnd);
    }
    private static tIh copyMap(CmH doc){tIh map=tIh.unz();map.beI(doc.jPe().KrW(),doc.jPe().iqe());return map;}
    /** dev.5 bisect modes, read live (at most twice a second) from
     *  Windows %LOCALAPPDATA% or macOS ~/Library/Application Support:
     *  …/BitwigVideo/mods/video-preview/absolute-mode.txt. */
    private static java.nio.file.Path modeFile(){
        String local=System.getenv("LOCALAPPDATA");
        String base=local!=null&&!local.isBlank()?local
            :System.getProperty("user.home")+"/Library/Application Support";
        return java.nio.file.Path.of(base,"BitwigVideo","mods","video-preview","absolute-mode.txt");
    }
    private static volatile String modeCache;private static long modeReadAt;
    private static String mode(){
        long now=System.nanoTime();
        if(modeCache==null||now-modeReadAt>500_000_000L){
            try{
                java.nio.file.Path p=modeFile();
                modeCache=java.nio.file.Files.exists(p)?java.nio.file.Files.readString(p).trim():"deferred";
            }catch(Throwable e){modeCache="deferred";}
            if(modeCache.isEmpty())modeCache="deferred";
            modeReadAt=now;
        }
        return modeCache;
    }
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
