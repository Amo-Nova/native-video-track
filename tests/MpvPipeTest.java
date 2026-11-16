package local.bitwig.video;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Exercise concurrent replies and cancellation while an overlapped read/seek is pending. */
public final class MpvPipeTest {
    public static void main(String[] args)throws Exception{
        Path root=Path.of(args[0]);int replies=0;
        for(int cycle=0;cycle<5;cycle++){
            try(MpvConnection p=new MpvConnection(root.resolve("vendor/mpv"),root.resolve("tests/results/pipe-"+cycle+".log"),false)){
                p.load(root.resolve("tests/media/compatibility/4k60-h264-aac.mp4"));
                List<CompletableFuture<?>> batch=new ArrayList<>();
                for(int i=0;i<256;i++)batch.add(p.request("get_property",i%2==0?"time-pos":"pause"));
                CompletableFuture.allOf(batch.toArray(CompletableFuture[]::new)).get(4,TimeUnit.SECONDS);replies+=batch.size();
                p.seek(3.91); // close while the decoder and pipe reader can still be waiting
            }
            long readers=Thread.getAllStackTraces().keySet().stream().filter(t->t.isAlive()&&t.getName().equals("Video-preview-mpv-reader")).count();
            if(readers!=0)throw new AssertionError("IPC reader survived close: "+readers);
        }
        System.out.println("PIPE_PASS replies="+replies+" close_during_seek=5 reader_leaks=0");
    }
}
