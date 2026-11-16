package local.bitwig.video;
import java.nio.file.*;

/** Compare every accepted reuse against an independent full exact seek, including VFR. */
public final class MpvExactReuseTest {
    static void exact(MpvConnection p,double target)throws Exception{
        long end=System.nanoTime()+8_000_000_000L;
        while(!p.seek(target)){p.checkSeek();Thread.sleep(2);}
        while(p.seeking()&&System.nanoTime()<end){p.checkSeek();Thread.sleep(2);}
        if(p.seeking())throw new AssertionError("seek timeout");
    }
    public static void main(String[] args)throws Exception{
        Path root=Path.of(args[0]);int checked=0;
        try(MpvConnection p=new MpvConnection(root.resolve("vendor/mpv"),root.resolve("tests/results/exact-reuse-mpv.log"),false)){
            for(String file:new String[]{"4k60-h264-aac.mp4","4k30-hevc10-aac.mp4","4k30-av1-opus.mkv","1080-vfr-vp9-opus.webm","4k24-prores-pcm.mov"}){
                double duration=p.load(root.resolve("tests/media/compatibility").resolve(file));
                if(p.reuseStoppedFrame(2.462))throw new AssertionError("cache leaked between files");
                for(double target:new double[]{.101,2.462,2.467,3.998,4.01,7.901,10.913,11.99}){
                    exact(p,target);double frame=p.get("time-pos").getAsDouble();
                    if(target>=duration-1||frame>=duration-1){
                        if(p.reuseStoppedFrame(target))throw new AssertionError("ambiguous EOF time reused");
                        continue;
                    }
                    for(double fraction:new double[]{0,.2,.5,.8,1}){
                        double probe=target+(frame-target)*fraction;long before=p.completedSeeks();
                        if(!p.reuseStoppedFrame(probe))throw new AssertionError("proven interval rejected: "+file+" "+probe);
                        if(p.completedSeeks()!=before)throw new AssertionError("reuse decoded unnecessarily");
                        exact(p,probe);double reference=p.get("time-pos").getAsDouble();
                        if(Math.abs(frame-reference)>.000001)throw new AssertionError(file+" reused="+frame+" raw="+reference+" target="+probe);
                        checked++;
                    }
                    if(p.reuseStoppedFrame(frame+1))throw new AssertionError("unknown frame accepted");
                }
                exact(p,2.0);p.request("set_property","pause",false).get();Thread.sleep(75);
                if(p.reuseStoppedFrame(p.get("time-pos").getAsDouble()))throw new AssertionError("reused during playback");
                p.request("set_property","pause",true).get();Thread.sleep(25);
                System.out.println("EXACT_REUSE_OK "+file+" checks=35 + EOF fallback");
            }
        }
        System.out.println("EXACT_REUSE_PASS checks="+checked);
    }
}
