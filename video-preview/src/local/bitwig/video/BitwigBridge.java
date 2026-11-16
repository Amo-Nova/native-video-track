package local.bitwig.video;

import bitwig.obf.OnD;
import bitwig.obf.drf;
import bitwig.obf.twN;
import bitwig.obf.xm;
import bitwig.obf.ExQ;
import com.bitwig.flt.app.Hvv;
import com.bitwig.flt.document.core.iface.bMt;
import bml.api.Bml;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class BitwigBridge {
    static final Bml.Mod MOD = Bml.mod("video-preview");
    private static final UUID STATE_ID = UUID.fromString("db319c63-e1f9-49a6-9ff4-d811ac2b3ae9");
    private static final String CATEGORY = "Video Preview";
    private static final AtomicBoolean POSTED = new AtomicBoolean();
    private static ScheduledExecutorService clock;
    private static MpvEngine engine;
    private static UiLink controls;
    private static DockHost dock;
    private static volatile Snapshot latest = new Snapshot(null, VideoConfig.EMPTY, VideoConfig.EMPTY, 0, false, 0);
    private static long lastLog;
    private static final TransportTime TIME = new TransportTime();

    public record Snapshot(bMt document, VideoConfig config, VideoConfig playback, double seconds, boolean playing, long nanos) {}

    static synchronized void start() {
        if (clock != null) return;
        dock = new DockHost();
        engine = new MpvEngine(MOD.resource("player"), MOD.resource("logs"), dock);
        clock = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Video-preview-clock"); t.setDaemon(true); return t;
        });
        clock.scheduleAtFixedRate(() -> {
            if (POSTED.compareAndSet(false, true)) {
                try { drf.EO().asyncExec(() -> { try { sample(); } finally { POSTED.set(false); } }); }
                catch (Throwable e) { POSTED.set(false); logFailure(e); }
            }
        }, 500, 25, TimeUnit.MILLISECONDS);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> { engine.close(); dock.close(); if (controls != null) controls.close(); }, "Video-preview-exit"));
        MOD.log("Started video-preview 0.5.0-dev.3 for Windows 6.1.7");
    }

    static bMt currentDocument() {
        var app = Hvv.mEL();
        if (app == null || app.cCV() == null || app.cCV().Hqm() == null) return null;
        return app.cCV().Hqm().byf();
    }

    private static void sample() {
        try {
            bMt document = currentDocument();
            VideoConfig config = document == null ? VideoConfig.EMPTY : read(document);
            double seconds = 0; boolean playing = false;
            if (document != null) {
                OnD transport = document.XzC();
                playing = transport.JrT().getBoolValue();
                // These are exactly the native targets of the official controller seconds API.
                seconds = TIME.update(document, transport.Fhl().getDoubleValue(), transport.XzC().getDoubleValue(), playing);
            }
            if (!Double.isFinite(seconds)) throw new IllegalStateException("无效的工程时间");
            VideoConfig playback=VideoTimeline.resolve(document,seconds,config);
            latest = new Snapshot(document, config, playback, seconds, playing, System.nanoTime());
            VideoMarkers.refresh(document);
            VideoWidget.refresh(dock, !playback.file().isEmpty() && playback.enabled());
            engine.accept(latest);
        } catch (Throwable e) {
            latest = new Snapshot(null, VideoConfig.EMPTY, VideoConfig.EMPTY, 0, false, System.nanoTime());
            engine.accept(latest); logFailure(e);
        }
    }

    private static VideoConfig read(bMt document) {
        for (Object obj : document.yy()) {
            if (obj instanceof twN state && STATE_ID.equals(state.byf())) {
                for (int i = 0; i < state.PJ3(); i++) {
                    xm category = state.beI(i);
                    if (CATEGORY.equals(category.beI())) {
                        if (category.BBg() == 0) return VideoConfig.EMPTY;
                        if (!(category.beI(0) instanceof ExQ value))
                            throw new IllegalStateException("工程的视频设置类型不匹配");
                        return VideoConfig.decode((String) value.beI().getValue());
                    }
                }
            }
        }
        return VideoConfig.EMPTY;
    }

    static Snapshot snapshot() { return latest; }
    static String playerStatus() { String setup=PlayerRuntime.status();return !setup.isBlank()?setup:engine == null ? "正在连接" : engine.status(); }
    static String playerTime() { return engine == null ? "" : engine.timeStatus(); }
    static double frameRate(){return engine==null?0:engine.frameRate();}
    static void probe(Path file,java.util.function.BiConsumer<Double,Throwable> done){start();engine.probeInfo(file,done);}

    static void commit(Snapshot origin, VideoConfig config) {
        drf.EO().asyncExec(() -> {
            try {
                if (origin.document() == null || currentDocument() != origin.document())
                    throw new IllegalStateException("当前工程已切换，请在目标工程中重新操作。");
                // Match DocumentStateProxy's native model, under our own permanent UUID.
                twN state = origin.document().beI("Video Preview", STATE_ID, 0, List.of(), false);
                xm category = state.MgQ(CATEGORY);
                ExQ setting = category.Gef("Association", 0, "", true, false);
                setting.beI().setValue(config.encode());
                MOD.log("Project video updated: " + config.file() + " offset=" + config.offset());
                sample();
                // Updating an offset must not tear down and recreate the decoder/window.
                engine.enable();
            } catch (Throwable e) { error(e); }
        });
    }

    static void choose(Snapshot origin, Path path) {
        engine.probe(path, failure -> {
            if (failure != null) { error(failure); return; }
            drf.EO().asyncExec(() -> {
                try {
                    if (currentDocument() != origin.document())
                        throw new IllegalStateException("选片期间工程已切换，请重新选择。");
                    VideoConfig current = read(origin.document());
                    commit(origin, new VideoConfig(1, path.toAbsolutePath().toString(), current.offset(), current.enabled()));
                } catch (Throwable e) { error(e); }
            });
        });
    }

    static void showControls() {
        start(); sample();
        if (controls == null) controls = new UiLink();
        controls.show();
    }
    static void showPreview() { start(); PreviewPlacement.show(true); }
    static void hidePreview() { PreviewPlacement.show(false); }
    static void enablePreview(){start();engine.enable();}
    static void positionMenu(){start();dock.positionMenu();}
    static void resetFloating(){if(dock!=null)dock.resetFloating();}
    static void error(Throwable e) {
        MOD.log("Video preview error", e);
        if (controls != null) controls.error(e.getMessage() == null ? e.toString() : e.getMessage());
    }
    private static void logFailure(Throwable e) {
        long now = System.nanoTime();
        if (now - lastLog > TimeUnit.SECONDS.toNanos(5)) { lastLog = now; MOD.log("Transport/settings read failed", e); }
    }
}
