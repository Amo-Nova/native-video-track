# Persistent integration validation

Date: 2026-11-16. Integration 1.0.0-dev.1, Video 0.5.0-dev.2, exact Windows x64 Bitwig 6.1.7.

## Implementation

The local installation replaces 49 prepared host classes and merges 147 helper classes/resources, preserving all other original JAR entries with per-entry hash checks. Environment-reading calls are redirected in BML API and three reflection bridges; video decoding, absolute time, audio, editing, markers and layout algorithms are unchanged.

The runtime resolves versioned components from the host location and writable data from `%LOCALAPPDATA%/BitwigVideo`. Original backups, staged verification and pending records protect installation/recovery. Running hosts, unknown/upgraded JARs and damaged backups are refused. Restoration preserves user projects/media/settings and does not remove externally modified files.

## Isolated transaction tests

Passed first install and backup verification, configuration initialization without BML environment variables, reflection-bridge loading, repeated install, missing-component detection/repair, interruption before and after publication, independent recovery, repeated restore, unknown-JAR refusal, damaged-backup refusal and settings migration without overwriting existing preferences.

Ordinary-classpath `-Xverify:all` checks loaded and resolved fields/methods/constructors for all 191 changed/helper classes in that validation set. Unicode/space component paths passed isolated component tests. The native host launcher separately rejected non-ASCII install paths; moving the disposable host to an ASCII directory allowed launch. The installer therefore requires ASCII host installation paths, not ASCII project/media names.

Evidence: `integration/tests/runs/20261116-071555/results.json`, `recovery-edges.json` and `link-all-classes.log`.

## Ordinary launch and complete project paths

The original `Bitwig Studio.exe` loaded the integration with no Patchbay environment. Tests imported same-named distinct videos, suppressed repeated callbacks, queued independent requests and produced four clips backed by three assets. Move, split, undo and BPM edits retained absolute time and original-audio mapping. First save collected video and audio.

The entire project was copied to a new directory while original project/media paths were made unavailable. A cold launch through the original program opened the moved project with all four clips and no manual relink. A later cold run removed one video: the other two assets remained usable, and restoring the file allowed automatic recovery and correct locating.

## Editing/decode regression

| Operation | UI-call p95 | Final settling time |
|---|---:|---:|
| 180 fine locations | 0.97 ms | 88.74 ms |
| 180 micro-moves | 2.68 ms | 17.65 ms |
| 180 trims | 2.65 ms | 12.34 ms |
| 12 splits with undo | 48.74 ms | 26.98 ms |

Thirty-five playback samples had maximum frame-clock error 25.97 ms and no newly reported dropped frames. Bound audio source offsets and one-second progression agreed within a 0.02 ms assertion tolerance; split source ranges remained continuous. An extremely dense artificial trim sequence ended with a 0.001-beat geometry residual, about 0.43 ms at 140 BPM, while AV mapping remained consistent. No sample-accurate zero-residual claim is made.

These are software timing/model observations, not display/audio-device round-trip latency. Evidence: `integration/tests/native-edit-regression.json` and `tests/results/multi-video-import/`.

## Installed-host results

Start-menu, taskbar and Windows ShellExecute project-file cold starts passed. Neither `BML_MOD_CONFIG` nor `JAVA_TOOL_OPTIONS` was present, and loading came from the integrated host JAR. The project-file launch restored four clips/three videos and each clip located correctly. Final status was Installed, healthy=true, backupVerified=true and pending=false. Restoring the isolated copy recovered the exact original hash.

Original JAR SHA-256:

```text
a5b303466cf66a80e0fd7df00e49fb14eb41c23e59d30b300c0554b6769b3367
```

Verified installed JAR SHA-256:

```text
c10efef91e76aa50545e36d4f740be4b2d43ebd1eb0631b82555b8ca7b14c78a
```

File-association checks used an isolated registry tree and covered first/repeated association, interrupted repair, restoration, existing defaults, UserChoice and preservation of later application changes. No UserChoice hash is rewritten.

Only the exact Windows 6.1.7 build is covered. Upgrades/official repairs require revalidation. Interactive UAC behavior was not fully exercised in the administrator test environment. macOS installation remains unimplemented. Raw logs, registry fixtures and projects are not included in this source archive.
