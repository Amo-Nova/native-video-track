# macOS edition: dependencies and attribution

Project-owned implementation: Native Video Track, MIT license, copyright (c) 2026 Amo Nova. Original development was a collaboration with OpenAI Codex, an AI coding assistant. [AUPhui](https://github.com/AUPhui) contributed macOS testing and project work to the Apple Silicon edition supplied by Amo Nova. Polarity provided inspiration and the repository/Launcher blueprint; REAPER and Logic Pro are workflow references.

- **Bitwig Studio — Bitwig GmbH.** User-installed host and Java runtime. No host application, SDK, generated host replacement classes or runtime is included in this MOD.
- **[Patchbay/BML — shaboogen](https://github.com/shaboogen-dev/Patchbay).** Integration and build tooling. The supplied Mac build targets `0.36.0-dev.16-macos` and Actions and Menus `0.8.0-dev.1`, installed separately and governed by their upstream licenses.
- **[mpv](https://mpv.io/).** Separately installed video playback/media processing, normally from [Homebrew](https://formulae.brew.sh/formula/mpv). This MOD contains no mpv, FFmpeg, libplacebo or codec binaries. The exact licenses/features depend on the user's installed build; consult [mpv copyright](https://github.com/mpv-player/mpv/blob/master/Copyright) and that package's notices. The pinned Windows build is not the Mac runtime dependency.
- **[Gson](https://github.com/google/gson) and [JNA](https://github.com/java-native-access/jna).** Provided by the user's local Bitwig `libs.jar`, not redistributed in this MOD. The Mac player connection uses Java Unix-domain sockets.

The project MIT license does not relicense Bitwig or these dependencies. No Polarity Devices code, REAPER codec DLLs or Logic Pro code is included. This project is not affiliated with or endorsed by Bitwig GmbH.
