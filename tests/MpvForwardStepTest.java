package local.bitwig.video;
import java.nio.file.*;

/** Actual stepped frame must equal an independent full exact seek, even across GOPs/VFR. */
public final class MpvForwardStepTest {
    public static void main(String[] args)throws Exception{
        Path root=Path.of(args[0]);int checks=0,fast=0;
        try(MpvConnection p=new MpvConnection(root.resolve("vendor/mpv"),root.resolve("tests/results/forward-step-mpv.log"),false)){
            for(String file:new String[]{"4k60-h264-aac.mp4","4k30-hevc10-aac.mp4","4k30-av1-opus.mkv","1080-vfr-vp9-opus.webm","4k24-prores-pcm.mov"}){
                p.load(root.resolve("tests/media/compatibility").resolve(file));int hits=0;
                for(double seed:new double[]{2.462,3.968,7.901})for(double delta:new double[]{.006,.013,.020,.034,.052,.082}){
                    MpvExactReuseTest.exact(p,seed);
                    double first=p.get("time-pos").getAsDouble(),target=first+delta;
                    // Learn the target by an independent seek, then return to the earlier frame.
                    MpvExactReuseTest.exact(p,target);
                    MpvExactReuseTest.exact(p,seed);
                    if(p.stepStoppedForward(target)){
                        double stepped=p.get("time-pos").getAsDouble();
                        MpvExactReuseTest.exact(p,target);double reference=p.get("time-pos").getAsDouble();
                        if(Math.abs(stepped-reference)>.000001)throw new AssertionError(file+" target="+target+" stepped="+stepped+" exact="+reference);
                        if(!p.get("pause").getAsBoolean())throw new AssertionError("step left player running");
                        hits++;fast++;
                    }
                    checks++;
                }
                if(hits==0)throw new AssertionError("No forward optimization exercised: "+file);
                MpvExactReuseTest.exact(p,11.5);
                if(p.stepStoppedForward(11.55))throw new AssertionError("EOF fast path accepted");
                MpvExactReuseTest.exact(p,2.462);
                if(p.stepStoppedForward(2.0))throw new AssertionError("backward fast path accepted");
                System.out.println("FORWARD_STEP_OK "+file+" checked=18 accepted="+hits);
            }
        }
        System.out.println("FORWARD_STEP_PASS checked="+checks+" accepted="+fast);
    }
}
