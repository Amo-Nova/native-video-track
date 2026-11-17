package local.bitwig.video;

import bitwig.obf.*;
import java.util.*;

/** 0.5.0-dev.3: floating window only. This class registers each host window for the
 * floating preview and adds the bottom-bar Video toggle; the four docked edge
 * splitters and their native widgets were removed. */
final class VideoLayout {
    private static final Map<aCZ,Boolean> WINDOWS=new WeakHashMap<>();
    private static final Map<aCZ,dBz> BUTTONS=new WeakHashMap<>();

    /** Hook entry: no layout change. Registers the window so the floating preview
     * can find the current document's top-level window handle. */
    static dwF wrap(dwF original,aCZ window){
        WINDOWS.put(window,Boolean.TRUE);
        return original;
    }
    static dwF button(dwF original,aCZ window){
        dXL row=dXL.fpX();row.unz(0.5f);row.byf(4);
        if(original!=null)row.IvP(original);
        dBz button=dBz.wLf("Video");button.sCA(PreviewPlacement::toggle);row.IvP(button);BUTTONS.put(window,button);
        return row;
    }
    /** Feeds the preview visibility from the current document's main window. */
    static boolean refresh(DockHost dock,boolean haveVideo){
        for(aCZ window:new ArrayList<>(WINDOWS.keySet())){
            try{
                if(window==null||!window.vTn()||window.BEX()!=0||window.dcC().Anf()!=BitwigBridge.currentDocument())continue;
                // macOS: no host HWND is embedded; mpv owns the preview window, so only the
                // visibility state is carried. Geometry applies at player launch.
                dock.update(new DockHost.Bounds(0,0,0,0,0,haveVideo&&PreviewPlacement.shown(),true));
                return true;
            }catch(Throwable ignored){continue;}
        }
        dock.update(null);return false;
    }
}
