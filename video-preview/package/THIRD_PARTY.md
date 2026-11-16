# Third-party media and modding components

- Patchbay 0.36.0-dev.14: https://github.com/shaboogen-dev/Patchbay/releases
  The local vendor folder retains the original release package and framework source.
- mpv Windows x86_64 build 20261111, mpv v0.42.0-38-gbcde6b93c.
  Listed by the mpv project's installation page: https://mpv.io/installation/
  Build release: https://github.com/shinchiro/mpv-winbuild-cmake/releases/tag/20261111
  Original archive: `mpv-x86_64-20261111-git-bcde6b93cc.7z`
  SHA-256: `f259b5fd4b5c395617c948a4fadcdf55bba1c5282fb7c13e88626b3f71072baf`
  mpv source: https://github.com/mpv-player/mpv/tree/bcde6b93cc
  Build recipes: https://github.com/shinchiro/mpv-winbuild-cmake
  mpv copyright / licensing: https://github.com/mpv-player/mpv/blob/bcde6b93cc/Copyright
  Includes FFmpeg N-128766-gcd4e96eb3 and libplacebo v7.391.0; original build output is recorded in validation.
- Gson and JNA are loaded from the user's existing Bitwig `libs.jar`. That jar is not copied into the MOD.
- The installed Bitwig runtime supplies Java for the control window. No Bitwig application code or runtime is included in the MOD folder.

Project-owned implementation source is MIT licensed, copyright (c) 2026 Amo Nova. Third-party components retain their own licenses. The public Launcher MOD ZIP contains no third-party player binaries. First use downloads the pinned archive directly from the upstream release above and verifies its SHA-256 plus individual file hashes before use. Only the three runtime files are extracted; no upstream installer/updater scripts run. See `player/README.txt` for cache and retry behavior, and the repository RELEASING.md for the separate locally bundled EXE release boundary.
