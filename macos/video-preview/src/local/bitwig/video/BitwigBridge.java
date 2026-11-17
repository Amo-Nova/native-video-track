package local.bitwig.video;

import bitwig.obf.OnD;
import bitwig.obf.drf;
import bitwig.obf.twN;
import bitwig.obf.xm;
import bitwig.obf.ExQ;
import com.bitwig.flt.app.Hvv;
import com.bitwig.flt.document.core.iface.bMt;
import bml.api.Bml;
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
        Runtime.getRuntime().addShutdownHook(new Thread(() -> { engine.close(); dock.close(); }, "Video-preview-exit"));
        MOD.log("Started video-preview 0.5.0-dev.14 for macOS 6.1.7 (arm64)");
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
            VideoLayout.refresh(dock, !playback.file().isEmpty() && playback.enabled());
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

    static void showPreview() { start(); sample(); PreviewPlacement.show(true); }
    /** One action to bind a single shortcut (e.g. Alt+V) to show/hide the preview window. */
    static void togglePreview() { start(); if (PreviewPlacement.shown()) hidePreview(); else showPreview(); }
    static void hidePreview() { PreviewPlacement.show(false); }
    static void enablePreview(){start();engine.enable();}
    static void resetFloating(){if(engine!=null)engine.reposition();}
    static void error(Throwable e) {
        MOD.log("Video preview error", e);
    }
    private static void logFailure(Throwable e) {
        long now = System.nanoTime();
        if (now - lastLog > TimeUnit.SECONDS.toNanos(5)) { lastLog = now; MOD.log("Transport/settings read failed", e); }
    }
}
