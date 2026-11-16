# Native clips, embedded preview and markers - historical 0.2.x

Date: 2026-11-15. Windows x64 / Bitwig 6.1.7 / Patchbay 0.36.0-dev.14. Final stage version: 0.2.0-dev.4. Subsequent audio/collection and platform work is covered separately.

## Native workflow

Passed in the actual host: empty-project file import, one-step import undo/redo, embedded preview, native splitting, movement, both-edge trimming, gap clearing, native cue-marker creation/rename/navigation, one-step marker undo, nominal frame stepping, save, normal exit/reopen and continued editing.

A split at source second 3 was moved to project second 20; project second 21 displayed source second 4/frame 120. Trim, save and reopen retained the mapping. Forty-five playback samples had median absolute clock error 7.206 ms, p95 13.196 ms and maximum 30.370 ms for the short H.264 test. These do not measure physical output latency.

## Repeated import and scrubbing

A single external drag had triggered three imports through an incorrect hover-timer hook. The callback moved from `dxc -> dwF.beI -> dKq` to the actual `dKY` drop event through `dqD -> dwF.Gef -> dKt`, with one commit per drag-data object. Later multi-video validation covers the external drop result.

dev.2 waited for decode completion before the next exact seek, retained only the latest intermediate target, preserved the paused decoder across gaps and reduced redundant property writes/log flushing. Each 30/24 fps test submitted 160 targets at 10 ms intervals; 147/134 intermediate requests were merged and final decoded positions were correct. Final waits were approximately 92/112 ms in headless decode tests.

dev.3 compared source targets directly, coalesced same-frame micro-movements and issued a final exact seek after roughly 60 ms of quiet. Two 320-update tests issued 12/10 seeks and matched direct exact decoding at the final target. Same-source movement and unchanged-frame trimming avoided redundant seeks. These measurements do not establish mouse-to-screen latency.

## Residual-pixel fix

Manual testing reported improved scrubbing but residual video pixels in the arranger during stress. The original symptom was not reliably reproduced or assigned a unique cause. dev.4 addressed window ownership coordinates, `WS_CLIPCHILDREN`, synchronous child geometry updates, `SWP_NOCOPYBITS`, old-region invalidation, out-of-bounds hiding and limited panel repainting.

Real-host checks covered panel resizing, device/Video switching, playback through gaps, two host-window sizes, clip movement/undo and edge-handle display behavior. The same player/render window was retained and final source frames remained correct. One short-lived green rectangle had an undetermined origin and was not treated as proof that all visual anomalies were eliminated. Final manual use accepted the dragging/residual-pixel fix; this does not establish all-monitor or all-codec reliability.

Evidence families: `tests/results/scrub-regression-dev2.txt`, `scrub-regression-dev3.txt`, `fine-scrub-final-dev3.txt`, `display-dev4-20261115/` and `display-dev4-followup-20261115/`. Raw logs and projects are excluded.

Stage-two limitations included no audio-bearing AV clips or project media collection and music-position carrier geometry. These were addressed in later stages; full mixed-DPI and continuous visual stress coverage remains incomplete.
