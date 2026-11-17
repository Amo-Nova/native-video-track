package local.bitwig.video;

import bml.actions.ActionProvider;
import bml.actions.Actions;
import bml.actions.Menu;

public final class VideoActions implements ActionProvider {
    public void declare(Actions actions) {
        actions.category("video_preview", "Video Preview");
        actions.action("video_preview_toggle", "Video Preview: Toggle Video Window")
            .category("video_preview").in(Menu.PLAY)
            .onRun(context -> BitwigBridge.togglePreview());
        actions.action("video_preview_show", "Video Preview: Show Video Window")
            .category("video_preview").in(Menu.PLAY)
            .onRun(context -> BitwigBridge.showPreview());
        actions.action("video_preview_hide", "Video Preview: Hide Video Window")
            .category("video_preview").in(Menu.PLAY)
            .onRun(context -> BitwigBridge.hidePreview());
        actions.action("video_preview_reset_layout","Video: 恢复默认窗口").category("video_preview").in(Menu.PLAY).onRun(c -> PreviewPlacement.reset());
        BitwigBridge.start();
    }
}
