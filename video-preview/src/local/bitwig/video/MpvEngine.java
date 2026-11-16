package local.bitwig.video;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.*;
import java.util.function.Consumer;

final class MpvEngine implements AutoCloseable {
    private final Path player, logs;
    private final ScheduledExecutorService worker;
    private final ExecutorService probeWorker;
    private volatile BitwigBridge.Snapshot latest;
    private volatile boolean wanted = true;
    private volatile boolean reset;
    private volatile String status = "选择视频后跟随 Bitwig 播放";
    private volatile String timeStatus = "";
    private MpvConnection connection;
    private String loaded = "";
    private Object loadedDocument;
    private double duration, lastDesired = Double.NaN, appliedSpeed = 1;
    private boolean wasPlaying, seekPending, catchingUp;
    private long lastSeek, lastLog, lastFlush;
    private BitwigBridge.Snapshot prior;
    private BufferedWriter trace;
    private long traceRows;
    private final DockHost dock;
    private long attachedWindow;
    private volatile double frameRate;
    private Object failedDocument;
    private String failedFile="";
    private volatile long retryAfter;
    private final ScrubCadence scrub=new ScrubCadence();
    double frameRate(){return frameRate;}

    MpvEngine(Path player, Path logs, DockHost dock) {
        this.dock = dock;
        this.player = player; this.logs = logs;
        worker = Executors.newSingleThreadScheduledExecutor(r -> daemon(r, "Video-preview-sync"));
        probeWorker = Executors.newSingleThreadExecutor(r -> daemon(r, "Video-preview-probe"));
        worker.scheduleWithFixedDelay(this::tickSafely, 0, 20, TimeUnit.MILLISECONDS);
    }
    private static Thread daemon(Runnable r, String name) { Thread t = new Thread(r, name); t.setDaemon(true); return t; }
    void accept(BitwigBridge.Snapshot s) { latest = s; }
    void show() { wanted = true; reset = true; }
    void enable() { wanted = true; retryAfter=0; }
    void hide() { wanted = false; }
    String status() { return status; }
    String timeStatus() { return timeStatus; }
    void probe(Path path, Consumer<Throwable> done) {
        probeInfo(path,(duration,failure)->done.accept(failure));
    }
    void probeInfo(Path path, java.util.function.BiConsumer<Double,Throwable> done) {
        status = "正在检查视频…";
        probeWorker.execute(() -> {
            try {
                Files.createDirectories(logs);
                double length;
                try (MpvConnection probe = new MpvConnection(player, logs.resolve("probe.log"), false)) { length=probe.load(path); }
                done.accept(length,null);
            } catch (Throwable failure) { status = "打开失败；原关联保持不变"; done.accept(0.0,failure); }
        });
    }

    private void tickSafely() {
        BitwigBridge.Snapshot attempted=latest;
        try { tick(); }
        catch (Throwable e) {
            status = "此视频暂不可用：" + e.getMessage() + "；正在等待恢复，将自动重试";
            BitwigBridge.MOD.log("Player failed", e);
            failedDocument=attempted==null?null:attempted.document();failedFile=attempted==null?"":attempted.playback().file();
            retryAfter=System.nanoTime()+5_000_000_000L;disconnect();
        }
    }

    private void tick() throws Exception {
        BitwigBridge.Snapshot s = latest;
        if (s == null) return;
        if (reset) { reset = false; disconnect(); }
        if (connection != null && !connection.alive()) {
            disconnect(); status = "正在恢复视频播放器…"; return;
        }
        if (!wanted || s.document() == null || !s.playback().enabled()) {
            disconnect();
            status = !wanted ? "预览已隐藏；工程的视频关联保留" : s.document() == null ? "等待 Bitwig 工程" : s.playback().file().isEmpty() ? VideoTimeline.active()?"此处没有视频片段":"拖入视频到编曲区，或点击选择 / 设置导入" : "当前工程的视频预览已停用";
            timeStatus = ""; return;
        }
        if (connection != null) connection.checkSeek();
        if (s.playback().file().isEmpty()) {
            // Clip edits cross empty timeline space repeatedly. Hide via DockHost, retain the
            // paused decoder. Re-locate on re-entry when source time changes. Never retain another project.
            if (loadedDocument != s.document()) disconnect();
            else suspend();
            status = VideoTimeline.active() ? "此处没有视频片段" : "拖入视频到编曲区，或点击选择 / 设置导入";
            timeStatus = ""; return;
        }
        // A failing movie must not disable every other clip. Back off only this source;
        // another source/project can load immediately, and repaired media retries itself.
        if(failedDocument==s.document()&&failedFile.equals(s.playback().file())&&System.nanoTime()<retryAfter)return;
        if (System.nanoTime() - s.nanos() > TimeUnit.MILLISECONDS.toNanos(500)) {
            suspend(); status = "等待 Bitwig 时钟恢复"; return;
        }
        if(!dock.visible()) {
            suspend();status="点击 Video 或“恢复视频画面”显示预览";return;
        }
        if(connection!=null && attachedWindow!=dock.handle())disconnect();
        if (connection == null) {
            Files.createDirectories(logs);
            attachedWindow=dock.handle();
            connection = new MpvConnection(player, logs.resolve("player.log"), true, attachedWindow);
            if (trace == null) {
                trace = Files.newBufferedWriter(logs.resolve("sync-" + System.currentTimeMillis() + ".csv"), StandardCharsets.UTF_8);
                trace.write("utc,project_s,target_s,player_s,error_s,playing,event\n");
            }
        }
        if (!loaded.equals(s.playback().file()) || loadedDocument != s.document()) {
            status = "正在载入视频…";
            duration = connection.load(Path.of(s.playback().file()));
            try{frameRate=connection.get("container-fps").getAsDouble();}catch(Exception ignored){frameRate=0;}
            loaded = s.playback().file(); loadedDocument = s.document();
            prior = null; lastDesired = Double.NaN; wasPlaying = false; seekPending = false; catchingUp=false; scrub.reset();
            connection.set("title", "Bitwig Video Preview — " + Path.of(loaded).getFileName());
            // Re-sample after I/O; never continue using an old project's clock.
            return;
        }
        long now = System.nanoTime();
        double desired = s.seconds() - s.playback().offset() + (s.playing() ? Math.max(0, (now - s.nanos()) / 1e9) : 0);
        boolean outside = desired < 0 || desired >= duration;
        double target = Math.max(0, Math.min(duration - 0.001, desired));
        boolean play = s.playing() && !outside;
        boolean jump = prior == null || prior.document() != s.document() || !prior.playback().equals(s.playback());
        if (prior != null && prior.nanos() != s.nanos()) {
            double expected = prior.seconds() + (prior.playing() ? (s.nanos() - prior.nanos()) / 1e9 : 0);
            jump |= Math.abs(s.seconds() - expected) > 0.09;
        }
        double actual = connection.predictedPosition(now);
        double error = actual - target;
        String event = "follow";
        if (!play && wasPlaying) {
            connection.set("pause", true);lastDesired=Double.NaN;catchingUp=false;scrub.reset();
        }
        boolean drift = play && Double.isFinite(actual) && Math.abs(error) > 0.16
            && now-lastSeek>TimeUnit.MILLISECONDS.toNanos(catchingUp?1500:400);
        if(play) seekPending |= jump || play != wasPlaying || drift || Double.isNaN(lastDesired);
        else {
            // A moved clip can change both project time and offset while its SOURCE time stays
            // identical. That is not a video jump. Small changing targets share a frame cadence;
            // once input rests, even a sub-frame final change is sent exactly.
            seekPending=scrub.ready(target,lastDesired,frameRate,now);
            // The final sub-frame edit may already be represented by the decoded frame.
            // A proven exact-seek interval can accept it immediately without another GOP decode.
            if(Math.abs(target-lastDesired)>0.0001
                &&(connection.reuseStoppedFrame(target)||connection.stepStoppedForward(target))){
                lastDesired=target;seekPending=false;
            }
        }
        if (seekPending) {
            if (appliedSpeed != 1) { connection.set("speed", 1.0); appliedSpeed = 1; }
            // While decoding, only latest is retained. Recompute its source time on every tick;
            // updating lastDesired only on acceptance guarantees the final drag position is sent.
            if (connection.seek(target)||(!play&&scrub.settled(now)&&connection.retargetStoppedFinal(target))) {
                lastSeek = now; lastDesired = target; seekPending = false; catchingUp=play;
                event = jump ? "jump" : drift ? "drift" : "locate";
            }
        } else if (play && !connection.seeking() && Double.isFinite(error) && now - lastSeek > TimeUnit.MILLISECONDS.toNanos(200)) {
            // A cold 4K exact seek can finish behind the running transport. The steady-state
            // 3% correction takes seconds to erase that delay. Briefly converge the muted
            // preview after decoding, without repeated seeks or touching native audio/tempo.
            if(catchingUp&&(Math.abs(error)<0.015||now-lastSeek>TimeUnit.SECONDS.toNanos(2)))catchingUp=false;
            double limit=catchingUp?0.15:0.03,gain=catchingUp?4.0:0.20;
            double speed = Math.max(1-limit, Math.min(1+limit, 1.0 - error * gain));
            if (Math.abs(speed - appliedSpeed) > 0.003) { connection.set("speed", speed); appliedSpeed = speed; }
        }
        if (play != wasPlaying) connection.set("pause", !play);
        if (outside) {
            String message = desired < 0 ? "视频尚未开始" : "视频已结束";
            if (!status.equals(message)) connection.request("show-text", message, 86_400_000);
            status = message;
        } else {
            if (status.equals("视频尚未开始") || status.equals("视频已结束")) connection.request("show-text", "", 1);
            status = play ? "正在跟随 Bitwig 播放" : "已定位到 Bitwig 时间位置";
        }
        timeStatus = String.format(Locale.ROOT, "视频 %8.3f / %.3f s", target, duration);
        if (traceRows < 100_000 && (!event.equals("follow") || (play && now - lastLog > TimeUnit.MILLISECONDS.toNanos(250)))) {
            trace.write(String.format(Locale.ROOT, "%s,%.6f,%.6f,%.6f,%.6f,%s,%s%n", Instant.now(), s.seconds(), target, actual, error, play, event));
            lastLog = now; traceRows++;
        }
        if (now - lastFlush > TimeUnit.SECONDS.toNanos(1)) { trace.flush(); lastFlush = now; }
        wasPlaying = play; prior = s;
    }
    private void suspend() {
        if (connection != null && wasPlaying) {
            connection.set("pause", true);lastDesired=Double.NaN;
        }
        wasPlaying = false; prior = null; seekPending = false; catchingUp=false; scrub.reset();
    }
    private void disconnect() {
        if (connection != null) { connection.close(); connection = null; }
        loaded = ""; loadedDocument = null; prior = null; lastDesired = Double.NaN; wasPlaying = false; seekPending = false; catchingUp=false;
        appliedSpeed = 1;
        scrub.reset();
        frameRate=0;
    }
    public void close() {
        wanted = false; worker.shutdownNow(); probeWorker.shutdownNow(); disconnect();
        try { if (trace != null) trace.close(); } catch (IOException ignored) {}
    }
}
