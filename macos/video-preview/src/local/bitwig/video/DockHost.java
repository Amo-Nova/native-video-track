package local.bitwig.video;

import java.util.Map;

/** macOS placement host. Windows reparented one stable render HWND between a native dock
 *  and an owned floating frame; macOS has no cross-process window embedding (mpv's --wid
 *  is X11/Win32/Android only), so the preview is mpv's own top-level window, launched at
 *  the saved floating geometry, and this class only carries the visibility state that
 *  drives the player connection's lifecycle. */
final class DockHost implements AutoCloseable {
    record Bounds(long parent, int x, int y, int width, int height, boolean visible, boolean floating) {}
    private volatile Bounds bounds;
    private volatile boolean closed;

    DockHost() {}
    void update(Bounds value) { bounds = value; }
    /** Always 0 on macOS: nothing is embedded, mpv owns its window. */
    long handle() { return 0; }
    boolean visible() { Bounds b = bounds; return !closed && b != null && b.visible(); }
    /** The Windows context menu is native; its commands were removed with the controls window. */
    void positionMenu() {}
    /** Geometry is applied by relaunching the player at the saved settings; see
     *  BitwigBridge.resetFloating(). Nothing native to restore here. */
    void resetFloating() {}
    Map<String, Object> inspect() {
        Bounds b = bounds;
        return Map.of("platform", "macos", "bounds", b == null ? "hidden" : b.toString());
    }
    public void close() { closed = true; bounds = null; }
}
