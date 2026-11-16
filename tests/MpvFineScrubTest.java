package local.bitwig.video;

import java.nio.file.*;
import java.util.concurrent.TimeUnit;

/** Slow fine scrubs should not repeatedly decode an unchanged frame; endpoint matches raw exact seek. */
public final class MpvFineScrubTest {
    public static void main(String[] args)throws Exception{
        Path logs=Path.of(args[2]);Files.createDirectories(logs);
        try(MpvConnection p=new MpvConnection(Path.of(args[0]),logs.resolve("fine-scrub-mpv.log"),false)){
            for(String file:new String[]{"frame-clock-30fps.mp4","clip-b-24fps.mp4"}){
                p.load(Path.of(args[1]).resolve(file));
                double fps=p.get("container-fps").getAsDouble();
                ScrubCadence cadence=new ScrubCadence();
                double issued=Double.NaN,target=0;
                int commands=0;
                for(int i=0;i<320;i++){
                    target=2.05+(i<160?i:319-i)*.001;
                    if(cadence.ready(target,issued,fps,System.nanoTime())&&p.seek(target)){issued=target;commands++;}
                    Thread.sleep(10);
                }
                // A final sub-frame change must reach the decoder once the mouse becomes quiet.
                target=2.057; // same 30/24 fps frame bucket as the last motion target (2.050)
                long released=System.nanoTime(), deadline=released+TimeUnit.SECONDS.toNanos(4);
                while(System.nanoTime()<deadline){
                    if(cadence.ready(target,issued,fps,System.nanoTime())&&p.seek(target)){issued=target;commands++;}
                    if(issued==target&&!p.seeking())break;
                    Thread.sleep(5);
                }
                check(issued==target&&!p.seeking(),"final sub-frame target was lost");
                double optimized=p.get("time-pos").getAsDouble();
                double settledMs=(System.nanoTime()-released)/1e6;
                exact(p,target);
                double reference=p.get("time-pos").getAsDouble();
                check(Math.abs(optimized-reference)<.0001,"final frame differs from raw exact seek");
                check(commands<30,"fine scrub still decodes excessively: "+commands);
                // Repeated unchanging source time, as when a selected clip and playhead move together.
                int unchanged=0;
                for(int i=0;i<100;i++)if(cadence.ready(target,issued,fps,System.nanoTime()))unchanged++;
                check(unchanged==0,"unchanged source frame must stay still");
                System.out.printf("FINE_SCRUB_OK file=%s updates=320 seeks=%d final=%.6f raw_exact=%.6f settle_ms=%.1f unchanged_requests=%d%n",
                    file,commands,optimized,reference,settledMs,unchanged);
            }
        }
        System.out.println("MPV_FINE_SCRUB_OK");
    }
    private static void exact(MpvConnection p,double point)throws Exception{
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(4);boolean accepted=false;
        while(System.nanoTime()<end){
            if(!accepted)accepted=p.seek(point);
            if(accepted&&!p.seeking())return;
            p.checkSeek();Thread.sleep(5);
        }
        throw new AssertionError("raw exact seek did not finish");
    }
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
