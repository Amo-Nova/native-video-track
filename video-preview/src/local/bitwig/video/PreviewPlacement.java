package local.bitwig.video;

import bml.api.Settings;
import bitwig.obf.drf;
import java.util.Locale;

/** Machine-local display preferences. Never touches a project or its undo manager. */
final class PreviewPlacement {
    enum Position {
        TOP("上方"), BOTTOM("下方"), LEFT("左侧"), RIGHT("右侧"), FLOAT("独立窗口");
        final String label;
        Position(String label){this.label=label;}
        String key(){return name().toLowerCase(Locale.ROOT);}
        static Position parse(String value){try{return valueOf(value.toUpperCase(Locale.ROOT));}catch(Exception ignored){return BOTTOM;}}
    }
    private static final Settings SETTINGS=BitwigBridge.MOD.settings();
    private static volatile Position position=Position.parse(SETTINGS.get("preview.position","bottom"));
    private static volatile boolean shown=SETTINGS.getBoolean("preview.shown",true);
    private static volatile long revision;
    static Position position(){return position;}
    static boolean shown(){return shown;}
    static long revision(){return revision;}
    static void select(String value){onUi(()->{
        position=Position.parse(value);shown=true;revision++;
        SETTINGS.put("preview.position",position.key());SETTINGS.putBoolean("preview.shown",true);
        VideoLayout.refresh();BitwigBridge.enablePreview();
    });}
    static void show(boolean value){onUi(()->{
        shown=value;revision++;SETTINGS.putBoolean("preview.shown",value);VideoLayout.refresh();
        if(value)BitwigBridge.enablePreview();
    });}
    static void toggle(){show(!shown);}
    static void reset(){onUi(()->{
        for(Position p:Position.values())SETTINGS.remove("preview.size."+p.key());
        for(String k:new String[]{"x","y","width","height"})SETTINGS.remove("preview.float."+k);
        BitwigBridge.resetFloating();
        select("bottom");
    });}
    static int size(Position p){return Math.max(p==Position.LEFT||p==Position.RIGHT?280:200,Math.min(1600,SETTINGS.getInt("preview.size."+p.key(),p==Position.LEFT||p==Position.RIGHT?420:320)));}
    static void size(Position p,double value){if(Double.isFinite(value)&&value>=160&&value<=4000)SETTINGS.putInt("preview.size."+p.key(),(int)Math.round(value));}
    static int floating(String key,int fallback){return SETTINGS.getInt("preview.float."+key,fallback);}
    static void floating(int x,int y,int width,int height){
        SETTINGS.putInt("preview.float.x",x);SETTINGS.putInt("preview.float.y",y);
        SETTINGS.putInt("preview.float.width",width);SETTINGS.putInt("preview.float.height",height);
    }
    static void onUi(Runnable action){drf.EO().asyncExec(()->{try{action.run();}catch(Throwable e){BitwigBridge.error(e);}});}
}
