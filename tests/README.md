# Test sources

`python tools/build_source.py all` automatically runs only `TransportTimeTest` and EXE payload verification. It does not launch or install Bitwig.

Other Java tests cover real mpv decoding, scrubbing, confirmed-frame reuse, long-GOP final targets, media extraction and layout. Configure local JDK/dependency paths and generate the required synthetic media first. Generators are `create_media.py`, `create_stage3_media.py` and `create_compatibility_media.py`; FFmpeg comes from PATH or `NVT_FFMPEG`. Large-file and 4K tests require additional time and disk space and are not run automatically.

`python tools/test_player_runtime.py` compiles `PlayerRuntimeTest` with the actual player setup implementation. It checks first use, offline/fresh-provider reuse, existing bundled player reuse, one download across concurrent threads and JVM processes, interrupted download, wrong archive/extracted hashes, cancellation, repair and preservation of unrelated cache data. `--online` additionally exercises the production Java HTTPS download and Windows tar extraction against the pinned upstream release. Both modes use isolated workspace caches and leave user projects and the normal player cache untouched.

The separate contributed Mac edition has host-free source/package checks in `tools/check_macos.py`. On the configured Windows review machine, `tools/test_macos_package.py --launcher-source <official-checkout> --candidate <mac-zip>` compiles actual Launcher 0.9.2 or 0.9.4 sources with `MacPackageTest`. It checks manual archive install/reinstall, runtime identity, attribution, external state and preservation of the Windows automatic catalog entry. `--catalog-json <file>` accepts a real public release feed for post-publication verification. None of these checks executes the Mac runtime; see `macos/VALIDATION.md` for the evidence boundary.

`NativeAcceptanceAgent.java`, `native-agent/` and `run_native_acceptance.py` are experimental host drivers. They connect to and operate a live Bitwig test process; historical scripts may assume that only one test host exists. Use a dedicated disposable host and test project, inspect the command before running it, and do not use an unsaved working project.

`integration/tests/` and `integration/exe/test_exe.py` exercise installation and restoration. Their fixtures expect a local original JAR or a verified backup. Configure isolated paths before use. File-association tests use an isolated HKCU registry tree.

Useful regression families:

- `run_compatibility_assets.py`, `run_compatibility_native.py`: codecs, extraction, timing and native editing.
- `run_longgop_player_test.py`, `run_longgop_benchmark.py`, `run_longgop_regression.py`: exact-frame reuse, IPC lifetime and native seek/edit behavior.
- `run_absolute_codec_native.py`: absolute-time behavior with multiple codecs.
- `validate_preview_placement.py`, `run_placement_codec_native.py`, `test_preview_lifecycle.py`: layout, editing, persistence and recovery.
- `test_multi_video_import.py`: queued imports, project-relative assets, relocated reopen and partial recovery.

Historical reports identify raw evidence paths, but projects, media, logs and compiled agents are not shipped here. Regenerate fixtures rather than assuming those paths already exist. API-driven host editing is distinct from actual mouse drag/drop verification.
