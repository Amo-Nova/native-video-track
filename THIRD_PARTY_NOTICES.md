# Attribution and technology sources

Bitwig Studio (Bitwig GmbH) is the host. shaboogen's Patchbay/BML supplies internal-class integration, mod tooling and frameworks. mpv provides video playback and media processing; the tested Windows build is from shinchiro and includes FFmpeg and libplacebo. JNA and Gson are supplied by the locally installed Bitwig `libs.jar`.

- [Patchbay/BML](https://github.com/shaboogen-dev/Patchbay)
- [mpv](https://mpv.io/) and [shinchiro Windows builds](https://github.com/shinchiro/mpv-winbuild-cmake)
- [FFmpeg](https://ffmpeg.org/) and [libplacebo](https://code.videolan.org/videolan/libplacebo)
- [JNA](https://github.com/java-native-access/jna) and [Gson](https://github.com/google/gson)

The project was developed collaboratively by Amo Nova (creator and maintainer) and OpenAI Codex (AI coding assistant and co-contributor). Project-specific work covers video workflow, synchronization, media management, docking, persistent installation, testing, packaging and documentation. Polarity provided inspiration, introduction and the repository/Launcher packaging blueprint. REAPER and Logic Pro are workflow references. No Polarity Devices code, REAPER code/codec DLLs or Logic Pro code is included in this snapshot. Full role descriptions are in `CREDITS.txt`.

`licenses/Patchbay-LICENSE.txt` preserves shaboogen's MIT license. `licenses/Patchbay-UPSTREAM-NOTICES.md` is the unchanged upstream notice file; it mentions upstream modules that this project may not use. The actual dependency list is in `DEPENDENCIES.md`.

This source repository contains neither the host nor third-party binaries. Project-owned source is MIT licensed under `LICENSE`; each dependency retains its own licensing and notices. The project license does not grant rights to the Bitwig application or relicense media dependencies. See `LICENSE_STATUS.md` and `RELEASING.md` for the binary release boundary.
