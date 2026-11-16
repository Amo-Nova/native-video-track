# Test sources

`python tools/build_source.py all` automatically runs only `TransportTimeTest` and EXE payload verification. It does not launch or install Bitwig.

Other Java tests cover real mpv decoding, scrubbing, confirmed-frame reuse, long-GOP final targets, media extraction and layout. Configure local JDK/dependency paths and generate the required synthetic media first. Generators are `create_media.py`, `create_stage3_media.py` and `create_compatibility_media.py`; FFmpeg comes from PATH or `NVT_FFMPEG`. Large-file and 4K tests require additional time and disk space and are not run automatically.

`NativeAcceptanceAgent.java`, `native-agent/` and `run_native_acceptance.py` are experimental host drivers. They connect to and operate a live Bitwig test process; historical scripts may assume that only one test host exists. Use a dedicated disposable host and test project, inspect the command before running it, and do not use an unsaved working project.

`integration/tests/` and `integration/exe/test_exe.py` exercise installation and restoration. Their fixtures expect a local original JAR or a verified backup. Configure isolated paths before use. File-association tests use an isolated HKCU registry tree.

Useful regression families:

- `run_compatibility_assets.py`, `run_compatibility_native.py`: codecs, extraction, timing and native editing.
- `run_longgop_player_test.py`, `run_longgop_benchmark.py`, `run_longgop_regression.py`: exact-frame reuse, IPC lifetime and native seek/edit behavior.
- `run_absolute_codec_native.py`: absolute-time behavior with multiple codecs.
- `validate_preview_placement.py`, `run_placement_codec_native.py`, `test_preview_lifecycle.py`: layout, editing, persistence and recovery.
- `test_multi_video_import.py`: queued imports, project-relative assets, relocated reopen and partial recovery.

Historical reports identify raw evidence paths, but projects, media, logs and compiled agents are not shipped here. Regenerate fixtures rather than assuming those paths already exist. API-driven host editing is distinct from actual mouse drag/drop verification.
