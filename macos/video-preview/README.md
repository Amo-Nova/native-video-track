# Native Video Track — macOS Apple Silicon

Contributed MOD **0.5.0-dev.14** for **Bitwig Studio 6.1.7**. Supplied by **Amo Nova**, with macOS testing and contributions from **[AUPhui](https://github.com/AUPhui)**, based on the project developed with OpenAI Codex. See `CREDITS.txt` in the release package and the repository's [Mac validation](https://github.com/Amo-Nova/native-video-track/blob/main/macos/VALIDATION.md).

## Install

1. Use a compatible macOS Patchbay/BML package with `maps/macos-6.1.7-map1`. The supplied build flow targets `0.36.0-dev.16-macos` and Actions and Menus framework `0.8.0-dev.1`.
2. Install mpv separately, normally with `brew install mpv`. The MOD does not bundle or automatically download a player.
3. Download `video-preview-0.5.0-dev.14-macos.zip` from the Mac release. In **Patchbay**, choose **Mods → Add Mod…**, select the ZIP, enable **Video: Timeline Preview** and its Actions and Menus framework, then load Bitwig. This is a manual import, not an automatic Launcher catalog update.

Alternatively, set `BITWIG_VIDEO_MPV` to a working mpv executable or supply one under the MOD's `player/` resource. Lookup order is the explicit environment override, `player/mpv`, `/opt/homebrew/bin/mpv`, `/usr/local/bin/mpv`, then PATH. Homebrew mpv depends on its installed libraries; copying that executable alone to another computer is insufficient.

Use an original compatible host for the Patchbay route. The separate persistent integration source is an alternative installation route; do not layer it onto the same already-modified host while using Patchbay.

## Working with video

Drag one or more videos into the arranger. Audio-bearing videos create native waveform-bearing AV clips; Bitwig plays their extracted WAV/RF64 audio and mpv remains muted. New imports use original-speed absolute project time, so BPM changes preserve second-based placement and duration. Native move, trim, split and undo use the source mapping carried by the clips.

The preview is **closed by default**, including after import. Click the **Video** button or use **Video Preview: Toggle Video Window** to show/hide it. **Show Video Window** and **Hide Video Window** are also available as actions. Hiding closes the preview process; closing mpv's window hides it without immediately restarting. Timeline gaps display black while the preview is visible.

The window is mpv's own floating, always-on-top window. Drag and resize it directly. It is not docked inside Bitwig and does not automatically follow the host window. Saved geometry is applied on player startup; **Video: 恢复默认窗口** resets it and restarts the player.

Project media collection uses `Video/` and `samples/Video/`. Save the project and copy its whole directory when relocating it. Launch with the same MOD installed when reopening. Keep collected media intact; missing-media recovery and cross-platform transfer have not received a fresh independent acceptance run in this publication.

If mpv is missing or cannot start, inspect the MOD/session log, install or repair the player, and retry. Imports prepare media before inserting the completed clip. GUI-launched Mac applications may not inherit a terminal's PATH, so the resolver also checks standard Homebrew paths.

## Edition limits

This edition omits the Windows control/settings window, four-edge docking, marker-management UI, manual AV separation/binding, relink, timebase-switch and frame-step controls. Some UI/status strings remain Chinese. No video export, compositing or optional BPM-following video speed is provided.

Runtime testing was reported on Apple Silicon by the contributors. Exact macOS/player versions, Intel execution, cross-platform project interchange, full codec/large-file coverage and physical AV latency were not independently verified during the Windows-side import.

The release contains project-owned code/source under MIT and no Bitwig or third-party player binaries. Dependencies retain their own licenses; see `THIRD_PARTY.md`.
