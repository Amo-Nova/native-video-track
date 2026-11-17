# macOS edition and compatibility

Updated **2026-11-17**: Amo Nova supplied a working Apple Silicon port, MOD **0.5.0-dev.14**, with macOS testing and contributions from **[AUPhui](https://github.com/AUPhui)**. Source is under [macos/](macos/README.md); the [Mac prerelease](https://github.com/Amo-Nova/native-video-track/releases/tag/v0.5.0-dev.14-macos) is installed manually through Patchbay **Mods → Add Mod…**.

This supersedes the earlier investigation-only status. The repository import preserves the supplied runtime payload and checks its contents on Windows; it is not a new Mac runtime test. See [validation and provenance](macos/VALIDATION.md).

## Platform differences

| Behavior | Windows 0.5.0-dev.3 | Mac 0.5.0-dev.14 |
|---|---|---|
| Target | Windows x64 / exact Bitwig 6.1.7 | Apple Silicon / exact Mac Bitwig 6.1.7 |
| Delivery | Automatic Launcher catalog | Manual Patchbay Add Mod import |
| Player | Pinned Windows build downloaded and verified on first use | Separately installed mpv, normally Homebrew |
| Preview | Top, bottom, left, right or floating | Floating mpv window, always on top |
| Import opens preview | Yes | Off by default; use Video action |
| Native AV import and absolute time | Present | Present in supplied implementation |
| Control/marker/AV-detach interfaces | Present | Removed from supplied edition |
| IPC | Windows named pipes | Java Unix-domain sockets |

The Mac manifest's `universal` architecture describes the shared host Java archive; it does not establish Intel execution. Its original host JAR SHA-256 is `6024752847797eb41e6fbc91e081211937931cc452a695ab78226321cefdac8d`, different from the Windows hash.

## Remaining work

Record exact macOS, hardware and mpv versions for reproducible runtime checks. Independently verify new/save/Save As/close/reopen/continued editing, missing-media recovery, transferred Windows projects, varied codecs/frame rates, long GOP, 4K and approximately 3 GB files. Full Retina/multi-monitor behavior and physical audio/display timing also need their own evidence.

Docking, restored manual controls, Intel support, other Bitwig versions and automatic cross-platform Launcher selection are separate future work. The persistent Mac installer source is included for maintenance but was not built, installed or restored on this Windows host. See [Mac build instructions](macos/BUILDING.md) before using it.
