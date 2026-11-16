# Preview placement validation - 0.5.0-dev.1

Date: 2026-11-16. Windows x64 / Bitwig 6.1.7 / Patchbay 0.36.0-dev.14.

Actual-host tests passed all five placement modes while stopped and playing, shared render-window/player reuse, workspace splitter behavior, unchanged clip/marker/undo state, floating close/show, per-edge size retention, restart persistence, off-screen-coordinate recovery and floating-menu return to docking.

One session kept the same mpv PID and render HWND across modes. A 320-pixel left panel retained its size. Floating bounds 720x480 at (300,180) survived close/show and restart. Simulated (-50000,-50000) bounds recovered to a visible primary work area. Playback spot checks during mode switches had maximum clock difference 26.2 ms, not a continuous per-frame latency measurement.

Synthetic settings-layout checking passed at 680x740 with 21 buttons and nine labels. A first combined script sought before the test clip and correctly showed an empty state; after correcting the test start to beat 30, the complete run passed. The initial run was not counted as a product timing failure or a successful full test.

## Codec/edit coverage

Twenty cases paired each codec with one placement. This is not the complete 25-combination codec-by-placement matrix. Each pair received 180 moves, 180 trims, 12 splits/undos and 35 playback samples.

| Input / placement | Move UI p95 | Trim UI p95 | Split UI p95 | Max playback clock error |
|---|---:|---:|---:|---:|
| 4K60 H.264 / top | 17.10 ms; repeat 5.94 ms | 2.88 ms | 28.15 ms | 25.24 ms |
| 4K30 HEVC 10-bit / right | 7.37 ms | 1.94 ms | 24.31 ms | 44.52 ms |
| 4K30 AV1 / floating | 6.76 ms | 1.85 ms | 16.58 ms | 43.74 ms |
| 1080p VFR VP9 / left | 5.92 ms | 1.85 ms | 18.35 ms | 48.46 ms |
| 4K24 ProRes / bottom | 1.90 ms | 1.63 ms | 40.69 ms | 57.62 ms |

Both H.264 move results are retained; no unique explanation for the first higher result was established. Splits remained source-continuous and undo restored one clip. RAW audio/video mapping differed by less than 20 microseconds. Playback samples remained below the 80 ms test threshold with zero reported dropped frames; extra H.264 playback after scrubbing reached 57.67 ms.

Final micro-move settling took 28-63 ms. Stop-time exact settling remained 186-294 ms, with an extra post-scrub H.264 case at 485 ms. UI response is not end-to-end display latency.

## Project lifecycle and limits

New projects inherited machine-local placement without stale video. Stereo AV import, default absolute time, a tempo change to 140 BPM, a six-second locked marker, AV separation/undo, Save As/collection and actual restart/reopen all passed. Persistent marker meaning was compared rather than session IDs. Editing/media/player core source remained unchanged from 0.4.0-dev.1, apart from a hidden-preview message.

The main environment was a single screen at one scale. Mixed-DPI monitors, physical monitor removal, modal-window interactions and full continuous visual stress were not comprehensively tested. Off-screen recovery was a coordinate simulation. Host geometry/window-state and synthetic layout checks do not establish complete visual residual-pixel acceptance. Placement is menu-selected; drag-to-edge snapping is not included.

Evidence/tools: `validate_preview_placement.py`, `run_placement_codec_native.py`, `test_preview_lifecycle.py`, `tests/results/placement-codec-regression.json`, `placement-lifecycle.json`, `placement-reopen-verification.json` and placement snapshots. Raw artifacts are excluded.
