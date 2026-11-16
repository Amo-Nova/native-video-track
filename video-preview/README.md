# Native Video Track

Version 0.5.0-dev.3 (packaging candidate), exact Windows x64 Bitwig Studio 6.1.7, Patchbay/BML 0.36.0-dev.14.

Use the root [build instructions](https://github.com/Amo-Nova/native-video-track/blob/main/BUILDING.md) to compile from source and [maintainer handoff](https://github.com/Amo-Nova/native-video-track/blob/main/HANDOFF.md) before modifying behavior. The current UI includes Chinese strings; this document describes their functions in English.

## Working with video

Drag videos into the native arranger or use the video import action. Audio-bearing media creates a native AV clip with a waveform; silent media creates a video carrier. Batch files are placed sequentially. Bitwig plays the extracted audio and mpv remains muted.

Move, trim, split, copy and undo using native editing. The source frame follows the project playhead within the active clip, not the selection alone. A timeline gap clears the preview. Detach audio/video creates separate independent objects; undo restores the original binding. Bound video cannot be silently replaced while retaining unrelated old sound.

New imports default to absolute seconds at original speed. Tempo changes preserve second-based boundaries and source offsets. Existing music-position clips retain their mode unless explicitly converted. Tempo-following video speed is not implemented.

Picture markers created through the panel default to locked project seconds and support naming, list navigation and previous/next jumps. They can switch to music position. Manually moving a video does not move project markers. Frame-step controls use nominal FPS; exact neighboring-frame navigation for arbitrary VFR is not implemented.

## Preview and persistence

The placement menu selects top, bottom, left, right or floating. Docked splitters and floating bounds are resizable; layout preferences are machine-local. Closing floating preview hides it. The Video/show-preview entry restores it, and reset layout returns to bottom.

Media is collected into project-local `Video/` and `samples/Video/` directories. Copy the complete project directory when moving between machines. Save As recollects media; partial missing-media failures do not block other assets, and retry/recovery uses asset identity rather than basename matching.

When installed through Patchbay Launcher, launch and reopen Bitwig through Patchbay. The separate persistent integration loads through ordinary Bitwig launch paths. Components are under `bin/bitwig-video`, settings/logs under `%LOCALAPPDATA%/BitwigVideo`. See the [release and switching guide](https://github.com/Amo-Nova/native-video-track/blob/main/RELEASING.md) for install/restore behavior. Do not stack both installation routes on a modified host JAR.

## Limits

Long-GOP exact seeks and stop-time repositioning can still take hundreds of milliseconds. Confirmed-frame reuse reduces some nearby scrubbing work but does not remove decoding cost. No proxy video or image cache is generated.

No video export, transitions, compositing, thumbnails or tempo-following speed are provided. Absolute-time audio supports a forward, non-looping single RAW event. Codec, timing and display claims are limited to the recorded tests; physical audio/display latency and full mixed-DPI visual stress remain unmeasured.

The source archive excludes media, projects, raw logs, host binaries and player binaries. Third-party details are in `package/THIRD_PARTY.md` and the root notices.
