package local.bitwig.video;

import java.io.*;
import java.net.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Verified player provisioning. Called by media/preview workers, never the host event thread. */
final class PlayerRuntime {
    static final String BUILD = "20261111-bcde6b93cc";
    static final String ARCHIVE_URL = "https://github.com/shinchiro/mpv-winbuild-cmake/releases/download/20261111/mpv-x86_64-20261111-git-bcde6b93cc.7z";
    static final String ARCHIVE_SHA256 = "f259b5fd4b5c395617c948a4fadcdf55bba1c5282fb7c13e88626b3f71072baf";
    static final long ARCHIVE_BYTES = 34_181_485;
    static final Map<String,String> FILES = Map.of(
        "mpv.exe", "3ea89fe7a4284b25dff3c72e1bde83e27324c90cfc4a074fe679293d19ee7c85",
        "mpv.com", "19a8feec834087fbad317259d1b951f37b821c72c58f212b4853e04080db5b0a",
        "d3dcompiler_43.dll", "4b074a3976399dc735484f5d43d04b519b7bdee8ac719d9ab8ed6bd4e6be0345");
    record Spec(String build, String url, String sha256, long bytes, Map<String,String> files) {}
    interface Fetcher { void fetch(Spec spec, Path archive, Consumer<String> progress, BooleanSupplier cancelled) throws Exception; }
    interface Extractor { void extract(Path archive, Path directory, Set<String> files, BooleanSupplier cancelled) throws Exception; }
    private static final PlayerRuntime DEFAULT = new PlayerRuntime(defaultRoot());
    private final Path root;
    private final Spec spec;
    private final Fetcher fetcher;
    private final Extractor extractor;
    private final ReentrantLock setup = new ReentrantLock();
    private final Map<Path,List<String>> verified = new HashMap<>();
    private volatile String progress = "";

    PlayerRuntime(Path root) {
        this(root,new Spec(BUILD,ARCHIVE_URL,ARCHIVE_SHA256,ARCHIVE_BYTES,FILES),PlayerRuntime::download,PlayerRuntime::extract);
    }
    PlayerRuntime(Path root, Spec spec, Fetcher fetcher, Extractor extractor) {
        this.root = root.toAbsolutePath().normalize(); this.spec = spec;
        if (!spec.build().matches("[a-zA-Z0-9.-]+") || !spec.files().keySet().equals(FILES.keySet()))
            throw new IllegalArgumentException("Invalid player specification");
        this.fetcher = fetcher; this.extractor = extractor;
    }
    private static Path defaultRoot() {
        String local = System.getenv("LOCALAPPDATA");
        Path base = local == null || local.isBlank() ? Path.of(System.getProperty("user.home"), "AppData", "Local") : Path.of(local);
        return base.resolve("BitwigVideo/player");
    }
    static String status() { return DEFAULT.progress; }
    static Path ensure(Path bundled) throws Exception { return ensure(bundled, s -> {}, () -> false); }
    static Path ensure(Path bundled, Consumer<String> progress, BooleanSupplier cancelled) throws Exception {
        return DEFAULT.get(bundled, progress, cancelled);
    }
    private static void check(BooleanSupplier cancelled) throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted() || cancelled.getAsBoolean()) throw new InterruptedIOException("Player preparation cancelled; existing clips are unchanged.");
    }
    private void report(String text, Consumer<String> listener) { progress = text; listener.accept(text); }

    Path get(Path bundled, Consumer<String> listener, BooleanSupplier cancelled) throws Exception {
        // Interruptible process-local gate coalesces preview/probe/import requests.
        while (!setup.tryLock(100, TimeUnit.MILLISECONDS)) { check(cancelled); if (!progress.isBlank()) listener.accept(progress); }
        Path staging = null;
        try {
            check(cancelled);
            if (valid(bundled, cancelled)) return bundled;
            Path target = child(spec.build());
            if (valid(target, cancelled)) return target;
            Files.createDirectories(root);
            if (Files.isSymbolicLink(root)) throw new IOException("Player cache must not be a symbolic link.");
            report("Preparing video player (first use requires Internet)...", listener);
            // A second Bitwig process shares the same per-user cache without racing extraction.
            try (FileChannel channel = FileChannel.open(child("."+spec.build()+".lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
                FileLock lock = null;
                long deadline = System.nanoTime()+TimeUnit.MINUTES.toNanos(4);
                while (lock == null) {
                    check(cancelled);
                    try { lock=channel.tryLock(); } catch (OverlappingFileLockException busy) { /* another provider in this JVM */ }
                    if (lock == null) {
                        if (System.nanoTime()>deadline) throw new IOException("Another process is preparing the video player. Try again shortly.");
                        listener.accept("Waiting for video player preparation..."); Thread.sleep(100);
                    }
                }
                try {
                    if (valid(target, cancelled)) return target;
                    staging = Files.createTempDirectory(root, ".player-"+spec.build()+"-");
                    Path archive = staging.resolve("download.7z");
                    fetcher.fetch(spec, archive, s -> report(s,listener), cancelled);
                    check(cancelled);
                    report("Verifying video player download...", listener);
                    if (!Files.isRegularFile(archive, LinkOption.NOFOLLOW_LINKS) || Files.size(archive)!=spec.bytes()
                        || !hash(archive,cancelled).equals(spec.sha256()))
                        throw new IOException("Video player download failed its SHA-256 check. It was not installed; retry the video operation.");
                    Path extracted = staging.resolve("files"); Files.createDirectory(extracted);
                    report("Extracting verified video player...", listener);
                    extractor.extract(archive, extracted, spec.files().keySet(), cancelled);
                    check(cancelled);
                    if (!valid(extracted,cancelled)) throw new IOException("Extracted video player files failed verification. Nothing was installed.");
                    // Never remove a previous cache before the replacement has passed all checks.
                    Path previous = null;
                    if (Files.exists(target,LinkOption.NOFOLLOW_LINKS)) {
                        if (Files.isSymbolicLink(target)) throw new IOException("Player cache target must not be a symbolic link.");
                        previous=child(".previous-"+spec.build()+"-"+UUID.randomUUID()); move(target,previous);
                    }
                    try { move(extracted,target); }
                    catch (IOException failure) { if (previous!=null) move(previous,target); throw failure; }
                    verified.remove(extracted);
                    // The bytes were just checked in staging; record their new location.
                    verified.put(target,fingerprint(target));
                    if (previous!=null) clean(previous);
                    report("Video player ready; cached for offline use.", listener);
                    return target;
                } finally { lock.release(); }
            }
        } finally {
            if (staging!=null) clean(staging);
            progress=""; setup.unlock();
        }
    }

    private Path child(String name) throws IOException {
        Path path=root.resolve(name).toAbsolutePath().normalize();
        if (path.equals(root) || !path.getParent().equals(root)) throw new IOException("Invalid player cache path.");
        return path;
    }
    private List<String> fingerprint(Path folder) throws IOException {
        List<String> values=new ArrayList<>();
        for (String name:new TreeSet<>(spec.files().keySet())) {
            BasicFileAttributes a=Files.readAttributes(folder.resolve(name),BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
            if (!a.isRegularFile() || a.isSymbolicLink()) throw new IOException("Player component is not a regular file.");
            values.add(name+":"+a.size()+":"+a.lastModifiedTime()+":"+a.fileKey());
        }
        return values;
    }
    private boolean valid(Path folder, BooleanSupplier cancelled) throws IOException {
        if (folder==null || !Files.isDirectory(folder,LinkOption.NOFOLLOW_LINKS)) return false;
        List<String> before;
        try { before=fingerprint(folder); } catch (IOException missing) { verified.remove(folder); return false; }
        if (before.equals(verified.get(folder))) return true;
        for (var file:spec.files().entrySet()) {
            check(cancelled);
            if (!hash(folder.resolve(file.getKey()),cancelled).equals(file.getValue())) { verified.remove(folder); return false; }
        }
        if (!before.equals(fingerprint(folder))) throw new IOException("Player files changed during verification.");
        verified.put(folder,before); return true;
    }
    static String hash(Path file, BooleanSupplier cancelled) throws IOException {
        try (InputStream in=Files.newInputStream(file)) {
            MessageDigest digest=MessageDigest.getInstance("SHA-256"); byte[] buffer=new byte[1024*1024];
            for (int count;(count=in.read(buffer))!=-1;) { check(cancelled); digest.update(buffer,0,count); }
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    private static void move(Path from,Path to)throws IOException {
        try { Files.move(from,to,StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException fallback) { Files.move(from,to); }
    }
    private void clean(Path owned) {
        Path absolute=owned.toAbsolutePath().normalize();
        if (!absolute.getParent().equals(root) || !(absolute.getFileName().toString().startsWith(".player-") || absolute.getFileName().toString().startsWith(".previous-")))
            throw new IllegalArgumentException("Refusing cleanup outside owned player staging");
        // No following of symbolic links; a running process can leave its old cache for later cleanup.
        try { Files.walkFileTree(absolute,new SimpleFileVisitor<>() {
            @Override public FileVisitResult visitFile(Path file,BasicFileAttributes attributes)throws IOException { Files.delete(file);return FileVisitResult.CONTINUE; }
            @Override public FileVisitResult postVisitDirectory(Path directory,IOException error)throws IOException { if(error!=null)throw error;Files.delete(directory);return FileVisitResult.CONTINUE; }
        }); } catch(IOException ignored) { /* Never invalidate a successfully prepared player because cleanup failed. */ }
    }
    private static Path systemTool(String relative)throws IOException {
        String windows=System.getenv("SystemRoot");
        if(windows==null || windows.isBlank())throw new IOException("Player setup requires Windows with the built-in tar.exe.");
        Path tool=Path.of(windows,"System32").resolve(relative);
        if(!Files.isRegularFile(tool))throw new IOException("Player setup needs Windows component "+tool.getFileName()+". No player files were changed.");
        return tool;
    }
    private static void download(Spec spec,Path archive,Consumer<String> progress,BooleanSupplier cancelled)throws Exception {
        // java.base is present in Bitwig's reduced JRE; java.net.http is not.
        // Avoid a PowerShell child: it can hang during startup inside the host.
        // No JVM-wide proxy/TLS changes. Use its existing default connection settings.
        URI uri=URI.create(spec.url());HttpURLConnection connection=null;
        long deadline=System.nanoTime()+TimeUnit.MINUTES.toNanos(3);int last=-1;
        try {
            progress.accept("Downloading video player 0% (first use)...");
            for(int redirects=0;;redirects++) {
                check(cancelled);
                if(System.nanoTime()>deadline)throw new IOException("Video player download timed out.");
                if(!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo()!=null)
                    throw new IOException("Player download requires an HTTPS URL without embedded credentials.");
                connection=(HttpURLConnection)uri.toURL().openConnection();
                connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(15000);connection.setReadTimeout(10000);
                connection.setRequestProperty("User-Agent","NativeVideoTrack/0.5.0-dev.3");
                connection.setRequestProperty("Accept-Encoding","identity");
                int code=connection.getResponseCode();
                if(Set.of(301,302,303,307,308).contains(code)) {
                    String next=connection.getHeaderField("Location");
                    if(next==null || redirects>=5)throw new IOException("Too many or invalid player download redirects.");
                    uri=uri.resolve(next);connection.disconnect();connection=null;continue;
                }
                if(code!=200)throw new IOException("Player download returned HTTP "+code+".");
                long declared=connection.getContentLengthLong();
                if(declared>=0 && declared!=spec.bytes())throw new IOException("Unexpected player download size.");
                break;
            }
            try(InputStream input=connection.getInputStream();OutputStream output=Files.newOutputStream(archive,StandardOpenOption.CREATE_NEW)) {
                byte[] buffer=new byte[64*1024];long bytes=0;
                for(int count;(count=input.read(buffer))!=-1;) {
                    check(cancelled);
                    if(System.nanoTime()>deadline)throw new IOException("Video player download timed out.");
                    bytes+=count;if(bytes>spec.bytes())throw new IOException("Unexpected player download size; download stopped.");
                    output.write(buffer,0,count);
                    int percent=(int)Math.min(99,bytes*100/spec.bytes());
                    if(percent!=last){progress.accept("Downloading video player "+percent+"% (first use)...");last=percent;}
                }
            }
            check(cancelled);
        } catch(IOException failure) {
            check(cancelled);
            throw new IOException("Could not download the video player from GitHub ("+failure.getClass().getSimpleName()+"). Check the connection/proxy and retry; existing clips are unchanged.",failure);
        } finally { if(connection!=null)connection.disconnect(); }
    }
    private static void extract(Path archive,Path into,Set<String> files,BooleanSupplier cancelled)throws Exception {
        List<String> args=new ArrayList<>(List.of(systemTool("tar.exe").toString(),"-xf",archive.toString(),"-C",into.toString()));
        args.addAll(new TreeSet<>(files));
        ProcessBuilder builder=new ProcessBuilder(args).redirectErrorStream(true).redirectOutput(archive.resolveSibling("extract.log").toFile());
        builder.environment().remove("JAVA_TOOL_OPTIONS");
        Process process=builder.start();long deadline=System.nanoTime()+TimeUnit.MINUTES.toNanos(2);
        try {
            while(!process.waitFor(150,TimeUnit.MILLISECONDS)) { check(cancelled);if(System.nanoTime()>deadline)throw new IOException("Video player extraction timed out. Retry the video operation."); }
            check(cancelled);
            if(process.exitValue()!=0)throw new IOException("Windows tar.exe could not extract the verified player. Update Windows and retry; existing clips are unchanged.");
        } finally { stop(process); }
    }
    private static void stop(Process process) {
        if(process.isAlive()) {
            process.destroy();
            try { if(!process.waitFor(2,TimeUnit.SECONDS))process.destroyForcibly().waitFor(2,TimeUnit.SECONDS); }
            catch(InterruptedException interrupted){process.destroyForcibly();Thread.currentThread().interrupt();}
        }
    }
}
