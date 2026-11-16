package local.bitwig.video;

import bml.actions.ActionProvider;
import bml.actions.Actions;
import bml.actions.Menu;

public final class VideoActions implements ActionProvider {
    public void declare(Actions actions) {
        actions.category("video_preview", "Video Preview");
        actions.action("video_preview_settings", "Video Preview: Open / Settings...")
            .category("video_preview").in(Menu.PLAY)
            .onRun(context -> BitwigBridge.showControls());
        actions.action("video_preview_show", "Video Preview: Show Video Window")
            .category("video_preview").in(Menu.PLAY)
            .onRun(context -> BitwigBridge.showPreview());
        actions.action("video_preview_hide", "Video Preview: Hide Video Window")
            .category("video_preview").in(Menu.PLAY)
            .onRun(context -> BitwigBridge.hidePreview());
        for(var position:PreviewPlacement.Position.values())actions.action("video_preview_position_"+position.key(),"Video: 预览位置 · "+position.label)
            .category("video_preview").in(Menu.PLAY).onRun(c -> PreviewPlacement.select(position.key()));
        actions.action("video_preview_reset_layout","Video: 恢复默认布局").category("video_preview").in(Menu.PLAY).onRun(c -> PreviewPlacement.reset());
        actions.action("video_marker_add", "Video: Add Project Marker Here").category("video_preview").in(Menu.PLAY).onRun(c -> VideoMarkers.add());
        actions.action("video_marker_previous", "Video: Previous Project Marker").category("video_preview").in(Menu.PLAY).onRun(c -> VideoMarkers.previous());
        actions.action("video_marker_next", "Video: Next Project Marker").category("video_preview").in(Menu.PLAY).onRun(c -> VideoMarkers.next());
        actions.action("video_audio_separate", "Video: Separate Audio and Video").category("video_preview").in(Menu.PLAY,Menu.selection("arranger_clip")).onRun(c -> VideoTimeline.separate());
        actions.action("video_audio_bind", "Video: Extract and Bind Audio").category("video_preview").in(Menu.PLAY,Menu.selection("arranger_clip")).onRun(c -> VideoTimeline.bindAudio());
        BitwigBridge.start();
    }
}
