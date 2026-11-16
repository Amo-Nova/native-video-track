# Dependencies and build inputs

These are the pinned inputs used by the verified snapshot, not a recommendation to upgrade to the latest releases.

| Component | Version / source | Role |
|---|---|---|
| Bitwig Studio | Exact Windows x64 6.1.7; original hash in BUILDING.md | Host classes, bundled runtime and `libs.jar`; supplied locally |
| Patchbay/BML, shaboogen | [0.36.0-dev.14](https://github.com/shaboogen-dev/Patchbay/releases/tag/0.36.0-dev.14) | Modkit, maps, hooks, loader and frameworks |
| Actions and Menus | 0.8.0-dev.1, in the Patchbay package | Menu/action registration |
| Panels | 0.2.0-dev.2, in the same package | Enabled dependency; final video layout is implemented here |
| Settings | 1.0.0, in the same package | Enabled dependency |
| mpv | v0.42.0-38-gbcde6b93c | Playback and media processing |
| shinchiro Windows build | [20261111](https://github.com/shinchiro/mpv-winbuild-cmake/releases/tag/20261111), `mpv-x86_64-20261111-git-bcde6b93cc.7z` | Tested x64 player binaries |
| FFmpeg / libplacebo | mpv reports N-128766-gcd4e96eb3 / v7.391.0 | mpv media and rendering stack |
| JNA / Gson | Supplied by the matching Bitwig `libs.jar` | Native interfaces / JSON |
| Microsoft JDK | Tested with 25.0.2; Java 21 output | Compilation and preparation |
| Python | 3.11+; tested with 3.14 | Build and test drivers |
| .NET Framework / PowerShell | Windows-provided tools | Installer compilation and execution |

Patchbay archive `BML-0.36.0-dev.14-windows.zip`, SHA-256:

```text
0b79642fb2b8c9d1bd22d18fd7041bcb0db7e75062769f8fd38f88fe9a4e5160
```

mpv archive SHA-256:

```text
f259b5fd4b5c395617c948a4fadcdf55bba1c5282fb7c13e88626b3f71072baf
```

Extract dependencies to local directories and point `build.config.local.json` at them. The mpv root must contain `mpv.exe`, `mpv.com`, `d3dcompiler_43.dll` and the original `doc/` directory. The Patchbay root must include `loader/`, `modkit/`, `maps/`, `mods/` and `LICENSE`.

`dependencies.lock.json` pins important file hashes, including the matching Bitwig `libs.jar`. Verify versions when a hash differs; do not delete the guard to force a build. Dependency changes require their own validation.

Synthetic media tests also require FFmpeg/ffprobe CLI with the requested encoders. Those tools are not a separate product-runtime requirement. Preserve upstream license files and notices when obtaining dependencies.
