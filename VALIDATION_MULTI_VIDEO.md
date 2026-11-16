# Multi-video drop and project paths - 0.5.0-dev.2

Date: 2026-11-16. Windows x64 / Bitwig 6.1.7. Subsequent persistent-integration acceptance completed the full import/relocated cold reopen/partial recovery lifecycle; see `VALIDATION_PERSISTENT_INSTALL.md`.

The original drop hook recognized only the arranger itself; track/clip descendants could accept native audio import first, leaving an ordinary audio clip rather than a bound video clip. Routing now recognizes arranger ancestry and uses the native drop coordinate conversion. Event deduplication remains in place.

Files in one batch remain sequential. Independent requests queue and retain their own second-based drop positions. New-project file selection uses the same multi-clip import path. Each asset resolves its project-relative copy independently; invalid old absolute paths are not tried before project-path validation. One failed asset does not block others, errors back off per file, and filenames are not used to guess identity.

Passed checks:

- Compilation and preparation: 10 hooks, 64 transforms, 49 replacement classes and linkage verification.
- All 39 relevant arranger/descendant controls returned the video drop handler.
- Two distinct same-named files imported as separate assets at beats 8 and 30, with the second following the first's actual end. Repeated submission of the same event did not duplicate them.
- Two queued independent requests at beats 80 and 120 produced four clips referencing three assets without losing requests while busy.
- All four clips used the correct project copy and decoded source second 1 correctly.
- Single move/split checks preserved source offsets, duration, continuity and RAW audio mapping. These individual checks did not replace stress regression.
- Real cross-application drag/drop produced one `Video drop committed` and one bound AV insertion.
- Synthetic settings-layout checks passed; core timing, media, player and window classes were byte-identical to dev.1.

An early automated split/frame-location run overlapped manual input and timed out. That run was not counted as fully passed or treated as a decoder failure. The later isolated persistent-integration run completed the outstanding collection, relocation, missing-media and editing cases. Test tools refuse automatic edit/exit when media outside the test area is discovered.

Preview still follows the project playhead, not selection alone. A playhead outside all video clips shows an empty preview. Bound video replacement cannot silently retain old audio, and absent source plus absent project copy cannot be reconstructed.

Evidence: `tests/results/native-drop-routing-dev2.txt`, `native-multi-import-*.txt` and `tests/test_multi_video_import.py`. Raw source media, projects and logs are excluded.
