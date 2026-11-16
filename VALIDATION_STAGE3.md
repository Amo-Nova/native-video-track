# AV clips and media collection - historical 0.3.0-dev.2

Dates: 2026-11-15 to 2026-11-16. Actual host validation passed for the core AV/collection lifecycle. Later tempo-map and multi-codec corrections are documented in `VALIDATION_COMPATIBILITY.md`.

## Implementation and correctness

mpv probes and extracts the default audio stream as float WAV/RF64 with its start offset preserved. A native RAW audio clip carries video metadata and a waveform; mpv remains muted. Separation clones/replaces objects in one undo transaction. An early in-place metadata-clear approach failed undo restoration and was replaced; separation and silent-video relinking were retested successfully.

Video collection uses asset IDs, streaming copy, read-back SHA-256 and complete-file publication. Project-directory changes trigger recollection while old valid copies remain available. Legacy reference indexes and recovery-source records support reopen/resume without rewriting source clips. Missing derived audio can be recovered from collected video.

## Real media tests

| Input | Result |
|---|---|
| 12-second H.264/AAC stereo | Extraction, start/middle/end seeks and cancellation cleanup passed; pulse alignment maximum error 0.063 ms |
| H.264/AAC with about one-second delayed audio start | Offset retained; pulse alignment maximum error 0.605 ms |
| 30-second H.264/AAC mono | Extraction and start/middle/end seeks passed |
| 8-second 24 fps silent video | No-audio detection and seeks passed |
| 3,358,281,675-byte H.264/AAC, 8105.744 seconds | Complete copy/hash, 1,556,349,056-byte audio extraction and seeks at 0.1/4052.872/8104.744 seconds passed |

The large file contains real decodable packets produced by repeating synthetic content, not sparse space or appended padding. Initial copy/extraction took about 22.62 seconds with a 128 MiB Java maximum heap. This is not total process RSS and excludes the host. A second cached run is not evidence of first-import time.

Pulse timing compares decoded WAV events with intended video event times, not speaker/display latency.

## Actual host lifecycle

Verified native stereo waveform import, first-save collection, bound movement/trim/split with continuous source mapping, independent editing after separation, one-step separation undo, legacy silent-clip upgrade/undo, Save As, normal exit/reopen and whole-folder relocation with original references made unavailable.

The real large file imported with a waveform and decoded near its beginning, middle and end. A middle segment played for more than 70 seconds with embedded video and native track/master audio levels. After Save As and restart, the tail decoded at 8104.744271 seconds from project-local media.

Native model operations used a test-only JVMTI driver on the UI thread; waveforms and playback were also visually observed. This is not an automated replay of cross-application mouse drag/drop. Existing scrub/window code was unchanged; not every previous manual stress scenario was repeated.

Evidence: `tests/results/stage3-assets/report.json`, `stage3-native-summary.json`, `stage3-native-checks.json` and `stage3-saveas-checks.json`. Raw projects/logs/media are excluded.

A complete transferred project includes its `Video/` and audio directories. Bound AV replacement cannot silently keep unrelated old audio. Clip looping, video speed changes and transitions were not implemented. Passing one large file does not establish every codec/profile or multichannel configuration.
