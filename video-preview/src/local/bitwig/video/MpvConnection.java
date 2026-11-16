package local.bitwig.video;

import com.google.gson.*;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/** All process / pipe operations belong on a worker, never Bitwig's event thread. */
final class MpvConnection implements AutoCloseable {
    private static final Gson JSON = new Gson();
    private final Process process;
    private WinNT.HANDLE pipe;
    private Thread reader;
    private final Object pipeLock = new Object();
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
    private interface PipeIO extends StdCallLibrary {
        PipeIO INSTANCE=Native.load("kernel32",PipeIO.class,W32APIOptions.DEFAULT_OPTIONS);
        boolean ReadFile(WinNT.HANDLE handle,Pointer buffer,int length,Pointer bytes,Pointer overlapped);
        boolean WriteFile(WinNT.HANDLE handle,Pointer buffer,int length,Pointer bytes,Pointer overlapped);
        boolean GetOverlappedResult(WinNT.HANDLE handle,Pointer overlapped,IntByReference bytes,boolean wait);
        boolean CancelIoEx(WinNT.HANDLE handle,Pointer overlapped);
    }

    MpvConnection(Path player, Path log, boolean visible) throws Exception {
        this(player, log, visible, 0);
    }
    MpvConnection(Path player, Path log, boolean visible, long parentWindow) throws Exception {
        String pipeName = "\\\\.\\pipe\\bitwig-video-" + ProcessHandle.current().pid() + "-" + UUID.randomUUID();
        List<String> args = new ArrayList<>(List.of(player.resolve("mpv.exe").toString(),
            "--no-config", "--load-scripts=no", "--idle=yes", "--pause=yes", "--keep-open=yes",
            "--no-audio", "--sub=no", "--osc=no", "--osd-level=0", "--osd-font=Microsoft YaHei",
            "--input-terminal=no", "--input-default-bindings=no", "--input-builtin-bindings=no",
            "--input-builtin-drag-and-drop=no", "--drag-and-drop=no", "--ytdl=no",
            "--input-ipc-server=" + pipeName, "--terminal=no", "--log-file=" + log,
            "--msg-level=all=warn", "--title=Bitwig Video Preview", "--autofit=960x540", "--hwdec=auto-safe"));
        if (visible) {
            args.add("--force-window=immediate");
            if(parentWindow!=0)args.add("--wid="+Long.toUnsignedString(parentWindow));
        } else args.addAll(List.of("--vo=null", "--force-window=no"));
        ProcessBuilder builder = new ProcessBuilder(args);
        // The JVM launcher's agent configuration must never propagate to unrelated helpers.
        builder.environment().remove("JAVA_TOOL_OPTIONS");
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD);
        process = builder.start();
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
            IOException last = null;
            while (pipe == null && process.isAlive() && System.nanoTime() < deadline) {
                WinNT.HANDLE opened = Kernel32.INSTANCE.CreateFile(pipeName, 0xC0000000, 0, null, 3, 0x40000000, null);
                if (!WinBase.INVALID_HANDLE_VALUE.equals(opened)) pipe = opened;
                else { last = new IOException("Named pipe: Windows error " + Kernel32.INSTANCE.GetLastError()); Thread.sleep(30); }
            }
            if (pipe == null) throw new IOException("播放器 IPC 未就绪；请查看 " + log, last);
            reader = new Thread(this::readLoop, "Video-preview-mpv-reader"); reader.setDaemon(true); reader.start();
            request("observe_property", 1, "time-pos");
            request("observe_property", 2, "pause");
            request("observe_property", 3, "speed");
        } catch (Exception e) { close(); throw e; }
    }

    boolean alive() { return !closed && process.isAlive(); }
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
            try(Memory buffer=new Memory(bytes.length)){
                buffer.write(0,bytes,0,bytes.length);
                if(transfer(false,buffer,bytes.length)!=bytes.length)throw new IOException("IPC short write");
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

    /** Independent overlapped read/write operations: wait for data, never poll/sleep or spin.
     * Native buffers and OVERLAPPED memory remain alive until completion/cancellation. */
    private int transfer(boolean reading,Memory buffer,int length)throws IOException{
        WinNT.HANDLE handle=pipe;
        WinBase.OVERLAPPED operation=new WinBase.OVERLAPPED();
        operation.hEvent=Kernel32.INSTANCE.CreateEvent(null,true,false,null);
        if(operation.hEvent==null)throw new IOException("IPC event creation failed");
        operation.write();boolean pendingIO=false;
        try{
            boolean done;
            synchronized(pipeLock){
                if(closed)throw new IOException("播放器已关闭");
                done=reading?PipeIO.INSTANCE.ReadFile(handle,buffer,length,null,operation.getPointer())
                    :PipeIO.INSTANCE.WriteFile(handle,buffer,length,null,operation.getPointer());
                int error=Native.getLastError();
                if(!done&&error!=997)throw new IOException("IPC "+(reading?"read":"write")+": Windows error "+error);
                pendingIO=!done;
            }
            IntByReference transferred=new IntByReference();
            if(!reading&&pendingIO&&Kernel32.INSTANCE.WaitForSingleObject(operation.hEvent,3000)!=0)
                throw new IOException("IPC write timed out");
            boolean complete=PipeIO.INSTANCE.GetOverlappedResult(handle,operation.getPointer(),transferred,reading);
            pendingIO=false;
            if(!complete)throw new IOException("IPC completion: Windows error "+Native.getLastError());
            return transferred.getValue();
        }finally{
            if(pendingIO){
                PipeIO.INSTANCE.CancelIoEx(handle,operation.getPointer());
                PipeIO.INSTANCE.GetOverlappedResult(handle,operation.getPointer(),new IntByReference(),true);
            }
            Kernel32.INSTANCE.CloseHandle(operation.hEvent);
        }
    }

    private void readLoop() {
        try(Memory buffer=new Memory(65536)) {
            ByteArrayOutputStream line = new ByteArrayOutputStream();
            while (!closed) {
                int count=transfer(true,buffer,65536);
                if(count==0)throw new IOException("IPC closed");
                byte[] bytes=buffer.getByteArray(0,count);
                for (byte b : bytes) {
                int value = b & 255;
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
        try { if (pipe != null && process.isAlive()) request("quit"); } catch (Exception ignored) {}
        closed = true;
        synchronized(pipeLock){if(pipe!=null)PipeIO.INSTANCE.CancelIoEx(pipe,null);}
        // Terminating our owned player also releases a blocking Windows pipe read.
        process.destroy();
        try { if (!process.waitFor(800, TimeUnit.MILLISECONDS)) process.destroyForcibly(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        try{if(reader!=null&&Thread.currentThread()!=reader)reader.join(1000);}catch(InterruptedException e){Thread.currentThread().interrupt();}
        synchronized (pipeLock) { if (pipe != null) Kernel32.INSTANCE.CloseHandle(pipe); }
        failPending(new IOException("播放器已关闭"));
    }
}
