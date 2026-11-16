# Architecture

## Host and timeline

| Class | Responsibility |
|---|---|
| `VideoHooks`, `BitwigBridge` | Patchbay hooks and internal Bitwig access; bound to Windows 6.1.7 |
| `VideoTimeline` | Native carrier clips, drop routing, import queue, asset binding, source offsets, AV separation and recovery |
| `VideoConfig` | Versioned project metadata and settings |
| `MediaAssets` | Streaming media collection, audio extraction, relative paths and derived WAV/RF64 recovery |
| `AbsoluteTime` | Original-speed, second-based placement and tempo-edit adjustments |
| `TransportTime` | Selection of playback, stopped and relocated start positions; project switching |
| `VideoMarkers`, `VideoMarkerInfo` | Native cue markers and second-based picture-marker constraints |

The native clips, saved tempo map and dedicated metadata in `.bwproject` are the persistent source of truth. Collected media uses `Video/<asset-id>/...` and `samples/Video/...`. Asset identity is not inferred from filenames. Derived audio and rebuildable caches do not replace the source video.

Host model access runs on the host UI thread. File I/O, decoding and IPC run in background workers. Asynchronous results must still belong to the project and clip that initiated the request before they are committed.

## Time and audio

New clips default to absolute project time and original-speed media. Native tempo edits update the necessary clip geometry inside the native edit transaction, preserving second-based boundaries and source offsets. Clips are temporarily removed and reinserted as a batch to avoid intermediate overlap trimming; object identity and undo behavior are retained. Background sampling does not repeatedly rewrite the project.

Legacy music-position clips keep their existing behavior unless explicitly converted. Music position and tempo-following playback speed are different concepts; optional video speed-following is not implemented. Supported absolute-time audio is a forward, non-looping single RAW event.

Audio plays in Bitwig's native engine. mpv remains muted. The default AV clip shares native editing geometry; separation creates independent audio and video objects rather than a permanent pair of linked tracks.

## Player and rendering

`MpvEngine` consumes host seconds and the active clip's source mapping. `MpvConnection` controls a separate mpv process over Windows named pipes with overlapped I/O.

`ScrubCadence` and the connection coalesce high-frequency intermediate targets, wait for decode completion, record confirmed frame intervals and reuse or advance within those intervals when valid. Unknown intervals use exact seeking. A command acknowledgement is not a completed frame; the final requested target must be resolved. Cached entries are numeric time intervals, not stored images or proxy media.

`VideoLayout`, `VideoWidget`, `VideoToolbar` and `PreviewPlacement` manage host layout and placement selection. `DockHost` and `FloatingPreview` use JNA/Win32 for native child windows, clipping, DPI and floating-window lifetime. These are major macOS porting boundaries.

Docking occupies actual host workspace space. A single render window can move between docked and floating parents without restarting mpv. Old regions are invalidated, old pixels are not copied during resizing, and invalid/out-of-bounds rectangles are hidden.

## Controls

`VideoControls` is a separate Swing JVM. `UiLink` and `UiClient` communicate over a local loopback channel with a random per-launch handshake token. The Bitwig JVM does not initialize AWT. Chinese runtime UI strings remain in this snapshot; translating the delivery documents has not localized the application.

## Persistent integration and installer

Patchbay modkit prepares hooks and helper code from the exact locally supplied host. The integration builder produces a local overlay. Installation backs up and verifies the original JAR before publishing the combined result. `InstalledRuntime` resolves fixed component paths and writable per-user settings for ordinary Bitwig launch paths.

The PowerShell core handles exact-version checks, running-host checks, hashes, staging, backup, pending-transaction recovery, repair and restore. The C# WinForms EXE wraps that core and its payload. Project associations are completed in the initiating user's context, including when the elevated worker runs under another account.

Components reside in `bin/bitwig-video/`; user settings and logs are under `%LOCALAPPDATA%/BitwigVideo`. Updates and official repairs can replace integration files, so each new host version needs explicit compatibility work.

This is third-party integration with internal host classes, not an official Bitwig video API. Host binaries and generated replacement classes are not included in this source delivery.
