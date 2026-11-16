# Apple Silicon porting notes

Status on 2026-11-16: dependency and source investigation only. There is **no runnable Mac package and no Mac runtime validation**. Target architecture is Apple Silicon; exact macOS and Bitwig versions still need to be selected.

The port should preserve multi-video editing, AV binding/separation, original-speed absolute time, picture markers, collected projects, four-edge docking and floating preview. Windows 6.1.7 is the current baseline. A Mac 6.1.7 candidate needs its own actual host JAR hash and map; Windows hashes are not transferable.

## Upstream findings recorded during investigation

- [Patchbay releases](https://github.com/shaboogen-dev/Patchbay/releases) list macOS 12+ Apple Silicon/Intel packages and Bitwig 6.1.7. The checked dev.14/dev.16 Mac notes described offline checks only; this does not establish runtime support for this video MOD. The Windows source remains pinned to dev.14.
- Patchbay's `MacPlatformOps.java` includes host discovery and identifies `Bitwig Studio.app/Contents/MacOS/BitwigStudio --launch` as the JVM child. That is a starting point, not proof that a modified app bundle launches correctly.
- [mpv JSON IPC](https://mpv.io/manual/stable/#json-ipc) uses Unix domain sockets on Unix and named pipes on Windows. Protocol-level synchronization can be shared, but connection, cancellation and lifetime handling need platform implementations.
- The documented [mpv `--wid` behavior](https://mpv.io/manual/stable/#options-wid) for other platforms does not prove that Windows cross-process HWND embedding works on macOS. Native views/rendering must be validated separately.
- [Apple signing and notarization documentation](https://developer.apple.com/documentation/security/notarizing-macos-software-before-distribution) is relevant to packaging. Signing an installer does not replace the host's signature; modified bundle resources, launch behavior and recovery require separate checks.

These are dated investigation findings, not a claim about newer upstream releases.

## Platform boundaries

| Source area | Windows dependency | Mac work |
|---|---|---|
| `MpvConnection.java` | `mpv.exe`, overlapped named-pipe I/O | arm64 player, Unix sockets, request ordering, cancellation and timeout parity |
| `DockHost.java`, `FloatingPreview.java` | user32, HWND, SetParent, Windows messages and DPI | Cocoa/native surface and window lifecycle; actual embedded space |
| `MediaAssets.java` | Windows executable path for extraction | Platform executable selection; audio offsets and WAV/RF64 validation |
| `UiLink.java` | External `javaw.exe` control process | Mac Java path and focus/close lifecycle; no AWT initialization in the host |
| `InstalledRuntime.java` | LOCALAPPDATA and Windows bin layout | Application Support and actual app-bundle paths; shared/read-only versus user-writable data |
| Build scripts | Windows maps, EXE and D3D files | Platform/architecture configuration and Mach-O dependency checks |
| Host hooks | Windows 6.1.7 mappings | Exact Mac map and runtime validation of drop, edit, layout, marker and serialization hooks |
| Project media | Relative paths and Windows fallbacks | Case-sensitive volumes, external volumes, Unicode/space paths and transferred projects |
| Installation/recovery | PowerShell, registry and NTFS publication | Mac transactions, app-exit detection, Finder/Dock/project entry points and independent recovery |

## Proposed implementation sequence

1. Establish an actual Apple Silicon test environment, exact host identity, original launch and signing state.
2. Separate player paths, IPC, preview surfaces, control-process launch and user directories from shared timeline/synchronization logic. Preserve the project format and run Windows regressions.
3. Validate synchronization with a standalone test preview, then implement real embedding. Floating-only validation does not complete the four-edge target.
4. Select the persistent-loading and app/pkg approach after validating host launch, signatures and restoration. Do not reuse a Windows overlay as a Mac payload.
5. Exercise installation, ordinary launch, new project, multi-import, fine edits, splits/undo, audio/video playback, markers, collection, close/reopen, continued editing and restore.

Bundle version detection, synthetic media and log collection into a coherent Mac test candidate. Do not count Windows offline checks as Mac runtime evidence.

## Required Mac checks

- arm64 host, JVM, player and native bridge; distinguish Rosetta from native execution.
- Frame-number/audio-pulse media for start/stop, loops, seeks, tempo automation and sustained playback.
- Real 4K, H.264/H.265/ProRes, varied frame rates, long GOP and approximately 3 GB media, including extraction and collection.
- Fine scrubbing, exact final targets, source-continuous splitting, AV separation and undo/redo.
- Retina, mixed scaling, external displays, full screen/Spaces, minimization, docking/floating changes and close/recovery.
- First save, Save As, whole-folder relocation, Windows-collected projects, missing assets and same-named videos.
- First/repeated install, missing-component repair, interruption, update refusal and restoration when the host cannot start.

Audio remains in Bitwig; the preview stays muted. Default BPM changes must not change media speed. Windows/Mac project interchange needs its own runtime validation.
