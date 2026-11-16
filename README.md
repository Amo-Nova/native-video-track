# Native Video Track

Unofficial video integration for **Bitwig Studio 6.1.7 on Windows x64**.

Created collaboratively by **Amo Nova** and **OpenAI Codex**, an AI coding assistant. This repository includes the video MOD, persistent integration, Windows installer, build tools and test sources. It is not affiliated with or endorsed by Bitwig GmbH.

## Project contributors

**Amo Nova — creator and maintainer.** Product direction, music-to-picture workflow requirements, feature decisions, hands-on Bitwig testing and feedback, and community coordination.

**OpenAI Codex — AI co-contributor.** Architecture and implementation, debugging and performance work, automated validation, build and installer tooling, and English source and release documentation. This work includes video import and timeline editing, audio/video synchronization, absolute-time mapping, picture markers, preview docking, project media collection and recovery, persistent Windows integration, and Patchbay Launcher packaging.

The project was developed through that collaboration, using the third-party foundations credited in [CREDITS.txt](CREDITS.txt) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). Project-owned source is released by Amo Nova under the [MIT License](LICENSE).

| Component | Version |
|---|---|
| Video MOD / Launcher packaging candidate | 0.5.0-dev.3 |
| Persistent integration | 1.0.0-dev.1 |
| Windows EXE installer source | 1.0.0-dev.3 |
| Patchbay/BML baseline | 0.36.0-dev.14 |

## Installation status

This is the source release. A public installable Launcher ZIP has not been published yet; its player redistribution materials and release acceptance are tracked in [RELEASING.md](RELEASING.md). Do not use GitHub's automatic source ZIP as an installable MOD.

The new source builds successfully, and its local candidate passes the actual Launcher 0.9.2 installer tests. See [current validation and remaining checks](VALIDATION_LAUNCHER.md).

The Launcher route uses source `Amo-Nova/native-video-track` and stable MOD ID `video-preview`. The persistent EXE remains a separate route for ordinary Bitwig shortcuts. **Do not stack them on the same modified host installation.** See [installation and switching](RELEASING.md#persistent-exe-route-and-switching).

## Build from source

1. Read [BUILDING.md](BUILDING.md), prepare the pinned external dependencies and configure `build.config.local.json`.
2. Run `python tools/build_source.py check`, then `python tools/build_source.py all`.
3. Read [ARCHITECTURE.md](ARCHITECTURE.md) for code ownership boundaries and [HANDOFF.md](HANDOFF.md) for maintenance constraints and remaining work.
4. Use a disposable, compatible Bitwig installation for runtime tests; see [tests/README.md](tests/README.md).

The preceding 0.5.0-dev.2 source snapshot was extracted into a fresh directory and successfully built into a MOD, prepared integration and EXE. Transport-state tests and EXE payload verification passed. See [SOURCE_BUILD_VALIDATION.md](SOURCE_BUILD_VALIDATION.md). The build command does not install or launch Bitwig.

## Current behavior

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
| `video-preview/src/` | 22 Java files: timeline, media, synchronization, markers, rendering and controls |
| `video-preview/package/` | Menu resources, icon and third-party notice |
| `integration/src/` | Persistent runtime bootstrap |
| `integration/*.ps1`, `*.cmd` | Installation, status, backup, repair, restore and file association |
| `integration/exe/` | C# WinForms installer and packaging/test tools |
| `tools/build_source.py` | Configurable build-only entry point |
| `tests/`, `integration/tests/` | Test sources, synthetic-media generators and historical acceptance drivers |
| `DESIGN*.md`, `VALIDATION*.md` | Technical design and stage-specific validation summaries |
| `MAC_COMPATIBILITY.md` | Apple Silicon porting investigation and outstanding requirements |
| `dependencies.lock.json` | Hashes of pinned external build inputs |
| `tools/package_launcher.py` | Build and validate the Launcher MOD ZIP from local build output |
| `RELEASING.md` | Launcher contract, installation routes and binary release gates |

## Support and delivery boundaries

Only the exact tested Windows 6.1.7 build is supported. Bitwig 6.1.0 and macOS have not been adapted or runtime-validated. Long-GOP seeks can still take hundreds of milliseconds; software clock measurements are not physical audio/display latency measurements.

Repository documentation is in English. Video editing and synchronization behavior is preserved from the verified source; the application and installer still contain Chinese UI strings. Runtime localization is a separate, unimplemented change.

No Bitwig binaries, generated host replacement classes, mpv binaries, user projects, media, raw logs, local configuration or credentials are included. Historical evidence paths identify development records; their raw artifacts are not bundled. Prepare dependencies using [DEPENDENCIES.md](DEPENDENCIES.md).

Credits and dependency roles are in [CREDITS.txt](CREDITS.txt) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). Project-owned source is [MIT licensed](LICENSE), copyright (c) 2026 Amo Nova. Third-party components retain their original licenses; see [license scope](LICENSE_STATUS.md).
