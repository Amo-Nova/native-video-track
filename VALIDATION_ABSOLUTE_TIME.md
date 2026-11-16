# Absolute time and picture markers - 0.4.0-dev.1

Date: 2026-11-16. Windows x64 / Bitwig 6.1.7 / Patchbay 0.36.0-dev.14. Optional tempo-following video speed is not included.

New AV and silent-video imports default to project seconds and original-speed playback. Fixed BPM, continuous curves and near-step tempo changes preserve second-based boundaries and source offsets. RAW audio mapping remains aligned. Native move, copy, trim, split, undo and redo remain available; detached objects inherit timebase and then edit independently.

Video-panel markers default to locked seconds and can switch to music position. Existing markers are not automatically converted. Manual video movement does not carry project markers. Legacy clips keep their original mode until explicit conversion, which preserves the current range/frame/audio and can be undone once.

## Native lifecycle results

- A 60-second AV clip at 10.909091-70.909091 seconds in a 110 BPM project retained that range at 240 BPM and with near-step tempo points.
- Adjacent clips at 12-32 and 32-70 seconds retained source offsets of 2 and 22 seconds under tempo-curve edits. Identity remained intact and one undo/redo restored tempo and clips together.
- Reopened candidate tests covered move/copy/trim/split continuity, repeated tempo changes, marker-mode undo, separation and post-separation tempo edits.
- RAW audio source time advanced one second per project second and matched video mapping within a 20-microsecond assertion tolerance. This is model mapping, not device latency.
- Five legacy codec clips preserved music-position semantics on open; conversion and undo/redo passed. The earliest silent carrier without asset metadata was also tested.
- First save, Save As, normal reopen, two-project switching, complete-folder relocation, missing-copy recovery and audio rebind passed.
- Synthetic control-layout checking passed at 680x705 with 19 buttons and eight text controls; this is not desktop visual stress evidence.

## Five-codec editing/playback matrix

The active audio-engine test project completed 21 cases: each codec received 180 micro-moves, 180 trims, 12 splits/undos and 35 playback samples, plus H.264 playback after scrubbing. A first run with an inactive audio engine yielded no playback samples and was not counted; the complete matrix was rerun successfully.

| Input | Move UI p95 | Trim UI p95 | Split UI p95 | Max playback clock error |
|---|---:|---:|---:|---:|
| 4K60 H.264 / AAC | 8.61 ms | 2.21 ms | 27.10 ms | 48.12 ms |
| 4K30 HEVC Main10 / AAC | 6.85 ms | 2.03 ms | 25.00 ms | 41.82 ms |
| 4K30 AV1 / Opus | 6.77 ms | 2.07 ms | 17.51 ms | 35.68 ms |
| 1080p VFR VP9 / Opus | 6.48 ms | 1.69 ms | 17.03 ms | 35.41 ms |
| 4K24 ProRes / PCM | 1.58 ms | 1.63 ms | 39.39 ms | 50.82 ms |

H.264 playback after scrubbing reached 74.87 ms, below the test's 80 ms threshold. Reported decode/render drops were zero. Trims with unchanged source targets did not trigger extra seeks. Final micro-move settling was about 28-46 ms; stop-time exact seeks were about 154-453 ms.

## Limits and implementation

Adjustment is inside native edit transactions, with a refreshed tempo map and batch remove/update/reinsert to prevent intermediate overlaps. No polling timer rewrites the project. The MOD must be loaded for time-lock maintenance; ordinary-launch loading was added later through persistent integration.

Supported absolute audio is a forward, non-looping single RAW event. Complex audio, video speed/pitch changes, thumbnails, export and exact adjacent-frame VFR navigation are not covered. The earlier large-file result was retained, not repeated. No new complete visual residual-pixel stress pass or physical AV loopback was performed; rendering code was unchanged.

Evidence: `tests/results/absolute-edits-summary.json`, `absolute-legacy-summary.json`, `absolute-recovery-summary.json`, `absolute-codec-regression.json`, `native-absolute-*.txt` and `run_absolute_codec_native.py`. Raw evidence is not bundled.
