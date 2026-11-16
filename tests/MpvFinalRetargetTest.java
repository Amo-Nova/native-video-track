package local.bitwig.video;
import java.nio.file.*;

public final class MpvFinalRetargetTest {
    public static void main(String[] args)throws Exception{
        Path root=Path.of(args[0]);int accepted=0;
        try(MpvConnection p=new MpvConnection(root.resolve("vendor/mpv"),root.resolve("tests/results/final-retarget-mpv.log"),false)){
            for(double obsolete:new double[]{3.91,7.91,11.91}){
                p.load(root.resolve("tests/media/compatibility/4k60-h264-aac.mp4"));
                p.seek(obsolete);Thread.sleep(85);
                if(p.retargetStoppedFinal(1.234)){
                    accepted++;
                    if(p.retargetStoppedFinal(1.8))throw new AssertionError("retarget cooldown bypassed");
                    long until=System.nanoTime()+8_000_000_000L;
                    while(p.seeking()&&System.nanoTime()<until){p.checkSeek();Thread.sleep(2);}
                    if(p.seeking())throw new AssertionError("retarget did not finish");
                    double actual=p.get("time-pos").getAsDouble();
                    MpvExactReuseTest.exact(p,1.234);double reference=p.get("time-pos").getAsDouble();
                    if(Math.abs(actual-reference)>.000001)throw new AssertionError("late obsolete reply accepted: "+actual+" vs "+reference);
                }
            }
            if(accepted==0)throw new AssertionError("No long decode was interrupted; increase fixture cost");
        }
        System.out.println("FINAL_RETARGET_PASS interrupted="+accepted+" exact_frames=true cooldown=true");
    }
}
