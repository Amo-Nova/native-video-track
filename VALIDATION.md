# Initial preview validation - historical 0.1.0-dev.1

Date: 2026-11-15. Windows x64, Bitwig Studio 6.1.7, Patchbay 0.36.0-dev.14. This stage validated a synchronized standalone preview, before native multi-clip video editing. Later capabilities are documented in the subsequent reports.

Passed: exact original-host hash matching, Java compilation with JDK 25.0.2/Java 21 output, modkit finish, Patchbay preparation, real host launch/menu registration, file selection and H.264 decoding.

The player test sought to 0, 7.5, 24.2, 1.1 and 14 seconds with errors below 0.04 seconds. Playback/pause, paused-frame retention, loop-back and reload passed. Missing and invalid files were rejected before committing the project association.

About 28 seconds of native playback produced 104 stable software-clock samples: median absolute error 6.182 ms, p95 19.355 ms, maximum 35.501 ms. Seven running loop-back events were observed. An offset test targeted source 4.545455 seconds and displayed frame 137 at 4.566667 seconds, within one 30 fps frame. These are software measurements, not physical display/audio-device latency.

Native save, normal exit/reopen, blank-project switching and preview close/show restored the correct association and two-second offset. Empty-project reads did not create unsaved modifications.

Development fixes included separating Swing into its own JVM to avoid host AWT initialization, distinguishing stopped position from relocated start position, waiting for `file-loaded` rather than only the command reply, and bounded diagnostics. Initial pipe polling was superseded by later overlapped-I/O work.

At this stage, native drag/drop, multiple clips, splits/trims, media collection, tempo-edit semantics and a broad codec/display matrix were not yet validated. Those historical exclusions are not the status of the current snapshot. Raw evidence paths included `tests/results/final-smoke.txt`, `tests/results/bitwig-mod.log` and stage-one test projects; raw artifacts are not in the source archive.
