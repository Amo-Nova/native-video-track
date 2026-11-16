# Build the Windows source snapshot

## Prerequisites

- Windows x64; this entry point currently rejects other platforms.
- Python 3.11 or later. The verified build used Python 3.14.
- JDK 25. The verified build used Microsoft JDK 25.0.2 and emits Java 21 bytecode with `--release 21`.
- Windows PowerShell and the Windows .NET Framework C# compiler.
- The exact Patchbay and mpv inputs listed in [DEPENDENCIES.md](DEPENDENCIES.md).
- An **unmodified Windows Bitwig Studio 6.1.7** installation or private SDK copy. Preserve its matching `bin/` directory, including `cp/`, and its complete `jre/` directory. Patchbay deep verification uses the bundled `jre/bin/java.exe`; two JAR files alone are insufficient.

The original `bin/bitwig.jar` must have this SHA-256:

```text
a5b303466cf66a80e0fd7df00e49fb14eb41c23e59d30b300c0554b6769b3367
```

If persistent integration is already installed, the active JAR is modified and cannot serve as the original compiler input. Create a private SDK copy of the matching `bin/` and `jre/`, and replace **only the copied** `bin/bitwig.jar` with the verified original backup from `bin/bitwig-video/backups/<hash>.jar`. The SDK does not need the `bin/bitwig-video/` components or logs. Do not restore or modify a working installation for this build, and do not add Bitwig SDK files to the source archive.

## Configure

Copy `build.config.example.json` to `build.config.local.json` and set these paths:

```json
{
  "format": 1,
  "java_home": "C:/SDK/jdk-25",
  "bitwig_bin": "C:/SDK/Bitwig-6.1.7/bin",
  "patchbay_root": "vendor/Patchbay-0.36.0-dev.14/BML-0.36.0-dev.14-windows",
  "mpv_root": "vendor/mpv"
}
```

Relative paths are resolved against the configuration file's directory. The local configuration is ignored by Git and is not included in this archive. A different configuration can be passed with `--config <path>`.

## Build and check

From the source root:

```text
python tools/build_source.py check
python tools/build_source.py all
```

After configuring the paths, `Build-Source.cmd` provides the same full build by double-clicking.

The `all` command:

1. Validates the original host and pinned dependency hashes.
2. Compiles the Java MOD and runs Patchbay modkit finish.
3. Prepares hooks in a new `build/<timestamp>/prepared-source/` directory, with deep verification.
4. Builds the persistent-integration payload and C# single-file installer.
5. Runs `TransportTimeTest` and verifies the EXE's embedded payload.

It does not install, start or automate Bitwig. Outputs are under `out/video-preview/`, `integration/package/` and `releases/setup-1.0.0-dev.3/`. Build state and verification records are under `build/`.

Individual stages are `mod`, `prepare`, `package`, `exe` and `verify`. `prepare` requires `mod`; run `mod` again before a fresh preparation so a new candidate directory is used. The compiled-host cache is accepted only with its matching source-hash marker.

The legacy builders retain original development defaults. Use `tools/build_source.py` across machines: it overrides those paths and invokes the build-only flow. It does not download or install dependencies automatically.

## Launcher candidate

After the `mod` stage, run `python tools/package_launcher.py`. This produces a local candidate ZIP and checksum under `releases/launcher/`; it does not publish them. Existing candidate files are never overwritten. To build another candidate of the same version for review, use a fresh `--output <directory>`.

The public ZIP deliberately excludes the locally bundled mpv executable and DLLs. To prepare the actual first-use-download package, use `python tools/build_source.py prepare --mod-zip releases/launcher/video-preview-0.5.0-dev.3.zip`. Without `--mod-zip`, preparation retains the local bundled output for the separate EXE workflow. Neither command modifies the installed host.

Run `python tools/test_player_runtime.py` for isolated failure, checksum, cancellation, cache recovery and concurrency tests. Add `--online` to also download the exact pinned upstream archive, extract its three allowed files and check offline reuse. These tests use new workspace caches under `build/player-<timestamp>/`, never the normal user cache or projects. The online test requires GitHub access and Windows `tar.exe`.

For the actual Launcher archive tests, obtain the official [Launcher v0.9.2 source](https://github.com/arius77R/patchbay-launcher/tree/v0.9.2), then run:

```text
python tools/test_launcher.py --launcher-source C:/SDK/patchbay-launcher-0.9.2 --candidate releases/launcher/video-preview-0.5.0-dev.3.zip
```

This compiles the unchanged Launcher source with the local harness and exercises its catalog and installer against temporary directories. It uses the actual pinned framework packages, makes no network request and does not launch Bitwig. Results and upstream source hashes are recorded under `build/launcher-<timestamp>/`. The older-version update fixture is metadata only; native project migration requires separate host testing. Public release gates are in [RELEASING.md](RELEASING.md).

## Validation limits and troubleshooting

`verify` covers transport rules and payload integrity, not full host behavior. Before deploying changes, run import, fine editing, split/undo, synchronization, save/reopen and restoration checks against a disposable host.

A missing bundled JRE requires the complete matching Bitwig SDK layout, not another external Java executable. A hash mismatch requires checking the dependency version; removing the check does not create compatibility. Legacy test drivers may still reference standard `C:/Program Files` locations or require a specific test project. Read [tests/README.md](tests/README.md) before running them.

FFmpeg/ffprobe CLI is needed only for synthetic test-media generation; put it on PATH or set `NVT_FFMPEG` for the generators. Product decoding uses the mpv media stack, so building the product does not require a separate FFmpeg CLI installation.

The generated EXE is unsigned. Building does not publish source or create a release. Windows 6.1.0 and macOS require separate implementation and runtime validation.
