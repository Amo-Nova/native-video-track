package local.bitwig.video;

import com.google.gson.*;
import java.io.*;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.UnsupportedAddressTypeException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/** All process / socket operations belong on a worker, never Bitwig's event thread.
 *  macOS port: the player IPC is mpv's Unix domain socket (--input-ipc-server); the
 *  request numbering, timeout, seek and confirmed-frame rules are unchanged. */
final class MpvConnection implements AutoCloseable {
    private static final Gson JSON = new Gson();
    private final Process process;
    private SocketChannel channel;
    private Thread reader;
    private final Object pipeLock = new Object();
    private final Path socketPath;
    private final AtomicLong ids = new AtomicLong();
    private final ConcurrentMap<Long, CompletableFuture<JsonElement>> pending = new ConcurrentHashMap<>();
    private volatile boolean closed;
    private volatile double position = Double.NaN;
    private volatile long positionAt;
    private volatile boolean paused = true;
    private volatile double speed = 1;
    private volatile long loadedGeneration;
    private volatile long readyGeneration;
    private volatile String loadFailure;
    private final Object seekLock = new Object();
    private boolean seekInFlight, seekStarted;
    private long seekAt, seekSerial, completedSeeks, reusedFrames, steppedFrames, retargetedSeeks, lastRetarget;
    private double seekTarget;
    private final AtomicLong pauseEpoch=new AtomicLong();
    private long seekPausedEpoch=-1,confirmedEpoch=-1;
    private volatile double mediaDuration;
    private double confirmedFrame=Double.NaN;
    // Only time intervals proven by completed exact seeks; no image cache or FPS assumptions.
    private final LinkedHashMap<Double,double[]> exactFrames=new LinkedHashMap<>(32,0.75f,true);
    private volatile IOException seekFailure;

    MpvConnection(Path player, Path log, boolean visible) throws Exception {
        this(player, log, visible, null);
    }
    MpvConnection(Path player, Path log, boolean visible, String geometry) throws Exception {
        // macOS limits a Unix domain socket path to ~104 bytes; Bitwig's java.io.tmpdir is a long
        // per-app /var/folders/... path, so the socket lives under /tmp with a short name.
        socketPath = shortSocketPath();
        Files.deleteIfExists(socketPath);
        List<String> args = new ArrayList<>(List.of(MpvBin.resolve(player).toString(),
            "--no-config", "--load-scripts=no", "--idle=yes", "--pause=yes", "--keep-open=yes",
            "--no-audio", "--sub=no", "--osc=no", "--osd-level=0", "--osd-font=PingFang SC",
            "--input-terminal=no", "--input-default-bindings=no", "--input-builtin-bindings=no",
            "--input-builtin-drag-and-drop=no", "--drag-and-drop=no", "--ytdl=no",
            "--input-ipc-server=" + socketPath, "--terminal=no", "--log-file=" + log,
            "--msg-level=all=warn", "--title=Bitwig Video Preview", "--hwdec=auto-safe"));
        if (visible) {
            // macOS has no cross-process window embedding (--wid is X11/Win32/Android only):
            // the preview is mpv's own top-level window at the saved floating geometry, kept
            // above Bitwig so it stays visible while editing (置顶).
            args.add("--force-window=immediate");
            args.add("--ontop=yes");
            args.add(geometry == null || geometry.isBlank() ? "--geometry=960x540+100+100" : "--geometry=" + geometry);
        } else args.addAll(List.of("--vo=null", "--force-window=no"));
        ProcessBuilder builder = new ProcessBuilder(args);
        // The JVM launcher's agent configuration must never propagate to unrelated helpers.
        builder.environment().remove("JAVA_TOOL_OPTIONS");
        builder.environment().put("PATH", "/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin");
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD);
        process = builder.start();
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
            IOException last = null;
            while (channel == null && process.isAlive() && System.nanoTime() < deadline) {
                try {
                    SocketChannel opened = SocketChannel.open(java.net.StandardProtocolFamily.UNIX);
                    try {
                        opened.connect(UnixDomainSocketAddress.of(socketPath.toString()));
                        opened.configureBlocking(true);
                        channel = opened;
                    } catch (IOException | UnsupportedAddressTypeException e) {
                        try { opened.close(); } catch (IOException ignored) {}
                        throw e instanceof IOException ? (IOException) e : new IOException(String.valueOf(e));
                    }
                } catch (IOException e) { last = e; Thread.sleep(30); }
            }
            if (channel == null) throw new IOException("播放器 IPC 未就绪；请查看 " + log, last);
            reader = new Thread(this::readLoop, "Video-preview-mpv-reader"); reader.setDaemon(true); reader.start();
            request("observe_property", 1, "time-pos");
            request("observe_property", 2, "pause");
            request("observe_property", 3, "speed");
        } catch (Exception e) { close(); throw e; }
    }

    /** A socket path that stays inside the kernel's sun_path limit (104 bytes on macOS). */
    private static Path shortSocketPath() {
        String name = "bwv-" + ProcessHandle.current().pid() + "-"
            + UUID.randomUUID().toString().substring(0, 8) + ".sock";
        for (String base : new String[]{"/tmp", System.getProperty("java.io.tmpdir", "/tmp"),
                System.getProperty("user.home", "/tmp")}) {
            try {
                Path candidate = Path.of(base).resolve(name).toAbsolutePath().normalize();
                if (candidate.toString().length() <= 100 && candidate.getParent() != null
                    && Files.isDirectory(candidate.getParent())) return candidate;
            } catch (Exception ignored) {}
        }
        return Path.of("/tmp", name);
    }

    boolean alive() { return !closed && process.isAlive(); }
    /** The player ended on its own with a clean exit: on macOS that is the user closing
     *  the preview window (mpv quits; our own close() is excluded by {@code closed}). */
    boolean userExited() {
        try { return !closed && !process.isAlive() && process.exitValue() == 0; }
        catch (IllegalThreadStateException stillRunning) { return false; }
    }
    synchronized CompletableFuture<JsonElement> request(Object... command) {
        CompletableFuture<JsonElement> future = new CompletableFuture<>();
        if (!alive()) return CompletableFuture.failedFuture(new IOException("视频窗口已关闭"));
        if(command.length>=3&&"set_property".equals(command[0])&&"pause".equals(command[1])&&Boolean.FALSE.equals(command[2])){
            pauseEpoch.incrementAndGet();
        }
        long id = ids.incrementAndGet(); pending.put(id, future);
        JsonObject json = new JsonObject(); json.add("command", JSON.toJsonTree(command)); json.addProperty("request_id", id);
        try {
            byte[] bytes = (JSON.toJson(json) + "\n").getBytes(StandardCharsets.UTF_8);
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                int written;
                synchronized (pipeLock) {
                    if (closed) throw new IOException("播放器已关闭");
                    written = channel.write(buffer);
                }
                if (written < 0) throw new IOException("IPC short write");
            }
        }
        catch (IOException e) { pending.remove(id); future.completeExceptionally(e); }
        future.orTimeout(3, TimeUnit.SECONDS).whenComplete((v, e) -> pending.remove(id));
        return future;
    }
    JsonElement get(String name) throws Exception { return request("get_property", name).get(3500, TimeUnit.MILLISECONDS); }
    void set(String name, Object value) { request("set_property", name, value); }
    /** A command reply is not a decoded frame. Let each seek produce a frame before another. */
    boolean seek(double seconds) throws IOException {
        synchronized (seekLock) {
            checkSeek();
            if (seekInFlight) return false;
            return beginSeek(seconds);
        }
    }
    /** Only the settled final drag target may replace an obsolete long decode. Continuous
     * motion still finishes frames; the cooldown prevents repeated quiet bursts starving it. */
    boolean retargetStoppedFinal(double seconds)throws IOException{
        synchronized(seekLock){
            checkSeek();long now=System.nanoTime();
            if(!seekInFlight||!paused||seekPausedEpoch!=pauseEpoch.get()
                ||Math.abs(seconds-seekTarget)<.0001||now-seekAt<80_000_000L
                ||now-lastRetarget<500_000_000L)return false;
            lastRetarget=now;retargetedSeeks++;
            return beginSeek(seconds);
        }
    }
    private boolean beginSeek(double seconds){
            seekInFlight = true; seekStarted = false; seekAt = System.nanoTime();
            seekTarget=seconds;
            seekPausedEpoch=paused?pauseEpoch.get():-1;
            long serial = ++seekSerial;
            request("seek", seconds, "absolute+exact").whenComplete((value, failure) -> {
                if (failure != null) synchronized (seekLock) {
                    if (serial == seekSerial) {
                        seekInFlight = false;
                        seekFailure = new IOException("视频定位失败", failure);
                    }
                }
            });
            return true;
    }
    void checkSeek() throws IOException {
        synchronized (seekLock) {
            if (seekFailure != null) throw seekFailure;
            if (seekInFlight && System.nanoTime() - seekAt > TimeUnit.SECONDS.toNanos(8))
                throw new IOException("视频精确定位超时；请重新显示预览重试");
        }
    }
    boolean seeking() { synchronized (seekLock) { return seekInFlight; } }
    long completedSeeks() { synchronized (seekLock) { return completedSeeks; } }
    long reusedFrames() { synchronized (seekLock) { return reusedFrames; } }
    long steppedFrames() { synchronized (seekLock) { return steppedFrames; } }
    long retargetedSeeks() { synchronized (seekLock) { return retargetedSeeks; } }
    /** An exact seek to t returned frame p: every timestamp between t and p selects p.
     * Reuse only that proven interval while p is still the paused decoded frame. */
    boolean reuseStoppedFrame(double target)throws IOException{
        synchronized(seekLock){
            checkSeek();if(!paused||seekInFlight||confirmedEpoch!=pauseEpoch.get()||position!=confirmedFrame)return false;
            double[] interval=exactFrames.get(position);
            if(interval==null||target<interval[0]||target>interval[1])return false;
            reusedFrames++;return true;
        }
    }
    /** Worker-only bounded forward decode. Keeps the decoder's GOP instead of flushing it.
     * Uses actual adjacent PTS values, including VFR, never a container FPS estimate. */
    boolean stepStoppedForward(double target)throws Exception{
        double frame,goal=Double.NaN;long serial,epoch;
        synchronized(seekLock){
            checkSeek();
            if(!paused||seekInFlight||confirmedEpoch!=pauseEpoch.get()
                ||position!=confirmedFrame||target>=mediaDuration-1
                ||target-position<=0||target-position>.090)return false;
            // Demuxer/keyframe rounding can differ from sequential frame selection (open GOP).
            // Only walk to a frame already proven by a full exact seek for this target interval.
            for(var entry:exactFrames.entrySet()){
                double[] interval=entry.getValue();
                if(target>=interval[0]&&target<=interval[1]){goal=entry.getKey();break;}
            }
            if(!Double.isFinite(goal)||goal<=position||goal-position>.100)return false;
            frame=position;epoch=pauseEpoch.get();serial=++seekSerial;
            seekInFlight=true;seekStarted=false;seekAt=System.nanoTime();
        }
        boolean accepted=false;
        try{
            long deadline=System.nanoTime()+120_000_000L;
            for(int count=0;count<8&&System.nanoTime()<deadline;count++){
                double previous=frame;
                request("frame-step").get(3500,TimeUnit.MILLISECONDS);
                boolean stopped=false;
                while(System.nanoTime()<deadline){
                    var pauseReply=request("get_property","pause");
                    var frameReply=request("get_property","time-pos");
                    stopped=pauseReply.get(3500,TimeUnit.MILLISECONDS).getAsBoolean();
                    frame=frameReply.get(3500,TimeUnit.MILLISECONDS).getAsDouble();
                    if(stopped)break;
                    Thread.sleep(2);
                }
                if(!stopped||frame<previous||frame>=mediaDuration-1||epoch!=pauseEpoch.get())return false;
                // Some video outputs consume the first step by presenting the seek's frame.
                // A completed step with unchanged PTS may safely request the next one.
                if(frame==previous)continue;
                // A timestamp discontinuity or sparse stream must use the original exact path.
                if(frame-previous>.100)return false;
                synchronized(seekLock){
                    if(serial!=seekSerial)return false;
                    steppedFrames++;position=frame;positionAt=System.nanoTime();
                }
                if(Math.abs(frame-goal)<.000001){accepted=true;return true;}
                if(frame>goal)return false;
            }
            return false;
        }finally{
            // Also cancel any unfinished step before the caller falls back or starts playback.
            request("set_property","pause",true).get(3500,TimeUnit.MILLISECONDS);
            synchronized(seekLock){if(serial==seekSerial){
                seekInFlight=false;
                if(accepted){confirmedFrame=frame;confirmedEpoch=epoch;}
                else confirmedEpoch=-1;
            }}
        }
    }
    private void rememberFrame(double frame,double low,double high){
        double[] prior=exactFrames.get(frame);
        if(prior!=null){low=Math.min(low,prior[0]);high=Math.max(high,prior[1]);}
        exactFrames.put(frame,new double[]{low,high});
        while(exactFrames.size()>128)exactFrames.remove(exactFrames.keySet().iterator().next());
    }
    private void decodedSeek(long serial,double target){
        synchronized(seekLock){if(serial!=seekSerial||!seekInFlight||closed)return;}
        // Both queries are ordered after playback-restart and before any next seek. mpv's
        // seeking=false means restart_complete; time-pos is then the actual playback PTS.
        // Batch the reads: repeated serial round trips add substantial background-window latency.
        var seekingReply=request("get_property","seeking");
        var frameReply=request("get_property","time-pos");
        seekingReply.thenCombine(frameReply,(seeking,value)->{
            if(seeking!=null&&!seeking.isJsonNull()&&seeking.getAsBoolean())
                return (JsonElement)null;
            return value;
        }).whenComplete((value,failure)->{
            synchronized(seekLock){
                if(serial!=seekSerial||!seekInFlight)return;
                if(failure!=null){
                    seekInFlight=false;seekFailure=new IOException("无法确认视频定位帧",failure);return;
                }
                double frame=value==null||value.isJsonNull()?Double.NaN:value.getAsDouble();
                boolean stopped=paused&&seekPausedEpoch==pauseEpoch.get();
                if(!Double.isFinite(frame)){
                    CompletableFuture.delayedExecutor(3,TimeUnit.MILLISECONDS)
                        .execute(()->decodedSeek(serial,target));
                    return;
                }
                // At EOF mpv may report container duration rather than a displayed frame PTS.
                // Keep the last second on the original exact path, including sparse/VFR tails.
                if(stopped
                    &&target<mediaDuration-1&&frame<mediaDuration-1){
                    rememberFrame(frame,Math.min(frame,target),Math.max(frame,target));
                    confirmedFrame=frame;confirmedEpoch=seekPausedEpoch;
                }
                if(Double.isFinite(frame)){position=frame;positionAt=System.nanoTime();}
                seekInFlight=false;completedSeeks++;
            }
        });
    }

    double load(Path file) throws Exception {
        if (!Files.isRegularFile(file)) throw new IOException("找不到视频，请选择 / 重联：" + file);
        long generation = loadedGeneration;
        loadFailure = null;mediaDuration=0;
        synchronized (seekLock) { seekSerial++; seekInFlight = false; seekStarted = false; seekFailure = null; exactFrames.clear(); confirmedEpoch=-1; lastRetarget=0; }
        set("pause", true);
        request("loadfile", file.toAbsolutePath().toString(), "replace").get(4, TimeUnit.SECONDS);
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(12);
        Exception last = null;
        while (alive() && System.nanoTime() < until) {
            if (loadFailure != null) throw new IOException("无法解码视频：" + loadFailure);
            if (loadedGeneration <= generation || readyGeneration <= generation) { Thread.sleep(25); continue; }
            try {
                JsonElement codec = get("video-codec");
                JsonElement duration = get("duration");
                if (codec != null && !codec.isJsonNull() && duration != null && !duration.isJsonNull()) {
                    double seconds = duration.getAsDouble();
                    if (Double.isFinite(seconds) && seconds > 0) {
                        mediaDuration=seconds;
                        return seconds;
                    }
                }
            } catch (Exception e) { last = e; }
            Thread.sleep(60);
        }
        throw new IOException("无法读取有限时长的视频流；请检查文件或编码。", last);
    }

    double predictedPosition(long now) {
        double age = Math.max(0, Math.min(0.25, (now - positionAt) / 1e9));
        return position + (paused ? 0 : age * speed);
    }

    private void readLoop() {
        try {
            ByteBuffer buffer = ByteBuffer.allocate(65536);
            ByteArrayOutputStream line = new ByteArrayOutputStream();
            while (!closed) {
                buffer.clear();
                int count = channel.read(buffer); // blocking; close() unblocks it
                if (count < 0) throw new IOException("IPC closed");
                buffer.flip();
                while (buffer.hasRemaining()) {
                    int value = buffer.get() & 255;
                    if (value == 10) {
                        JsonObject message = JsonParser.parseString(line.toString(StandardCharsets.UTF_8)).getAsJsonObject(); line.reset();
                        if (message.has("request_id")) {
                            CompletableFuture<JsonElement> future = pending.remove(message.get("request_id").getAsLong());
                            if (future != null) {
                                String error = message.has("error") ? message.get("error").getAsString() : "success";
                                if ("success".equals(error)) future.complete(message.get("data"));
                                else future.completeExceptionally(new IOException("mpv: " + error));
                            }
                        } else if (message.has("event") && "file-loaded".equals(message.get("event").getAsString())) {
                            loadedGeneration++;
                        } else if (message.has("event") && "seek".equals(message.get("event").getAsString())) {
                            synchronized (seekLock) { if (seekInFlight) seekStarted = true; }
                        } else if (message.has("event") && "playback-restart".equals(message.get("event").getAsString())) {
                            readyGeneration = loadedGeneration;
                            synchronized (seekLock) {
                                if (seekInFlight && seekStarted) { seekStarted=false;decodedSeek(seekSerial,seekTarget); }
                            }
                        } else if (message.has("event") && "end-file".equals(message.get("event").getAsString()) && message.has("reason") && "error".equals(message.get("reason").getAsString())) {
                            loadFailure = message.has("file_error") ? message.get("file_error").getAsString() : "文件读取失败";
                        } else if (message.has("event") && "property-change".equals(message.get("event").getAsString()) && message.has("data") && !message.get("data").isJsonNull()) {
                            String name = message.get("name").getAsString(); JsonElement data = message.get("data");
                            if ("time-pos".equals(name)) { position = data.getAsDouble(); positionAt = System.nanoTime(); }
                            if ("pause".equals(name)) paused = data.getAsBoolean();
                            if ("speed".equals(name)) speed = data.getAsDouble();
                        }
                    } else { line.write(value); if (line.size() > 1_048_576) throw new IOException("IPC message too large"); }
                }
            }
        } catch (Exception e) { if (!closed) failPending(e); }
        finally { failPending(new IOException("播放器连接已断开")); }
    }
    private void failPending(Exception e) { pending.values().forEach(f -> f.completeExceptionally(e)); pending.clear(); }
    public void close() {
        if (closed) return;
        try { if (channel != null && channel.isConnected() && process.isAlive()) request("quit"); } catch (Exception ignored) {}
        closed = true;
        try { if (channel != null) channel.close(); } catch (IOException ignored) {} // releases a blocking read
        // Terminating our owned player also releases the socket.
        process.destroy();
        try { if (!process.waitFor(800, TimeUnit.MILLISECONDS)) process.destroyForcibly(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        try{if(reader!=null&&Thread.currentThread()!=reader)reader.join(1000);}catch(InterruptedException e){Thread.currentThread().interrupt();}
        try { Files.deleteIfExists(socketPath); } catch (IOException ignored) {}
        failPending(new IOException("播放器已关闭"));
    }
}
