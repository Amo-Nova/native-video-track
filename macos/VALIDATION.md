# Mac contribution import validation

Date: **2026-11-17**. MOD **0.5.0-dev.14**, targeting **Bitwig Studio 6.1.7 on Apple Silicon**.

## Contributor evidence

Amo Nova supplied this version after adjusting it on a Mac and identified [AUPhui](https://github.com/AUPhui) as a colleague who helped with testing and contributions. The supplied handoff also describes compilation, ten-hook Modkit finish and Mac preparation checks. Raw Mac runtime/preparation logs, the exact macOS/player versions and compiler patch version were not included.

The repository import runs on Windows. It does not claim a fresh Mac build, playback/sync benchmark, signing check or installation/save/reopen acceptance pass. The supplied runtime payload is preserved.

## Input integrity

| Supplied archive | Original SHA-256 |
|---|---|
| `NativeVideoTrack-Mac-Source-0.5.0-dev.14.zip` | `e763c2b1051311664d7fc1d02b55c5a499c81a8c8aaba5679cc176eaeb481bf8` |
| `video-preview-0.5.0-dev.14-macos.zip` | `d629c0a1025fadcc3da52a2f1d2c7584334c760b344eba0c4b4679ac0081232d` |

Both originals remain unaltered. All 31 hashes in the supplied source manifest matched. All 15 runtime Java files embedded in the MOD matched the source archive byte-for-byte, and all supplied internal package checksums passed. The helper contains project classes targeting Java 21 (major version 65), without host/player/codec binaries.

## Publication scope

The source is imported under `macos/`. All runtime Java files, the supplied persistent implementation and mapping utility are preserved. The only change to `build_mac.py` adds license/credits and extends package checksum coverage; it does not change compilation or runtime behavior.

The public MOD retains the supplied helper JAR, built manifest, hooks, `uses.tsv`, runtime source, icon and action registration. English documentation replaces stale handoff text, and MIT/project/AUPhui attribution is added. This changes the archive hash, so public checksums are separate from the input hashes above. `IMPORT_PROVENANCE.json` records preserved-file hashes and `SOURCE_MANIFEST.json` records the current source tree.

Both public ZIP names end in `-macos.zip`. This keeps them outside Launcher 0.9.2/0.9.4's automatic asset-version pattern and preserves the existing Windows catalog entry. Mac installation uses the supplied manual **Patchbay → Mods → Add Mod…** flow.

## Checks and limits

The import checks archive safety, CRCs, complete package/source checksums, embedded-source equality, helper identity/class versions, source syntax and absence of host/player binaries. The ten unmodified official Launcher 0.9.4 Java sources passed package acceptance, install/reinstall, installed credit/license checks, external settings/media preservation and a combined Windows/Mac release-feed selection test. The supplied helper remains SHA-256 `0182747df9d71afb289395e3cd9b851d327796fabb6a826d0239c97621f260d6`. Public assets are downloaded again after publication and compared with the validated candidates.

These are integrity and distribution checks. They do not establish native Mac feature parity, Intel support, mixed-display behavior, broad codec/large-file coverage, physical AV latency or Windows/Mac project interchange. The optional persistent installer source is included for maintenance; its running-host option, signing behavior and older diagnostic version string were not exercised or changed here.
