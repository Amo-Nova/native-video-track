package local.bitwig.video;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicBoolean;
import com.google.gson.Gson;

public class MediaAssetsTest {
    public static void main(String[] args)throws Exception{
        Path base=Path.of(args[0]).toAbsolutePath(),source=Path.of(args[1]).toAbsolutePath();
        Path destination=base.resolve("tests/results/"+(args.length>2?args[2]:"stage3-assets")+"/"+source.getFileName());
        MediaAssets worker=new MediaAssets(base.resolve("vendor/mpv"),base.resolve("tests/results/stage3-assets/logs"));
        long started=System.nanoTime();
        var asset=worker.prepare(destination,source,System.out::println,()->false);
        System.out.println("ASSET "+new Gson().toJson(asset));
        System.out.println("SECONDS "+(System.nanoTime()-started)/1e9+" MAX_HEAP "+Runtime.getRuntime().maxMemory());
        if(source.getFileName().toString().startsWith("sync-")){
            Path recovery=destination.resolve("recovery");
            var silent=worker.collectVideo(recovery,source,asset.duration(),System.out::println);
            if(!silent.audioRelative().isEmpty())throw new AssertionError("Legacy collection must not bind audio");
            Path recovered=MediaAssets.under(recovery,asset.audioRelative());
            if(!Files.exists(recovered))worker.recoverAudio(recovery,asset,System.out::println);
            if(Files.mismatch(recovered,MediaAssets.under(destination,asset.audioRelative()))!=-1)throw new AssertionError("Recovered audio differs");
            System.out.println("LEGACY_COPY_AND_AUDIO_RECOVERY_OK");
        }
        try(MpvConnection player=new MpvConnection(base.resolve("vendor/mpv"),base.resolve("tests/results/stage3-assets/seek.log"),false)){
            player.load(MediaAssets.under(destination,asset.relative()));
            for(double seconds:new double[]{0.1,asset.duration()/2,asset.duration()-1}){
                player.seek(seconds);long deadline=System.nanoTime()+10_000_000_000L;
                while(player.seeking()&&System.nanoTime()<deadline)Thread.sleep(20);
                player.checkSeek();double actual=player.get("time-pos").getAsDouble();
                if(Math.abs(actual-seconds)>0.06)throw new AssertionError("Seek "+seconds+" -> "+actual);
                System.out.println("SEEK_OK "+seconds+" -> "+actual);
            }
        }
        AtomicBoolean cancel=new AtomicBoolean();Path target=destination.resolve("cancelled.mp4");
        try{MediaAssets.copy(source,target,Files.size(source),s->cancel.set(true),cancel::get,"cancel test");throw new AssertionError("Cancellation ignored");}
        catch(MediaAssets.Cancelled expected){if(Files.exists(target))throw new AssertionError("Cancelled target published");}
        try(var files=Files.list(destination)){if(files.anyMatch(p->p.toString().endsWith(".partial")))throw new AssertionError("Partial not cleaned");}
        try{MediaAssets.under(destination,"../escape");throw new AssertionError("Traversal accepted");}catch(java.io.IOException expected){}
        System.out.println("MEDIA_ASSETS_PASS");
    }
}
