package local.bitwig.video;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Failure/recovery and concurrency tests; no Bitwig installation or user cache is touched. */
public final class PlayerRuntimeTest {
    private static final byte[] ARCHIVE="verified test archive".getBytes(StandardCharsets.UTF_8);
    private static final Map<String,byte[]> CONTENT=Map.of(
        "mpv.exe","test executable".getBytes(StandardCharsets.UTF_8),
        "mpv.com","test launcher".getBytes(StandardCharsets.UTF_8),
        "d3dcompiler_43.dll","test library".getBytes(StandardCharsets.UTF_8));
    private static final PlayerRuntime.Spec SPEC=spec();
    private static PlayerRuntime.Spec spec() {
        Map<String,String> hashes=new HashMap<>();CONTENT.forEach((name,bytes)->hashes.put(name,digest(bytes)));
        return new PlayerRuntime.Spec("fixture-1","https://invalid.example/test",digest(ARCHIVE),ARCHIVE.length,Map.copyOf(hashes));
    }
    private static String digest(byte[] bytes) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
        catch(Exception e){throw new AssertionError(e);}
    }
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static void extract(Path archive,Path target,Set<String> names,java.util.function.BooleanSupplier cancelled)throws Exception{
        require(names.equals(CONTENT.keySet()),"Only the three verified runtime files may be requested");
        for(var file:CONTENT.entrySet())Files.write(target.resolve(file.getKey()),file.getValue());
    }
    private static PlayerRuntime provider(Path root,AtomicInteger requests) {
        return new PlayerRuntime(root,SPEC,(spec,archive,progress,cancelled)->{
            requests.incrementAndGet();progress.accept("fixture download");Files.write(archive,ARCHIVE);
        },PlayerRuntimeTest::extract);
    }
    private static PlayerRuntime offline(Path root) {
        return new PlayerRuntime(root,SPEC,(s,p,l,c)->{throw new AssertionError("Offline cache unexpectedly requested a download");},
            (a,t,f,c)->{throw new AssertionError("Offline cache unexpectedly requested extraction");});
    }
    private static Path get(PlayerRuntime runtime)throws Exception{return runtime.get(null,s->{},()->false);}
    private static void cleanStaging(Path root)throws Exception{
        if(!Files.exists(root))return;
        try(var paths=Files.list(root)){
            require(paths.noneMatch(p->p.getFileName().toString().startsWith(".player-")||p.getFileName().toString().startsWith(".previous-")),"Owned staging remains after completion/failure");
        }
    }
    private static void expectedFailure(PlayerRuntime runtime,String message)throws Exception{
        try{get(runtime);throw new AssertionError("Expected failure: "+message);}
        catch(IOException expected){require(expected.getMessage().contains(message),"Unexpected error: "+expected);}
    }
    private static void freshAndOffline(Path base)throws Exception{
        Path root=base.resolve("fresh");AtomicInteger requests=new AtomicInteger();List<String> progress=new ArrayList<>();
        PlayerRuntime runtime=provider(root,requests);
        Path ready=runtime.get(null,progress::add,()->false);
        require(ready.equals(root.resolve(SPEC.build())),"Unexpected cache path");
        require(requests.get()==1 && progress.stream().anyMatch(p->p.contains("cached for offline")),"First use did not finish");
        require(get(runtime).equals(ready) && get(offline(root)).equals(ready),"Fresh provider cannot reuse offline cache");
        require(requests.get()==1,"Warm cache repeated download");
        Path unused=base.resolve("unused-cache");
        require(offline(unused).get(ready,s->{},()->false).equals(ready),"Existing verified bundled player was not retained");
        require(!Files.exists(unused),"Bundled player caused cache writes");cleanStaging(root);
        System.out.println("FIRST_USE_OFFLINE_AND_BUNDLED_OK");
    }
    private static void concurrent(Path base)throws Exception{
        Path root=base.resolve("concurrent");AtomicInteger requests=new AtomicInteger();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        PlayerRuntime runtime=new PlayerRuntime(root,SPEC,(s,p,l,c)->{requests.incrementAndGet();entered.countDown();require(release.await(10,TimeUnit.SECONDS),"Download barrier timeout");Files.write(p,ARCHIVE);},PlayerRuntimeTest::extract);
        ExecutorService pool=Executors.newFixedThreadPool(8);
        try{
            List<Future<Path>> jobs=new ArrayList<>();jobs.add(pool.submit(()->get(runtime)));
            require(entered.await(10,TimeUnit.SECONDS),"No download started");
            for(int i=0;i<7;i++)jobs.add(pool.submit(()->get(runtime)));
            release.countDown();for(var job:jobs)require(job.get(10,TimeUnit.SECONDS).equals(root.resolve(SPEC.build())),"Wrong shared result");
            require(requests.get()==1,"Concurrent requests downloaded more than once");
        }finally{release.countDown();pool.shutdownNow();}
        cleanStaging(root);System.out.println("CONCURRENT_SINGLE_DOWNLOAD_OK");
    }
    private static void failuresAndRepair(Path base)throws Exception{
        Path root=base.resolve("repair");Files.createDirectories(root);Path sentinel=root.resolve("unrelated-user-file.txt");Files.writeString(sentinel,"keep");
        PlayerRuntime downloadFailure=new PlayerRuntime(root,SPEC,(s,p,l,c)->{Files.write(p,new byte[]{1,2});throw new IOException("fixture network failure");},PlayerRuntimeTest::extract);
        expectedFailure(downloadFailure,"fixture network failure");cleanStaging(root);
        AtomicInteger extracts=new AtomicInteger();
        PlayerRuntime badArchive=new PlayerRuntime(root,SPEC,(s,p,l,c)->Files.write(p,new byte[ARCHIVE.length]),(a,t,f,c)->extracts.incrementAndGet());
        expectedFailure(badArchive,"SHA-256");require(extracts.get()==0,"Unverified archive was extracted");cleanStaging(root);
        PlayerRuntime badExtract=new PlayerRuntime(root,SPEC,(s,p,l,c)->Files.write(p,ARCHIVE),(a,t,f,c)->{extract(a,t,f,c);Files.writeString(t.resolve("mpv.exe"),"wrong");});
        expectedFailure(badExtract,"failed verification");require(!Files.exists(root.resolve(SPEC.build())),"Invalid player was activated");cleanStaging(root);
        AtomicInteger requests=new AtomicInteger();PlayerRuntime runtime=provider(root,requests);Path ready=get(runtime);
        Files.writeString(ready.resolve("mpv.exe"),"corrupted player");
        expectedFailure(downloadFailure,"fixture network failure");
        require(Files.readString(ready.resolve("mpv.exe")).equals("corrupted player"),"Failed replacement removed previous cache");
        get(runtime);require(requests.get()==2,"Corrupted cache was not repaired");
        require(PlayerRuntime.hash(ready.resolve("mpv.exe"),()->false).equals(SPEC.files().get("mpv.exe")),"Repair has wrong bytes");
        Path older=root.resolve("older-version");Files.createDirectory(older);Files.writeString(older.resolve("keep.txt"),"older");
        get(offline(root));require(Files.readString(older.resolve("keep.txt")).equals("older"),"Other cache version removed");
        require(Files.readString(sentinel).equals("keep"),"Unrelated data changed");cleanStaging(root);
        System.out.println("FAILED_DOWNLOAD_HASH_EXTRACTION_AND_REPAIR_OK");
    }
    private static void cancellation(Path base)throws Exception{
        Path root=base.resolve("cancel");AtomicBoolean cancelled=new AtomicBoolean();
        PlayerRuntime runtime=new PlayerRuntime(root,SPEC,(s,p,l,c)->{Files.write(p,ARCHIVE);cancelled.set(true);},PlayerRuntimeTest::extract);
        try{runtime.get(null,s->{},cancelled::get);throw new AssertionError("Cancellation ignored");}
        catch(InterruptedIOException expected){}
        require(!Files.exists(root.resolve(SPEC.build())),"Cancelled download activated");cleanStaging(root);
        // A waiting caller can cancel without cancelling or duplicating the owner's download.
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);AtomicInteger calls=new AtomicInteger();
        PlayerRuntime busy=new PlayerRuntime(root,SPEC,(s,p,l,c)->{calls.incrementAndGet();entered.countDown();require(release.await(10,TimeUnit.SECONDS),"Barrier timeout");Files.write(p,ARCHIVE);},PlayerRuntimeTest::extract);
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try{
            Future<Path> owner=pool.submit(()->get(busy));require(entered.await(10,TimeUnit.SECONDS),"Owner not started");
            Future<Boolean> waiter=pool.submit(()->{try{busy.get(null,s->{},()->true);return false;}catch(InterruptedIOException expected){return true;}});
            require(waiter.get(3,TimeUnit.SECONDS),"Waiting caller ignored cancellation");release.countDown();owner.get(10,TimeUnit.SECONDS);
            require(calls.get()==1,"Cancelled waiter restarted download");
        }finally{release.countDown();pool.shutdownNow();}
        cleanStaging(root);System.out.println("CANCELLATION_AND_WAITING_CALLER_OK");
    }
    private static void processChild(Path root)throws Exception{
        Files.createDirectories(root);
        PlayerRuntime runtime=new PlayerRuntime(root,SPEC,(s,p,l,c)->{
            Files.writeString(root.resolve("downloads.txt"),"download\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
            Thread.sleep(1000);Files.write(p,ARCHIVE);
        },PlayerRuntimeTest::extract);
        get(runtime);
    }
    private static void acrossProcesses(Path base)throws Exception{
        Path root=base.resolve("processes");Files.createDirectories(root);List<Process> processes=new ArrayList<>();
        String java=Path.of(System.getProperty("java.home"),"bin","java.exe").toString();
        try{
            for(int i=0;i<3;i++){
                ProcessBuilder builder=new ProcessBuilder(java,"-cp",System.getProperty("java.class.path"),PlayerRuntimeTest.class.getName(),"child",root.toString());
                builder.redirectErrorStream(true).redirectOutput(root.resolve("child-"+i+".log").toFile());builder.environment().remove("JAVA_TOOL_OPTIONS");processes.add(builder.start());
            }
            for(Process p:processes)require(p.waitFor(20,TimeUnit.SECONDS)&&p.exitValue()==0,"Concurrent process failed");
        }finally{for(Process p:processes)if(p.isAlive())p.destroyForcibly();}
        require(Files.readAllLines(root.resolve("downloads.txt")).size()==1,"Cross-process cache downloaded repeatedly");cleanStaging(root);
        System.out.println("CROSS_PROCESS_SINGLE_DOWNLOAD_OK");
    }
    private static void online(Path base)throws Exception{
        Path root=base.resolve("upstream-cache");
        require(!Files.exists(root),"Online test needs a fresh cache");
        long started=System.nanoTime();Path ready=getWithProgress(new PlayerRuntime(root));
        require(ready.equals(root.resolve(PlayerRuntime.BUILD)),"Production cache version differs");
        try(var files=Files.list(ready)){require(files.map(p->p.getFileName().toString()).collect(java.util.stream.Collectors.toSet()).equals(PlayerRuntime.FILES.keySet()),"Unexpected upstream files extracted");}
        PlayerRuntime.Spec real=new PlayerRuntime.Spec(PlayerRuntime.BUILD,PlayerRuntime.ARCHIVE_URL,PlayerRuntime.ARCHIVE_SHA256,PlayerRuntime.ARCHIVE_BYTES,PlayerRuntime.FILES);
        PlayerRuntime offline=new PlayerRuntime(root,real,(s,p,l,c)->{throw new AssertionError("Production cache attempted network");},PlayerRuntimeTest::extract);
        require(get(offline).equals(ready),"Production cache failed offline validation");cleanStaging(root);
        System.out.printf(Locale.ROOT,"UPSTREAM_DOWNLOAD_HASH_EXTRACT_OFFLINE_OK seconds=%.3f%n",(System.nanoTime()-started)/1e9);
    }
    private static Path getWithProgress(PlayerRuntime runtime)throws Exception{return runtime.get(null,System.out::println,()->false);}
    public static void main(String[] args)throws Exception{
        Path base=Path.of(args[1]).toAbsolutePath().normalize();
        if(args[0].equals("child")){processChild(base);return;}
        Files.createDirectories(base);
        if(args[0].equals("online")){online(base);return;}
        freshAndOffline(base);concurrent(base);failuresAndRepair(base);cancellation(base);acrossProcesses(base);
        System.out.println("PLAYER_RUNTIME_ACCEPTANCE_OK");
    }
}
