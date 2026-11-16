# Releases and Patchbay Launcher

Repository: [Amo-Nova/native-video-track](https://github.com/Amo-Nova/native-video-track).

The `0.5.0-dev.3` source introduces repository metadata and Launcher packaging. Video behavior is based on `0.5.0-dev.2`. It is a development build for **Windows x64 / exact Bitwig Studio 6.1.7**, using Patchbay `0.36.0-dev.14`. macOS and other host versions are not supported.

See [VALIDATION_LAUNCHER.md](VALIDATION_LAUNCHER.md) for completed checks and the remaining native reopen gate.

## Package contract

- Keep the technical ID and top-level directory `video-preview` stable. The public display name is Native Video Track.
- The default branch exposes `video-preview/mod.json`. Source and built manifests share all metadata; Modkit adds the built hooks and targets.
- Name the installable asset `video-preview-<version>.zip`. It contains exactly one `video-preview/` directory, the built helper JAR, resources, source and notices.
- Attach it to a published GitHub Release, optionally marked prerelease. Drafts and GitHub's automatic source ZIP are not installable catalog entries.
- Keep checksums outside the MOD ZIP. Do not use a leading `v` or `+build` in the asset version. Release tags may use `v`.
- The checked Launcher 0.9.2 does not filter by OS/architecture. Do not publish a Mac asset under the same ID/version and expect platform selection.

The declared framework versions preserve the existing tested set: Actions and Menus `0.8.0-dev.1`, Panels `0.2.0-dev.2`, Settings `1.0.0`. Do not remove them merely because some imports are indirect.

## Build and validate locally

```text
python tools/check_repository.py
python tools/build_source.py mod
python tools/package_launcher.py
python tools/build_source.py prepare
```

`package_launcher.py` produces a local candidate and a SHA-256 file under `releases/launcher/`. Local packaging is not a declaration that all public-release gates have passed. `python tools/build_source.py all` separately builds and verifies the persistent EXE route; it does not install it.

Before publishing an installable asset:

1. Validate the candidate with the actual Launcher archive checker and installer. Exercise catalog selection, dependency resolution, update and failed-install recovery in temporary directories.
2. Prepare against a disposable **original** compatible host. Verify import, multi-video playback, scrub, split/undo, save, normal exit, reopen and continued editing. Check layout settings, collected media and disable/re-enable recovery.
3. Record what was tested, exact dependency versions and remaining limits. Do not present historical `VALIDATION_*.md` reports as a new runtime acceptance pass.
4. Supply complete redistribution notices and matching source/build materials for every bundled third-party component. The pinned private mpv archive currently contains a manual and image, but not a complete corresponding-source bundle; the public binary gate is pending. Source publication is independent of this gate.
5. Publish the immutable candidate and its checksum. Verify the public download and manifest against the locally tested files.

The player-bearing EXE has the same third-party release gate. Do not upload Bitwig's SDK, map-generated host overlays or user projects as release assets.

## Launcher installation route

After a validated MOD asset is published, add source `Amo-Nova/native-video-track` in Patchbay Launcher, with file filter `video-preview-*.zip`. Select Native Video Track and its framework dependencies. Launch Bitwig through Patchbay. Reopen projects through the same route so the MOD is loaded.

Launcher discovers assets by stable MOD ID and selects the highest available version across enabled sources. Disable conflicting fork sources if they publish the same ID. Installing an update replaces the package; it must not delete project media. A failed staged installation must leave the previous package usable.

This source repository alone does not make the MOD appear as installable. Until an installable release exists, maintainers must build locally using the pinned dependencies.

## Persistent EXE route and switching

The separate Windows installer integrates prepared changes into a local compatible host and supports ordinary Bitwig launch shortcuts. See [integration/README.txt](integration/README.txt). This is different from loading through Patchbay Launcher.

Do not stack both routes on the same already-modified host JAR. Patchbay selects its map using the original JAR hash. To switch, save and normally close Bitwig, restore the original through the existing restore tool, verify it, then use Launcher; alternatively use a separate original installation. Never automatically replace an unknown or upgraded JAR.

Removing/disabling the MOD does not remove the project's `Video/` or `samples/Video/` data. Preserve those folders and project metadata so restoring the MOD recovers the video bindings.

## Upstream references

- [Polarity's repository blueprint](https://github.com/polarity/bitwig-mods)
- [Launcher packaging guide](https://github.com/polarity/bitwig-mods/blob/main/docs/patchbay-launcher.md)
- [Launcher 0.9.2 implementation](https://github.com/arius77R/patchbay-launcher/blob/v0.9.2/src/Mods.java)
