# Maintainer handoff

Repository candidate: Video 0.5.0-dev.3 / Integration 1.0.0-dev.1 / Setup source 1.0.0-dev.3. Video behavior is based on the 0.5.0-dev.2 snapshot dated 2026-11-16. See RELEASING.md for distribution status.

## First development session

1. Build the unmodified snapshot using [BUILDING.md](BUILDING.md). This establishes the pinned baseline without touching a running host.
2. Read `VideoTimeline`, `AbsoluteTime` and `MediaAssets` before changing clip semantics; read `MpvEngine`, `MpvConnection` and `ScrubCadence` before changing seek behavior.
3. Use a disposable compatible installation and synthetic projects for native tests. Several historical drivers assume a single test host and can edit or close it.
4. Keep runtime changes separate from dependency upgrades, host-version mapping and UI localization so regressions can be attributed.

## Behavior to preserve

- New imports use absolute project seconds at original speed. Optional tempo-following video speed is not implemented.
- The default AV clip is one native waveform-bearing object. Detaching produces independent editable objects; one undo restores the bound clip.
- Preserve native source offsets through move, trim, split, copy, undo and redo. Split segments must remain source-continuous.
- Native clips and project metadata are authoritative. Save As, complete-folder relocation, same-named distinct assets and partial missing-media recovery must keep working.
- Never rewrite source video. Use bounded-memory collection and do not block the host event thread with decoding, disk access or IPC.
- Coalesce intermediate scrubbing targets, but resolve the last target against actual decoded output. Do not treat nominal FPS as proof of an exact frame match.
- Keep old video pixels out of the arranger when moving, hiding or resizing the preview. Preserve actual embedded layout behavior.
- Preserve project markers at their selected timebase. Manually moving a video does not move a project picture marker.
- Installation checks that the host is closed, verifies the exact original and retains recoverable backups. Do not kill the host or overwrite unknown/upgraded files.

## Verified baseline

The existing Windows installation has passed multi-video import, native editing and undo, audio extraction/waveforms, AV separation, absolute-time behavior, picture markers, layout persistence, media collection, relocated cold reopen, partial recovery and ordinary launch-path checks. See the relevant `VALIDATION_*.md` files for stage-specific scope.

The clean source build separately passed MOD compilation, Patchbay preparation, integration/EXE packaging, transport rules and payload integrity. No new runtime claim is made by the documentation-only r2 handoff.

Historical JSON/TXT/log/project paths in the reports identify development evidence; raw logs, media and projects are excluded from this archive. Test code is included so suitable fixtures can be regenerated.

## Outstanding work

| Area | Current limit / next requirement |
|---|---|
| Bitwig 6.1.0 | No adaptation or runtime test. Select the exact host/map and revalidate every hook; do not bypass the version guard. |
| Apple Silicon | No runnable Mac package. Replace Windows IPC/rendering/install boundaries and validate on an actual Mac. |
| Long GOP | Confirmed nearby-frame reuse is faster, but uncached seeks and stop-time repositioning can still take hundreds of milliseconds. |
| Physical AV latency | Software frame clocks and audio source mapping have been measured; no display/audio-device loopback calibration. |
| Display coverage | Full mixed-DPI, multiple-monitor, modal-window and continuous visual stress coverage remains incomplete. |
| VFR frame stepping | Time seeking has been tested; adjacent-frame controls still use nominal FPS rather than a complete PTS index. |
| Installer UAC | Standard-user, alternate-admin credentials and cancellation flows are implemented but not fully interactively tested. |
| Localization | Current application/installer UI includes Chinese literals. This handoff translates documentation only. |
| Distribution | Project-owned source is MIT licensed. EXE is unsigned; public binary publication awaits player redistribution materials and release acceptance. |

Thumbnails, transitions/compositing, video export, scene detection, a full SMPTE/drop-frame UI and optional tempo-following speed are not implemented.

For a Mac test round, prepare one coherent candidate with version detection, synthetic media and log collection. The target is Apple Silicon; exact macOS and Bitwig versions still need to be selected. Preserve the Windows behavior and project format during the port. See [MAC_COMPATIBILITY.md](MAC_COMPATIBILITY.md).
