# Technical design

Current snapshot: Video 0.5.0-dev.2, Integration 1.0.0-dev.1 and Windows Setup 1.0.0-dev.2. Supported host: the exact Windows x64 Bitwig Studio 6.1.7 build.

## End-to-end workflow

Install once, launch through the ordinary Bitwig icon or project file, create a project, drag videos into the arranger, edit native clips, add picture markers, save and collect media, close, reopen and continue editing.

The arranger drop position determines placement. Files within a single drop are sequential; independent requests are queued with their own second-based drop positions. Duplicate callbacks are suppressed. Failed probes do not leave incomplete clips, and delayed results are checked against the originating project before commit.

## Clip, time and media rules

- A video with audio becomes a single native waveform-bearing AV clip. Bitwig plays audio; mpv stays muted. Native movement, trimming, splitting and undo preserve source mapping.
- Detaching AV creates independently editable audio and silent-video objects. One undo restores the bound object. It is not a permanently linked two-track mode.
- New clips default to absolute seconds and original-speed playback. Tempo edits preserve their second-based start/end and source offset. Legacy clips keep music-position behavior unless explicitly converted.
- Optional tempo-following video speed is not implemented. A change in musical placement is distinct from changing media playback speed.
- Native cue markers created through the video panel default to locked project seconds and can switch to music position. Manual video movement does not carry these project markers.
- Native clips, saved tempo data and versioned project metadata are authoritative. Collected source video uses `Video/<asset-id>/`; derived audio uses `samples/Video/`. Same-named files remain separate assets.
- Saving, Save As and project-directory changes trigger background collection with streaming copies, hash checks and complete-file publication. Source video is not rewritten. Valid old copies remain usable while collection finishes.
- Reopening prefers project-relative references. One missing asset does not block others, and restored files can be retried. Missing originals cannot be reconstructed when both the source and collected copy are absent.

Absolute-time adjustments occur within native edit transactions. Background sampling does not repeatedly write the project. Time conversion uses native seconds and the full tempo map rather than the current BPM alone. Supported absolute-time audio is a forward, non-looping single RAW event.

## Player and interface

Host access runs on the UI thread; decoding, file work and IPC run in the background. mpv uses a separate process and overlapped Windows named-pipe I/O. Seek completion is based on decoded output, not command acknowledgement.

Intermediate scrubbing targets may be merged, but the final target must resolve exactly. Confirmed numeric frame intervals can be reused; unknown targets fall back to exact decoding. There is no image cache or proxy-video system. Full long-GOP decoding can still be slow.

Preview placement supports all four workspace edges and a floating window. Docking consumes actual workspace space. A shared native render child can move between docked and floating parents without restarting mpv. Clipping, old-region invalidation and suppression of copied old pixels protect the arranger.

Placement and sizes are machine-local preferences. They do not create clip edits or project undo entries. Closing the floating window hides the preview; it does not remove media. Settings run in a separate Swing JVM because the host JVM must not initialize AWT.

## Persistent installation

Installation combines locally prepared Patchbay class changes and helpers with the exact host JAR. Ordinary Bitwig launch paths then load the component without a special launcher. This is third-party integration, not an official native extension API.

The installer checks the exact original hash and that the target host is closed, backs up the original, verifies a staged result and uses pending records for interrupted publication. Unknown or upgraded hosts are not overwritten. A separate recovery tool works even if the host cannot launch.

Components live under `bin/bitwig-video/`; writable settings/logs live under `%LOCALAPPDATA%/BitwigVideo`. Restoration preserves projects, media and preferences. File associations run in the initiating user's context and preserve existing defaults and UserChoice. The host install path is currently restricted to ASCII because of the tested native launcher's behavior; project and media names are not similarly restricted.

The unsigned WinForms EXE wraps the same installer core and payload. The target machine does not need Python, a development JDK or a Patchbay development folder. Updates or official repairs may replace integration files; each new host version requires compatibility verification.

## Evidence and remaining platforms

See `VALIDATION_PERSISTENT_INSTALL.md` for ordinary launch and complete project lifecycle, `VALIDATION_EXE_INSTALLER.md` for installer coverage, and the other stage-specific reports for earlier feature checks. Historical pending items should be read alongside later results.

UI-call time, decoded-frame settling time, software AV clock error and physical output latency are different measurements. Existing tests do not establish universal codec support, mixed-DPI display reliability or zero-latency long-GOP seeking.

Bitwig 6.1.0 is not adapted. The planned Apple Silicon port needs platform IPC, native rendering, installation/recovery and real host validation; see `MAC_COMPATIBILITY.md`.
