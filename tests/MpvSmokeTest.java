package local.bitwig.video;

import java.nio.file.*;
import java.util.concurrent.TimeUnit;

/** Real decoder and Windows named-pipe checks, not a mocked implementation test. */
public final class MpvSmokeTest {
    public static void main(String[] args) throws Exception {
        Path player=Path.of(args[0]), video=Path.of(args[1]), logs=Path.of(args[2]);
        Files.createDirectories(logs);
        try (MpvConnection p=new MpvConnection(player,logs.resolve("smoke-mpv.log"),false)) {
            double duration=p.load(video); check(duration>=29.9 && duration<=30.1,"duration="+duration);
            System.out.println("LOAD_OK duration="+duration+" codec="+p.get("video-codec"));
            for (double point:new double[]{0,7.5,24.2,1.1,14.0}) {
                seekExact(p,point); double actual=p.get("time-pos").getAsDouble();
                check(Math.abs(actual-point)<0.04,"seek "+point+" actual="+actual);System.out.println("SEEK_OK target="+point+" actual="+actual);
            }
            p.set("pause",false);Thread.sleep(1000);p.set("pause",true);Thread.sleep(100);
            double actual=p.get("time-pos").getAsDouble();check(actual>14.8 && actual<15.3,"play="+actual);
            System.out.println("PLAY_PAUSE_OK actual="+actual);
            Thread.sleep(400);double held=p.get("time-pos").getAsDouble();check(Math.abs(held-actual)<0.04,"pause does not hold");
            boolean missing=false;try{p.load(video.resolveSibling("does-not-exist.mp4"));}catch(java.io.IOException e){missing=true;}
            check(missing,"missing file must fail");System.out.println("MISSING_FILE_OK");
            seekExact(p,0.5);check(Math.abs(p.get("time-pos").getAsDouble()-0.5)<0.04,"loop back");
            System.out.println("LOOP_SEEK_OK");
            double reloaded=p.load(video);check(reloaded==duration,"reload duration");
            seekExact(p,9);check(Math.abs(p.get("time-pos").getAsDouble()-9)<0.04,"reload frame");
            System.out.println("RELOAD_OK");
            Path invalid=logs.resolve("invalid-video.mp4");Files.writeString(invalid,"not a video");
            boolean rejected=false;try{p.load(invalid);}catch(java.io.IOException e){rejected=true;}
            check(rejected,"invalid file must fail");System.out.println("INVALID_VIDEO_OK");
        }
        System.out.println("MPV_SMOKE_OK");
    }
    private static void seekExact(MpvConnection p,double point)throws Exception{
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
        boolean accepted=false;
        while(System.nanoTime()<end){
            if(!accepted)accepted=p.seek(point);
            if(accepted&&!p.seeking())return;
            p.checkSeek();Thread.sleep(5);
        }
        throw new AssertionError("seek frame timeout "+point);
    }
    private static void check(boolean valid,String message){if(!valid)throw new AssertionError(message);}
}
