package local.bitwig.video;

import bitwig.obf.*;
import bml.api.At;
import bml.api.Hook;
import bml.api.This;
import bml.api.Return;
import bml.api.Arg;
import com.bitwig.flt.widget.core.timeline.Jpp;
import com.bitwig.windowing_system.odK;
import java.io.File;
import java.nio.file.Path;
import java.util.*;

/** External video files are committed only by the native drop event, never by hover. */
public final class VideoHooks {
    private static final Set<epD> COMMITTED=Collections.newSetFromMap(new WeakHashMap<>());

    @Hook(target=alC.class,method="sCA(LaCZ;)LdwF;",at=At.WRAP_RETURN)
    public static dwF previewLayout(@Return dwF content,@Arg(0) aCZ window){
        try{return VideoLayout.wrap(content,window);}catch(Throwable e){BitwigBridge.error(e);return content;}
    }
    @Hook(target=aCZ.class,method="frp()LdwF;",at=At.REPLACE_CALL,callOwner=alC.class,call="beI(LaCZ;)LdwF;",occurrence=1)
    public static dwF previewButton(alC profile,aCZ window){
        dwF content=profile.beI(window);
        try{return VideoLayout.button(content,window);}catch(Throwable e){BitwigBridge.error(e);return content;}
    }

    @Hook(target=dgR.class,method="Heb()V",at=At.ENTRY)
    public static void timeEditBegin(@This dgR manager){AbsoluteTime.begin(manager);}
    @Hook(target=dgR.class,method="jTs()V",at=At.ENTRY)
    public static void timeEditFlush(@This dgR manager){AbsoluteTime.flush(manager);}
    @Hook(target=dgR.class,method="iMb()V",at=At.BEFORE_RETURN)
    public static void timeEditEnd(@This dgR manager){AbsoluteTime.end(manager);}
    @Hook(target=dgR.class,method="mEL()V",at=At.ENTRY)
    public static void timeUndo(@This dgR manager){AbsoluteTime.undo(manager);}
    @Hook(target=dgR.class,method="LYs()V",at=At.ENTRY)
    public static void timeRedo(@This dgR manager){AbsoluteTime.undo(manager);}
    @Hook(target=QJG.class,method="tWW()LDrA;",at=At.WRAP_RETURN)
    public static DrA copyVideo(@Return DrA result,@This QJG source){AbsoluteTime.copied(source,result);return result;}
    @Hook(target=oFQ.class,method="ocs()LDyF;",at=At.WRAP_RETURN)
    public static DyF copyAudio(@Return DyF result,@This oFQ source){AbsoluteTime.copied(source,result);return result;}

    @Hook(target=dqD.class,method="beI(LdwF;DD)LdKt;",at=At.REPLACE_CALL,
          callOwner=dwF.class,call="Gef(DDLepD;)LdKt;")
    public static dKt videoDrop(dwF widget,double x,double y,epD data){
        try {
            // Native hit testing visits track/clip children before the arranger itself.
            // Claim movie drops at those children too, before native audio import wins.
            Jpp arranger=widget instanceof Jpp grid?grid:(Jpp)widget.byf(Jpp.class);
            if(arranger!=null){
                List<Path> paths=new ArrayList<>();
                boolean mixed=false;
                for(Object item:data.sCA(File.class))if(item instanceof File file){
                    if(VideoTimeline.accepts(file.toPath()))paths.add(file.toPath());else mixed=true;
                }
                if(!mixed&&!paths.isEmpty())return new VideoDrop(arranger,data,List.copyOf(paths));
            }
        }catch(Throwable failure){BitwigBridge.MOD.log("Video drop inspection failed: "+failure);}
        return widget.Gef(x,y,data);
    }

    private static final class VideoDrop implements dKt {
        private final Jpp arranger;
        private final epD data;
        private final List<Path> paths;
        VideoDrop(Jpp arranger,epD data,List<Path> paths){
            this.arranger=arranger;this.data=data;this.paths=paths;
        }
        @Override public dwF cCV(){return arranger;}
        @Override public void beI(dKU enter){}
        @Override public odK beI(dKW over){return odK.sCA;}
        @Override public void beI(dKV leave){}
        @Override public odK beI(dKY drop){
            return commit(drop.wLf());
        }
        private odK commit(double x){
            try{
                // The host can recreate candidate handlers while moving the pointer.
                // Deduplicate by drag data identity across those handlers.
                synchronized(COMMITTED){if(!COMMITTED.add(data))return odK.sCA;}
                // cCV() is the arranger, so native drop coordinates are arranger-local.
                double beat=Math.max(0,arranger.t_(arranger.sCA(x)));
                var document=arranger.lq_();
                BitwigBridge.MOD.log("Video drop committed: count="+paths.size()+" beat="+beat);
                VideoTimeline.importFiles(document,paths,beat);
                return odK.sCA; // COPY: the external source file is always retained.
            }catch(Throwable failure){BitwigBridge.error(failure);return odK.beI;}
        }
        @Override public boolean equals(Object value){
            return value instanceof VideoDrop other&&other.arranger==arranger&&other.data==data;
        }
        @Override public int hashCode(){return 31*System.identityHashCode(arranger)+System.identityHashCode(data);}
    }
}
