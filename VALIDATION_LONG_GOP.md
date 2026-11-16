# Long-GOP seeking validation - 0.3.0-dev.4

Date: 2026-11-16, Windows / Bitwig 6.1.7. Normal save, exit, reopen and continued editing were included.

The main gain was repeated fine clip movement: H.264 4K60 final settling took **31.8-48.3 ms**, compared with one previous 212.4 ms observation. Other fine targets took **154.9-234.3 ms**, and larger sweeps **231.5-251.5 ms**. Not every target improved; uncached long GOPs still require full decoding.

## Changes

- Store at most 128 intervals confirmed by actual exact decoding; reuse a paused frame only when that confirmed interval covers the target. Entries contain numbers, not images.
- Advance from current decode state only where an exact-frame record exists, checking actual PTS. Unknown ranges, timeout, overflow or mismatch fall back to exact seek. Nominal FPS alone cannot prove equivalence.
- Replace a stale paused seek only after at least 60 ms target quiet, at least 80 ms old-seek execution and a 500 ms replacement interval. Continuous movement still completes intermediate frames.
- Use overlapped named-pipe I/O with separate read/write completion events and valid native buffers. Close cancels and reaps pending operations; request IDs isolate old replies.
- Invalidate reuse on playback, clear records on file changes and retain exact seeking in the final second to avoid confusing EOF/container timestamps with real frames.

Audio, source-offset semantics, native editing, storage and window rendering were unchanged.

## Latency comparison

Same five-media project, tempo curve and final source targets. Baseline: one observation each. New build: three rounds; this is not a statistical-significance claim.

| Operation | Previous single result | New min / median / max |
|---|---:|---:|
| H.264 4K60 fine target A | 220.2 ms | 156.1 / 212.0 / 232.0 ms |
| H.264 4K60 fine target B | 218.4 ms | 204.7 / 233.5 / 234.3 ms |
| H.264 4K60 fine target C | 224.9 ms | 154.9 / 197.3 / 231.5 ms |
| H.264 4K60 clip micro-move | 212.4 ms | 31.8 / 33.1 / 48.3 ms |
| H.264 4K60 larger sweep | 227.5 ms | 231.5 / 245.1 / 251.5 ms |
| HEVC Main10 4K30 fine target | 124.1 ms | 126.2 / 138.9 / 141.9 ms |
| AV1 4K30 fine target | 173.1 ms | 186.3 / 187.2 / 187.3 ms |
| VP9 VFR 1080p fine target | 126.9 ms | 109.7 / 126.6 / 153.9 ms |
| ProRes 4K24 fine target | 90.8 ms | 110.2 / 110.7 / 125.5 ms |

Timing starts after the final native edit call returns and waits for host propagation, engine consumption and stable decoded output. A separate exact seek checks the final frame. This includes observation/scheduling overhead, not physical mouse-to-screen time. Unlike the earlier compatibility report, this measurement removed a 100 ms floor, so its numbers cannot all be directly subtracted from that older table.

## Correctness and remaining cost

Passed 175 paused-frame reuse comparisons and 90 forward cases, 87 using the fast path and the others falling back. All matched exact decoding. Passed 1280 concurrent IPC replies, five closes during decoding and three bounded stale-seek replacements without stale-reply contamination.

Native tests covered 4860 fine updates, 1800 move/left-trim updates, and 72 splits with matching undos across codecs and a near-step tempo case. Source ranges stayed continuous and native audio/video source mapping matched within floating-point precision.

Five-codec playback and H.264 playback immediately after scrubbing each provided 35 samples with zero reported dropped frames. Maximum matrix clock error was 58.54 ms; restarted 4K60 cold playback reached 24.54 ms. Sampling began after 0.7 seconds, and no physical loopback measurement was made.

Stop-time exact repositioning still had roughly 327/405 ms cases and a restarted cold-playback stop near 240 ms. Save/reopen restored all clips, tempo data, trims and collected media. No new comprehensive visual residual-pixel acceptance was claimed.

Evidence/tools: `tests/results/longgop-summary.json`, `longgop-before.json`, `longgop-after.json`, `run_longgop_benchmark.py`, `run_longgop_regression.py` and `run_longgop_player_test.py`. Raw records and projects are excluded from this archive.
