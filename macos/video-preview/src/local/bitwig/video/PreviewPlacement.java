package local.bitwig.video;

import bml.api.Settings;
import bitwig.obf.drf;
import java.util.Locale;

/** Machine-local display preferences. Never touches a project or its undo manager.
 * 0.5.0-dev.3: floating window only; the four docked placements were removed. */
final class PreviewPlacement {
    enum Position {
        FLOAT("独立窗口");
        final String label;
        Position(String label){this.label=label;}
        String key(){return name().toLowerCase(Locale.ROOT);}
        static Position parse(String value){return FLOAT;}
    }
    private static final Settings SETTINGS=BitwigBridge.MOD.settings();
    // preview.visible defaults to false: importing a video never opens the window by itself.
    // (The old key preview.shown, default true, is left behind and ignored.)
    private static volatile boolean shown=SETTINGS.getBoolean("preview.visible",false);
    static boolean shown(){return shown;}
    static Position position(){return Position.FLOAT;}
    /** Kept for older clients/settings: any placement request now shows the floating window. */
    static void select(String value){show(true);}
    static void show(boolean value){onUi(()->{
        shown=value;SETTINGS.putBoolean("preview.visible",value);
        if(value)BitwigBridge.enablePreview();
    });}
    static void toggle(){show(!shown);}
    /** Whether importing a video also opens the preview window (off by default). */
    static boolean autoShow(){return SETTINGS.getBoolean("preview.autoshow",false);}
    static void reset(){onUi(()->{
        for(String k:new String[]{"x","y","width","height"})SETTINGS.remove("preview.float."+k);
        BitwigBridge.resetFloating();show(true); // an explicit reset still shows the window
    });}
    static int floating(String key,int fallback){return SETTINGS.getInt("preview.float."+key,fallback);}
    /** macOS launch geometry for mpv's own preview window (mpv applies --geometry at start). */
    static String geometry(){
        int w=Math.max(352,Math.min(7680,floating("width",960))),h=Math.max(240,Math.min(4320,floating("height",540)));
        int x=Math.max(-1_000_000,Math.min(1_000_000,floating("x",100))),y=Math.max(-1_000_000,Math.min(1_000_000,floating("y",100)));
        return w+"x"+h+"+"+x+"+"+y;
    }
    static void floating(int x,int y,int width,int height){
        SETTINGS.putInt("preview.float.x",x);SETTINGS.putInt("preview.float.y",y);
        SETTINGS.putInt("preview.float.width",width);SETTINGS.putInt("preview.float.height",height);
    }
    static void onUi(Runnable action){drf.EO().asyncExec(()->{try{action.run();}catch(Throwable e){BitwigBridge.error(e);}});}
}
