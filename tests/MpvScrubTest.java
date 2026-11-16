package local.bitwig.video;

import java.nio.file.*;
import java.util.concurrent.TimeUnit;

/** Exercise actual decoding under changing drag targets; no simulated decoder. */
public final class MpvScrubTest {
    public static void main(String[] args) throws Exception {
        Path player=Path.of(args[0]), media=Path.of(args[1]), logs=Path.of(args[2]);
        Files.createDirectories(logs);
        try(MpvConnection p=new MpvConnection(player,logs.resolve("scrub-mpv.log"),false)) {
            for(String file:new String[]{"frame-clock-30fps.mp4","clip-b-24fps.mp4"}) {
                double duration=p.load(media.resolve(file));
                double fps=p.get("container-fps").getAsDouble();
                long before=p.completedSeeks(), start=System.nanoTime();
                int sent=0, merged=0;
                // Alternate forward/reverse scrubs, with targets arriving faster than decode.
                for(int i=0;i<160;i++) {
                    double target=0.5+(duration-1)*(.5+.48*Math.sin(i*.07));
                    if(p.seek(target))sent++;else merged++;
                    Thread.sleep(10);
                }
                long during=p.completedSeeks()-before;
                check(during>=5,"no continuous decoded progress: "+during);
                double finalTarget=Math.floor((duration*.63)*fps)/fps;
                long released=System.nanoTime();
                while(!p.seek(finalTarget)) {p.checkSeek();Thread.sleep(5);}
                waitForFrame(p);
                double actual=p.get("time-pos").getAsDouble();
                check(Math.abs(actual-finalTarget)<.002,"final frame target="+finalTarget+" actual="+actual);
                System.out.printf("SCRUB_OK file=%s updates=160 sent=%d merged=%d decoded_during_drag=%d final_target=%.6f final_actual=%.6f settle_ms=%.1f%n",
                    file,sent,merged,during,finalTarget,actual,(System.nanoTime()-released)/1e6);
                // Adjacent forward/backward frames must survive coalescing and stay paused.
                for(int delta:new int[]{1,-1,1,1,-1}) {
                    finalTarget+=delta/fps;
                    check(p.seek(finalTarget),"previous exact seek not complete");waitForFrame(p);
                    actual=p.get("time-pos").getAsDouble();
                    check(Math.abs(actual-finalTarget)<.002,"frame step target="+finalTarget+" actual="+actual);
                }
                Thread.sleep(150);
                check(Math.abs(p.get("time-pos").getAsDouble()-finalTarget)<.002,"paused frame drift");
                System.out.println("FRAME_STEPS_OK fps="+fps);
                p.set("pause",false);Thread.sleep(350);p.set("pause",true);Thread.sleep(60);
                check(p.get("time-pos").getAsDouble()>finalTarget+.2,"play after scrub failed");
            }
        }
        System.out.println("MPV_SCRUB_OK");
    }
    private static void waitForFrame(MpvConnection p)throws Exception{
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(4);
        while(p.seeking()&&System.nanoTime()<deadline){p.checkSeek();Thread.sleep(5);}
        check(!p.seeking(),"decoder did not complete exact seek");
    }
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
