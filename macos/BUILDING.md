# Build the macOS Apple Silicon edition

Target: MOD `0.5.0-dev.14`, Bitwig Studio **6.1.7 for macOS**. This is the contributed build flow; it was not rerun against a Mac SDK during the Windows-side repository import.

## Inputs

| Dependency | Expected location or requirement |
|---|---|
| JDK 25 | Selected by `/usr/libexec/java_home`; Java sources compile with `--release 21` |
| Python | 3.9 or newer for the supplied Mac scripts; repository-wide checks require 3.11+ |
| Patchbay/BML | `0.36.0-dev.16-macos`, default `~/Downloads/BML-0.36.0-dev.16-macos` |
| Patchbay source | `patchbay-src/` beside this `macos/` directory; provides `tools/build_mod.py` and `mod_uses.py` |
| Original Mac SDK | `pristine-bin/bitwig.jar` and `pristine-bin/libs.jar` inside this directory, privately copied from the matching original application |
| Actions and Menus framework | `action-menu-composition-framework` `0.8.0-dev.1`, from the matching Patchbay package |
| mpv | Runtime only, normally installed with Homebrew |

The original Mac `bitwig.jar` SHA-256 recorded by the contributed installer is:

```text
6024752847797eb41e6fbc91e081211937931cc452a695ab78226321cefdac8d
```

Use the original JAR, not one already containing a persistent overlay. A copied SDK and any original backup remain private build inputs. The Mac hash is different from the Windows 6.1.7 hash. Check `maps/macos-6.1.7-map1` against the actual SDK.

The supplied scripts use editable path constants rather than the root Windows `build.config.local.json`. Inspect `PATCHBAY`, `BITWIG_JAVA`, `RUNTIME`, `CHECK_MODS` and `BML_ROOT` before building. The exact Patchbay source commit and JDK patch version used for the supplied binary were not included; a byte-identical rebuild has not been independently demonstrated here.

## Build the MOD

From this directory on a Mac:

```sh
python3 build_mac.py
```

The script runs compile-jar, javac, Modkit finish, `kit_manifest` and `mod_uses`, then creates `out/video-preview-0.5.0-dev.14-macos.zip` and `out/check-mods/video-preview/`. The packaging step includes English instructions, MIT license, contributor credits and checksums. It does not install into the application.

For the actual Patchbay preparation check, use a disposable original compatible host, add the required Actions and Menus framework beside the candidate, and run the matching package's `loader/prepare-mod.sh` against that host's `Contents/Java` directory. Inspect the exact paths first; the expected target is `macos-6.1.7`.

The public import reused the supplied helper JAR without recompilation. Documentation and attribution additions change the public ZIP's hash; the original input hash and preserved code hashes are recorded in `IMPORT_PROVENANCE.json`. Do not use the old supplied ZIP hash as an assertion about a rebuilt publication archive.

## Check or reproduce the publication packaging

From the repository root, `python tools/check_macos.py` checks the current source manifest and preserved input hashes. `python tools/package_macos_import.py --input-zip <original-contributed-mod.zip> --output <new-directory>` packages the exact supplied binary with the reviewed English documents, license and credits, and also creates a standalone Mac source ZIP. It refuses another input binary or an existing output directory; it does not build, install or run the Mac code.

After an intentional documentation change, review the diff and regenerate its manifest with `python tools/check_macos.py --refresh-source-manifest` before packaging. Runtime changes require a new build and validation record rather than presenting an old contributed helper as rebuilt code.

## Optional persistent integration source

`persistent/persistent_install.py` provides `build`, `verify`, `install`, `status` and `restore`. It builds from a prepared runtime, adds the supplied `InstalledRuntime.java`, stores original-JAR backups, changes the local app bundle and performs an ad-hoc `codesign` operation. It is distinct from importing the MOD through Patchbay.

This publication includes that source for maintenance but does not publish a persistent installer or claim a fresh install/restore/signing acceptance pass. Configure all paths and use an isolated copy first. Save and quit Bitwig before any installation or restoration. The supplied opt-in `--allow-running` behavior remains in the source; it was not exercised or endorsed by this import.

The supplied bootstrap's diagnostic `video.version` string still says `0.5.0-dev.9`; the MOD manifest and startup label are `0.5.0-dev.14`. This diagnostic discrepancy is documented rather than changing untested integration code in a source-import task.

## Mapping utility

`compose_remap.py` composes the inverse Windows 6.1.3→6.1.7 remap with Windows 6.1.3→Mac 6.1.7. Set its `A` and `B` paths to the matching private map inputs before use. See [PORTING.md](PORTING.md). None of these SDK/map tools should be run against a working project or used to infer compatibility with another Bitwig version.
