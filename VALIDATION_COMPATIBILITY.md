# 4K, codecs and tempo-map validation - 0.3.0-dev.3

Date: 2026-11-16, Windows / Bitwig 6.1.7. This historical stage tested BPM edits and tempo automation, not a new variable-speed video mode. Absolute-time defaults were added later.

## Corrected behavior

A trimmed clip crossing a tempo curve previously differed from native RAW audio by 137.383 ms because video used the original import position for conversion. The fix uses native event/clip time context; tested source mapping then matched to floating-point precision. Unupgraded legacy metadata retains its historical model.

Cold 4K60 playback previously converged too slowly after an exact seek under steady-state +/-3% correction. A bounded post-seek catch-up phase now returns to steady-state once close enough or after two seconds. A restarted cold-playback test reduced sampled maximum error from 95.279 to 29.706 ms and p95 from 89.327 to 28.738 ms. Native audio, tempo and saved clip geometry were unchanged.

## Codec matrix

All inputs were real 12-second synthetic motion videos with white flashes and stereo pulses at 1/3/5/7/9/11 seconds. H.264/HEVC/AV1 included approximately four-second GOPs. On the test GPU, H.264/HEVC/AV1/VP9 used d3d11va; ProRes used software decoding.

| Input | Fine-edit call p95 ceiling | Largest split call | Final seek ceiling | Playback error p95 / max |
|---|---:|---:|---:|---:|
| 4K60 H.264 / AAC | 2.02 ms | 62.94 ms | 260.1 ms | 24.35 / 27.47 ms |
| 4K30 HEVC Main10 / AAC | 1.48 ms | 36.61 ms | 160.8 ms | 24.63 / 29.16 ms |
| 4K30 AV1 / Opus | 1.23 ms | 28.14 ms | 167.4 ms | 25.28 / 27.46 ms |
| 1080p VFR VP9 / Opus | 1.17 ms | 35.64 ms | 242.5 ms | 30.20 / 34.53 ms |
| 4K24 ProRes 422 LT / PCM | 1.52 ms | 57.32 ms | 198.7 ms | 41.78 / 45.15 ms |

Passed extraction, full copy, cancellation cleanup and exact start/middle/end seeks. Decoded pulse/event alignment had maximum error 0.063 ms. There were 2700 fine-edit updates across playhead movement, clip movement and left trims; unchanged source targets avoided extra decoding. Five codecs plus a near-step-tempo case completed 72 splits and matching undos with continuous source ranges. Short playback samples reported no decoder/render dropped frames.

Changing a trimmed clip to 60, 240 and 90 BPM produced source offsets matching RAW audio. Continuous and near-step tempo curves, AV separation/undo, normal save/restart/reopen and continued editing passed with project-local media.

## Measurement limits

UI timings include queueing/model calls and are not mouse-to-display latency. Final-seek measurement included a minimum 100 ms quiet period. Playback sampled 35 points over roughly 3.5 seconds beginning 0.7 seconds after start; unsampled startup behavior is not covered. API-driven edits do not establish mouse drag/drop behavior or a new visual residual-pixel stress pass.

Not all profiles, VFR patterns, 4K60 HEVC/AV1, long-duration thermal behavior or display configurations were tested. VFR time seeking passed; neighboring-frame controls still use nominal FPS. Physical device AV latency remains unmeasured.

Evidence: `tests/results/compatibility-summary.json`, `compatibility-assets/report.json`, `native-compat-*.txt`; rerun tools include `create_compatibility_media.py`, `run_compatibility_assets.py` and `run_compatibility_native.py`. Raw evidence and fixtures are not bundled.
