# Native Video Track for macOS

Contributed Apple Silicon edition **0.5.0-dev.14**, targeting **Bitwig Studio 6.1.7**. Supplied by **Amo Nova**, with macOS testing and contributions from **[AUPhui](https://github.com/AUPhui)**. The original project was developed collaboratively with **OpenAI Codex**, an AI coding assistant. See [CREDITS.txt](CREDITS.txt) for roles and foundations.

This is a separate platform edition. Its version number does not indicate a replacement for the Windows `0.5.0-dev.3` release. The supplied port describes its starting point as Windows `0.5.0-dev.8`; that intermediate Windows snapshot is not substituted for the repository's Windows implementation.

## Install and use

Download the MOD ZIP from the [Mac prerelease](https://github.com/Amo-Nova/native-video-track/releases/tag/v0.5.0-dev.14-macos), install mpv separately, and use **Mods → Add Mod…** in Patchbay. Read [the Mac user guide](video-preview/README.md). This asset is deliberately not an automatic Launcher catalog update for the shared `video-preview` ID.

The preview uses mpv's own floating, always-on-top window. Importing video does not open it by default; use the Video button to show it. This edition retains drag import, native AV audio, absolute-time editing and project media collection, but omits the Windows docking/control/marker-management and AV-detach interfaces. See [platform differences](../MAC_COMPATIBILITY.md).

## Source layout

| Path | Purpose |
|---|---|
| `video-preview/src/` | The 15 supplied runtime Java files, preserved byte-for-byte |
| `video-preview/mod.json`, `build.json`, `package/` | Source manifest, build settings and package resources |
| `build_mac.py` | Mac compile, Modkit finish and ZIP packaging; publication additions include licenses, credits and complete checksums |
| `persistent/` | Supplied app-bundle integration and restoration source; not a distributed installer payload |
| `compose_remap.py` | Development mapping composition for Windows/Mac 6.1.7 |
| `SOURCE_MANIFEST.json` | Hashes of the current published source tree |
| `IMPORT_PROVENANCE.json` | Supplied archive hashes and preserved code/payload hashes |
| `VALIDATION.md` | Contributor-reported Mac testing versus checks performed during repository import |

Build on a Mac using [BUILDING.md](BUILDING.md). Host binaries, pristine backups, Patchbay binaries, mpv and user media are not included. Project-owned source remains [MIT licensed](LICENSE), copyright (c) 2026 Amo Nova; third-party components retain their own licenses.
