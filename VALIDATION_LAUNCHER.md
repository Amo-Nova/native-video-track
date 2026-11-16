# GitHub and Launcher packaging validation

Date: 2026-11-16. Candidate MOD `0.5.0-dev.3`, Windows x64 / exact Bitwig Studio `6.1.7`, Patchbay `0.36.0-dev.14`, Launcher `0.9.2`.

## Scope

This change introduces the public source repository, MIT license (Amo Nova), English maintenance documentation, dependency metadata, a README resource and a Launcher ZIP builder. Comparison with the preceding English r2 source snapshot confirms that all 22 video Java files preserve their behavior; the only Java differences are version labels in the startup log and control-window title.

The stable MOD ID remains `video-preview`. The Windows installer source version is `1.0.0-dev.3` so the new local payload is not confused with the existing `1.0.0-dev.2` installer. The production installed integration was not updated.

## Build evidence

- Source metadata, JSON and Python syntax checks passed.
- Modkit compiled the exact host SDK and finished 10 hooks, 5 non-public accesses and 18 rewritten helper classes.
- Patchbay preparation passed with 4 mods, 64 transforms and 49 replacement classes; the original host JAR was read and hashed, not modified.
- The integration package built with 196 overlay entries and 45 component files. The unsigned EXE built successfully and its embedded payload verified.
- Transport-state tests passed for pause, locate, loop and project switch.

## Actual Launcher code

`tools/test_launcher.py` compiled the seven unmodified Java sources from official Launcher tag `v0.9.2` with `tests/LauncherPackageTest.java`. It recorded upstream source hashes and ran without UI or network access.

Passed checks: current prerelease discovery, exclusion of drafts, display metadata, actual candidate archive acceptance/install, automatic installation of all three pinned real framework packages, version update, interrupted transfer, checksum failure, unsafe/truncated ZIP rejection, staging/download cleanup and preservation of external settings/media sentinels.

The previous-version update fixture contains metadata only. This tests the installer transition, not native project migration from an older executable. The candidate's local checksum is in `releases/launcher/`; binary artifacts and raw machine-specific logs are not committed.

## Native host smoke test

The isolated original host was started with the prepared external MOD through Patchbay's launch script. Two synthetic videos (one with stereo AAC, one silent) were dropped in a batch. Their native clips, audio binding and source mapping were checked.

Passed: switching the decoder between assets, fractional-second seeks, native split and source continuity, undo, move/undo, absolute-time preservation after changing tempo to 140 BPM, a picture marker, playback, left preview placement, project-local media collection, save and normal exit. Sampled seek results differed from the requested source time by at most 16.667 ms; one sampled software playback-clock difference was 25.9 ms. These are smoke-test observations, not a latency benchmark or physical audio/display calibration.

Cold reopen was **not completed** in this release round: an existing non-fixture project became active after restart, so native automation was stopped. Its content was not edited or automatically closed. The earlier release's historical reopen evidence remains in the corresponding validation documents; it is not substituted for this candidate's missing check.

## Publication status

The source repository is ready for collaboration. The local MOD ZIP and EXE are **not public releases**. Remaining binary-release gates are complete third-party player redistribution materials and the unfinished native reopen/update/recovery acceptance described in [RELEASING.md](RELEASING.md). No macOS or Bitwig 6.1.0 compatibility is claimed.
