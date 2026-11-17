# Native Video Track

Unofficial video integration for **Bitwig Studio 6.1.7**, with separate Windows x64 and macOS Apple Silicon editions.

Created collaboratively by **Amo Nova** and **OpenAI Codex**, an AI coding assistant, with macOS testing and contributions from **[AUPhui](https://github.com/AUPhui)**. This repository includes the video MODs, integration and installer sources, build tools and tests. It is not affiliated with or endorsed by Bitwig GmbH.

## Project contributors

**Amo Nova — creator and maintainer.** Product direction, music-to-picture workflow requirements, feature decisions, hands-on Bitwig testing and feedback, and community coordination.

**[AUPhui](https://github.com/AUPhui) — macOS testing and contributions.** Helped test and contribute to the Apple Silicon edition supplied by Amo Nova. The imported Mac implementation and its tested runtime payload are preserved; see the [Mac handoff](macos/README.md).

**OpenAI Codex — AI co-contributor.** Architecture and implementation, debugging and performance work, automated validation, build and installer tooling, and English source and release documentation. This work includes video import and timeline editing, audio/video synchronization, absolute-time mapping, picture markers, preview docking, project media collection and recovery, persistent Windows integration, and Patchbay Launcher packaging.

The project was developed through that collaboration, using the third-party foundations credited in [CREDITS.txt](CREDITS.txt) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). Project-owned source is released by Amo Nova under the [MIT License](LICENSE).

| Component | Version |
|---|---|
| Video MOD / Launcher prerelease | 0.5.0-dev.3 |
| macOS Apple Silicon MOD / manual import | 0.5.0-dev.14 |
| Persistent integration | 1.0.0-dev.1 |
| Windows EXE installer source | 1.0.0-dev.3 |
| Patchbay/BML baseline | 0.36.0-dev.14 |

## Installation status

| Platform | Download and installation | Player / preview |
|---|---|---|
| Windows x64, Bitwig 6.1.7 | [Windows prerelease](https://github.com/Amo-Nova/native-video-track/releases/tag/v0.5.0-dev.3); add `Amo-Nova/native-video-track` in Patchbay Launcher | Verified first-use mpv download; docked or floating preview |
| macOS Apple Silicon, Bitwig 6.1.7 | [Mac prerelease](https://github.com/Amo-Nova/native-video-track/releases/tag/v0.5.0-dev.14-macos); download the `-macos.zip` MOD, then **Mods → Add Mod…** in Patchbay | Install mpv separately; floating, always-on-top preview; [Mac instructions](macos/video-preview/README.md) |

The two editions have different feature sets and independent version numbers. The Mac ZIP is a manual Patchbay import; it is not advertised as a Windows Launcher update. Mac runtime testing was supplied by Amo Nova and AUPhui; the repository import was checked on Windows, not rerun on a Mac here. See [Mac validation](macos/VALIDATION.md).

The following installation and build details refer to the Windows edition. Mac maintainers should use [macos/BUILDING.md](macos/BUILDING.md).

Install the built `video-preview-0.5.0-dev.3.zip` from [GitHub Releases](https://github.com/Amo-Nova/native-video-track/releases). In Patchbay Launcher, add `Amo-Nova/native-video-track` as a Mods source and select Native Video Track and its dependencies. Do not use GitHub's automatic source ZIP as an installable MOD.

The MOD ZIP contains no third-party player binaries. First video use downloads the pinned mpv build directly from its upstream GitHub Release in the background, checks SHA-256 and caches it under `%LOCALAPPDATA%/BitwigVideo/player/`. First use requires Internet access and Windows `tar.exe`; later use works offline. Download failure leaves existing clips unchanged and can be retried. See [validation](VALIDATION_LAUNCHER.md).

The Launcher route uses source `Amo-Nova/native-video-track` and stable MOD ID `video-preview`. The persistent EXE remains a separate route for ordinary Bitwig shortcuts. **Do not stack them on the same modified host installation.** See [installation and switching](RELEASING.md#persistent-exe-route-and-switching).

## Build from source

1. Read [BUILDING.md](BUILDING.md), prepare the pinned external dependencies and configure `build.config.local.json`.
2. Run `python tools/build_source.py check`, then `python tools/build_source.py all`.
3. Read [ARCHITECTURE.md](ARCHITECTURE.md) for code ownership boundaries and [HANDOFF.md](HANDOFF.md) for maintenance constraints and remaining work.
4. Use a disposable, compatible Bitwig installation for runtime tests; see [tests/README.md](tests/README.md).

The preceding 0.5.0-dev.2 source snapshot was extracted into a fresh directory and successfully built into a MOD, prepared integration and EXE. Transport-state tests and EXE payload verification passed. See [SOURCE_BUILD_VALIDATION.md](SOURCE_BUILD_VALIDATION.md). The build command does not install or launch Bitwig.

## Current Windows behavior

- Drag one or more videos into the native arranger. Each import keeps its drop position; files in the same batch are placed sequentially.
- Videos with audio become native waveform-bearing AV clips. Audio plays through Bitwig; mpv provides a muted video preview.
- Native move, trim, split and undo operations preserve the video source mapping. Detaching AV creates independently editable audio and video objects.
- New imports use absolute project time at original speed. Tempo changes preserve their second-based boundaries and source offsets. Optional tempo-following video speed is not implemented.
- Picture markers can be locked to project seconds. Moving a video manually does not move these project markers.
- Preview placement supports top, bottom, left, right and a floating window. Layout preferences are local to the machine.
- Media collection supports saving, Save As, moving the complete project directory, multiple assets and partial missing-media recovery.
- Persistent installation enables ordinary Bitwig launch paths, with backup, repair and restoration tools.

## Source layout

| Path | Purpose |
|---|---|
| `video-preview/src/` | 23 Java files: timeline, media, synchronization, markers, rendering, controls and player setup |
| `video-preview/package/` | Menu resources, icon and third-party notice |
| `macos/` | Imported Apple Silicon 0.5.0-dev.14 source, build tools, credits and validation scope |
| `integration/src/` | Persistent runtime bootstrap |
| `integration/*.ps1`, `*.cmd` | Installation, status, backup, repair, restore and file association |
| `integration/exe/` | C# WinForms installer and packaging/test tools |
| `tools/build_source.py` | Configurable build-only entry point |
| `tests/`, `integration/tests/` | Test sources, synthetic-media generators and historical acceptance drivers |
| `DESIGN*.md`, `VALIDATION*.md` | Technical design and stage-specific validation summaries |
| `MAC_COMPATIBILITY.md` | Mac edition status, feature differences and remaining compatibility work |
| `dependencies.lock.json` | Hashes of pinned external build inputs |
| `tools/package_launcher.py` | Build and validate the Launcher MOD ZIP from local build output |
| `RELEASING.md` | Launcher contract, installation routes and binary release gates |

## Support and delivery boundaries

The Windows and Mac editions target their own exact Bitwig 6.1.7 builds and maps. Bitwig 6.1.0, Intel Mac execution and cross-platform project interchange are not validated by this publication. Long-GOP seeks can still take hundreds of milliseconds; software clock measurements are not physical audio/display latency measurements.

Repository documentation is in English. Each edition's runtime behavior is preserved from its respective source; applications and installers still contain Chinese UI strings. Runtime localization is a separate, unimplemented change.

No Bitwig binaries, generated host replacement classes, mpv binaries, user projects, media, raw logs, local configuration or credentials are included. Historical evidence paths identify development records; their raw artifacts are not bundled. Prepare dependencies using [DEPENDENCIES.md](DEPENDENCIES.md).

Credits and dependency roles are in [CREDITS.txt](CREDITS.txt) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). Project-owned source is [MIT licensed](LICENSE), copyright (c) 2026 Amo Nova. Third-party components retain their original licenses; see [license scope](LICENSE_STATUS.md).
