# GitHub and Launcher release validation

Date: 2026-11-16. MOD `0.5.0-dev.3`, Windows x64 / exact Bitwig Studio `6.1.7`, Patchbay `0.36.0-dev.14`, Launcher `0.9.2`.

## Scope

This release adds the public repository, MIT license (Amo Nova), contributor credits, English maintenance documentation, dependency metadata, a README resource, Launcher ZIP packaging and verified first-use player setup.

The stable MOD ID remains `video-preview`. The public ZIP contains the MOD and source/notices, with no third-party player binaries. `PlayerRuntime` downloads the same pinned decoder used in the preceding video tests. Existing media/probe/preview workers resolve that runtime before use; no download, extraction or hashing runs on the host event thread. The preview rechecks the current document and clip after setup so it does not continue with stale first-use state. Editing, source mapping, tempo rules and seek algorithms are otherwise unchanged.

The separate persistent EXE is not part of this public prerelease. Its earlier local build and payload check predate this first-use downloader; they are not presented as a new EXE acceptance pass. The production integrated installation was not changed.

## Build and archive

- Source metadata, JSON and Python syntax checks passed.
- Modkit compiled the MOD and finished 10 hooks, 5 non-public accesses and 18 rewritten helper classes.
- The actual public MOD-only ZIP was used for Patchbay preparation against an isolated original host: 4 mods, 64 transforms, 49 replacement classes. The original host JAR was read and hashed, not modified.
- Package validation checked source/built metadata, all declared resources, source freshness, licenses, archive paths, CRCs, helper classes and the absence of bundled player/host classes.
- Release asset: `video-preview-0.5.0-dev.3.zip`, 214,002 bytes, 39 files.
- SHA-256: `90380554113fd100c845830549e04e17b69a8dbd5d2d29b183a8b5d898871125`.

## Player setup and recovery

`tools/test_player_runtime.py --online` compiled the actual implementation with `PlayerRuntimeTest`. Passed:

- Empty-cache setup and progress, reuse by a new provider with all network access replaced by a failing test callback, and reuse of an existing verified bundled player.
- A single download across concurrent callers and across three JVM processes.
- Interrupted download, incorrect archive SHA-256, incorrect extracted-file hashes and cancellation without activating incomplete files.
- A waiting caller cancelling without cancelling the owner's download.
- Corrupt-cache repair, retention of the previous cache when replacement fails, and preservation of unrelated data and another cached version.
- A real Java HTTPS download of the pinned 34,181,485-byte upstream archive, archive hash verification, Windows tar extraction of only the three expected files, individual hash verification and offline reuse. This run completed in 4.294 seconds; it is one network observation, not a download-speed guarantee.

The implementation uses `java.base`, present in Bitwig's reduced Java runtime; it does not require `java.net.http` or PowerShell. TLS validation and JVM-wide proxy settings are not changed. A native failed-setup observation also confirmed zero inserted clips and an unchanged project modification flag. Downloading occurs before clip insertion and media collection.

## Actual Launcher code

`tools/test_launcher.py` compiled the seven unmodified Java sources from official Launcher tag `v0.9.2` with `tests/LauncherPackageTest.java`, recording upstream source hashes. The test used the final public candidate and actual pinned framework packages, without UI or network access.

Passed: prerelease catalog discovery, draft exclusion, metadata, archive acceptance/install, automatic dependency installation, version update, interrupted transfer, checksum failure, unsafe/truncated ZIP rejection, staging/download cleanup and preservation of external settings/media sentinels.

The previous-version update fixture contains metadata only; it tests the installer transition rather than native migration from an older executable.

## Native host acceptance

The isolated original host loaded the MOD-only package through Patchbay's launch script with no player cache. Importing two synthetic videos (one with stereo AAC, one silent) downloaded and verified the player automatically and created the expected native clips.

Passed: decoder switching between assets, fractional-second seeks, native split and source continuity, undo, move/undo, absolute-time preservation after changing tempo to 140 BPM, a picture marker, playback, left preview placement, project-local video/audio collection, save and normal exit.

The saved project was then reopened in a fresh host process using the cached player. Both asset identities, source offsets, audio binding, marker state and left placement were restored. Both videos decoded at the expected positions; split/undo and saving after reopen passed, followed by normal exit.

With the host closed, actual Patchbay preparation excluded the MOD when it was deselected and included it again when selected. The actual Launcher installer then replaced the installed package with the release ZIP. Hashes of 13 saved settings, synthetic project/media and cached-player files remained unchanged. A fresh host loaded that reinstalled package and repeated both-video locate and split/undo/save successfully. This checks selection/preparation and recovery; it does not claim editing or saving the project in a running host with the MOD absent.

Across the create/reopen samples, decoded seek position differed from the requested source time by at most 16.667 ms. One sampled software playback-clock difference was 15.992 ms. These are smoke-test observations, not a latency benchmark or physical audio/display calibration. Imports exercised the native drop handler through a test API; this release round did not repeat physical mouse drag/drop or the full historical 4K/long-GOP suite.

Raw projects, logs and machine-specific paths are kept outside the public repository. Historical codec, large-file and editing evidence remains in the other versioned validation documents; it is not represented as a fresh run here.

## Publication boundary

This prerelease publishes the MOD ZIP and checksum, not a player-bearing EXE. First use on each computer requires GitHub access and Windows `tar.exe`; a complete verified cache supports subsequent offline use. No macOS or Bitwig 6.1.0 compatibility is claimed. See [RELEASING.md](RELEASING.md) for installation, update and switching behavior.
