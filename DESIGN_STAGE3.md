# AV clips, project media and absolute time

This document summarizes the implemented rules introduced across 0.3.x, 0.4.x and the 0.5.0-dev.2 import/path fixes. Current status is in the root README; this is not a proposal for a new mode.

## Bound AV and separation

Import probes the default audio stream using the bundled mpv, extracts float WAV/RF64 with the audio start offset preserved, and creates a native RAW audio clip carrying video metadata. Silent media remains a video carrier clip. The preview player stays muted.

Native clip movement, trim, split and copy operate on the shared object. Separation clones the audio content and geometry, creates the matching silent-video carrier and replaces the original within a single undo transaction. The two objects are subsequently independent. Clearing opaque metadata in place did not participate correctly in undo during early development; retaining and replacing objects fixed that behavior.

Legacy silent clips can explicitly extract and bind audio without changing their current boundaries or source offset. Background completion validates the original project and clip identity. Replacing bound video while silently keeping old audio is not supported; reimport is required.

## Project collection and recovery

Assets use independent identities, including files with identical basenames. Video is collected to `Video/<asset-id>/...`, extracted audio to `samples/Video/...`. Metadata preserves relative paths and original-source fallbacks. Streaming copy, read-back SHA-256 and complete temporary-file publication avoid partially valid targets and unbounded memory growth.

Project-directory changes, first save and Save As trigger collection into the new directory while valid old copies remain usable. Recovery indexes preserve prior locations so interrupted collection can resume after reopening. Derived audio can be rebuilt from collected video. Legacy references use project-local `Video/.legacy-*.json` indexes without rewriting old clips or implicitly adding sound.

A complete project transfer includes the project file and collected directories. A failed or missing asset does not block the remaining assets. Retry uses verified project paths and does not guess replacement identity from a matching filename.

## Multi-video drop routing

The final import routing recognizes arranger descendants as well as the outer arranger. Native coordinate conversion preserves the real drop location. A single drag/drop data object is committed once; independent requests queue with their own positions. Files in one batch remain sequential. Choosing files in a new project uses the same clip import path; the original single-video association is retained only for compatibility.

The 0.5.0-dev.2 tests covered 39 valid drop targets, same-named assets from different directories, duplicate callbacks, queued requests and actual decoder switching. The subsequent persistent-integration tests completed cold relocated reopen and partial missing-media recovery.

## Absolute time and markers

New imports use absolute project seconds at original speed. Legacy music-position objects retain their behavior until explicitly converted. A conversion preserves the current frame, range and audio mapping and can be undone once. Separated objects inherit the current timebase and then edit independently.

Before native edits, absolute seconds and source offsets are captured. During the same edit transaction, the native time map is refreshed and clip geometry is updated in a batch, temporarily removing and reinserting objects to prevent intermediate overlap trimming. Object identity and native undo remain intact; a timer does not repeatedly rewrite project state.

Markers created through the video panel default to locked project seconds, with a music-position option. Existing markers are not automatically converted. Picture markers identify project events, not source-frame tracking points: moving or recutting video manually does not move them.

Absolute-time audio support is limited to a forward, non-looping single RAW event. Complex audio objects are not silently converted. Video speed following BPM, time stretching and pitch-preservation rules are separate, unimplemented work.

## Seeking and validation

The 0.3.0-dev.4 performance work reuses confirmed numeric frame intervals, permits bounded sequential advancement and replaces stale final seeks under rate limits. IPC uses Windows completion events. Source mapping, native editing and rendering semantics remain independent of these optimizations.

Validation covers WAV pulse alignment, delayed audio starts, real approximately 3.36 GB media, native waveforms and AV undo, tempo curves, multiple codecs, exact final frames, collection, Save As and relocated reopen. See `VALIDATION_STAGE3.md`, `VALIDATION_COMPATIBILITY.md`, `VALIDATION_LONG_GOP.md` and `VALIDATION_ABSOLUTE_TIME.md` for results and measurement limits.

Thumbnails, proxy management, scene detection, full SMPTE/drop-frame presentation and exact adjacent-frame VFR navigation are not part of this implementation. Four-edge/floating placement is documented separately in `DESIGN_PREVIEW_PLACEMENT.md`.
