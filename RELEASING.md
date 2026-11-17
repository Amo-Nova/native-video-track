# Releases and Patchbay Launcher

Repository: [Amo-Nova/native-video-track](https://github.com/Amo-Nova/native-video-track).

## Separate macOS edition

The contributed Apple Silicon `0.5.0-dev.14` edition is maintained under `macos/` and published as `v0.5.0-dev.14-macos`. Install `video-preview-0.5.0-dev.14-macos.zip` with **Mods → Add Mod…** in Patchbay. The source archive has the distinct name `NativeVideoTrack-Mac-Source-0.5.0-dev.14-macos.zip`.

The extra `-macos` suffix deliberately keeps both ZIPs outside Launcher 0.9.2/0.9.4's automatic asset-version pattern. This preserves the existing Windows `video-preview` catalog entry. The Mac package retains its tested internal ID `video-preview`; do not rename the asset to `video-preview-0.5.0-dev.14.zip` without first implementing and validating a platform-aware distribution strategy. Launcher 0.9.4 adds compatibility metadata, but its catalog still selects the newest asset per ID before that compatibility check.

Publication preserves the supplied helper JAR, all 15 runtime Java files, hooks and resource mappings. Only documentation, licenses, credits and package checksums are refreshed. Runtime testing was reported by Amo Nova with AUPhui's testing and contributions; import checks on Windows do not constitute a fresh Mac runtime pass. See [macos/VALIDATION.md](macos/VALIDATION.md). No player binary or persistent installer payload is published in this Mac release.

The remaining sections describe the Windows release contract.

The `0.5.0-dev.3` source introduces repository metadata, Launcher packaging and verified first-use player setup. Video behavior is based on `0.5.0-dev.2`, using the same decoder build. It is a development build for **Windows x64 / exact Bitwig Studio 6.1.7**, using Patchbay `0.36.0-dev.14`. macOS and other host versions are not supported.

See [VALIDATION_LAUNCHER.md](VALIDATION_LAUNCHER.md) for completed checks and their limits.

## Package contract

- Keep the technical ID and top-level directory `video-preview` stable. The public display name is Native Video Track.
- The default branch exposes `video-preview/mod.json`. Source and built manifests share all metadata; Modkit adds the built hooks and targets.
- Name the installable asset `video-preview-<version>.zip`. It contains exactly one `video-preview/` directory, the built helper JAR, resources, source and notices.
- The public MOD ZIP contains no third-party player binaries. `player/README.txt` describes the verified first-use download. Local EXE build output must not be uploaded as this asset.
- Attach it to a published GitHub Release, optionally marked prerelease. Drafts and GitHub's automatic source ZIP are not installable catalog entries.
- Keep checksums outside the MOD ZIP. Do not use a leading `v` or `+build` in the asset version. Release tags may use `v`.
- The checked Launcher 0.9.2 does not filter by OS/architecture. Do not publish a Mac asset under the same ID/version and expect platform selection.

The declared framework versions preserve the existing tested set: Actions and Menus `0.8.0-dev.1`, Panels `0.2.0-dev.2`, Settings `1.0.0`. Do not remove them merely because some imports are indirect.

## Build and validate locally

```text
python tools/check_repository.py
python tools/test_player_runtime.py --online
python tools/build_source.py mod
python tools/package_launcher.py
python tools/build_source.py prepare --mod-zip releases/launcher/video-preview-0.5.0-dev.3.zip
```

`package_launcher.py` produces a local candidate and a SHA-256 file under `releases/launcher/`. Local packaging is not a declaration that all public-release gates have passed. `python tools/build_source.py all` separately builds and verifies the persistent EXE route; it does not install it.

Before publishing an installable asset:

1. Validate the candidate with the actual Launcher archive checker and installer. Exercise catalog selection, dependency resolution, update and failed-install recovery in temporary directories.
2. Prepare against a disposable **original** compatible host. Verify import, multi-video playback, scrub, split/undo, save, normal exit, reopen and continued editing. Check layout settings, collected media and disable/re-enable recovery.
3. Record what was tested, exact dependency versions and remaining limits. Do not present historical `VALIDATION_*.md` reports as a new runtime acceptance pass.
4. Verify the public ZIP contains only the MOD, project source and notices. The player is obtained directly from its upstream release; verify the pinned download, extracted file hashes, offline reuse and failure recovery. Do not silently replace this with bundled media binaries.
5. Publish the immutable candidate and its checksum. Verify the public download and manifest against the locally tested files.

The separate player-bearing EXE still requires complete matching third-party redistribution materials before public release; its local archive currently does not supply those materials. This prerelease publishes only the MOD ZIP and its checksum. Do not upload Bitwig's SDK, map-generated host overlays or user projects as release assets.

## First-use player setup

`PlayerRuntime` obtains the unchanged shinchiro `20261111` Windows x64 mpv archive (34,181,485 bytes) from the pinned URL in `video-preview/package/player/README.txt`. It verifies the archive's SHA-256 before extracting only `mpv.exe`, `mpv.com` and `d3dcompiler_43.dll`, then verifies each file before activating the cache. Upstream installer and updater scripts are never run. This preserves the codec build used in previous video tests.

Setup runs on the existing media/preview workers, with progress in the preview/import status. It uses the host's existing Java HTTPS implementation and the system `tar.exe`, without changing global proxy or TLS settings. The final cache is `%LOCALAPPDATA%/BitwigVideo/player/20261111-bcde6b93cc/`; it is outside the replaceable MOD package and the project. Settings and project media do not live in this cache. Each process verifies the files on first use; subsequent requests reuse their verified file fingerprints.

Concurrent callers and processes share one download. A failed download, checksum, extraction or cancellation does not activate incomplete files or insert an import clip. Repeat the import after fixing the connection; an existing project's unavailable preview also retries. A complete cache works without networking. An update or disabling/removing the MOD leaves the cache and collected media in place. If a different machine has no cache, its first video use still requires Internet access even when the project media was collected.

## Launcher installation route

After a validated MOD asset is published, add source `Amo-Nova/native-video-track` in Patchbay Launcher, with file filter `video-preview-*.zip`. Select Native Video Track and its framework dependencies. Launch Bitwig through Patchbay. Reopen projects through the same route so the MOD is loaded.

Launcher discovers assets by stable MOD ID and selects the highest available version across enabled sources. Disable conflicting fork sources if they publish the same ID. Installing an update replaces the package; it must not delete project media. A failed staged installation must leave the previous package usable.

The release asset, rather than the source repository alone, makes the MOD installable in the Launcher.

## Persistent EXE route and switching

The separate Windows installer integrates prepared changes into a local compatible host and supports ordinary Bitwig launch shortcuts. See [integration/README.txt](integration/README.txt). This is different from loading through Patchbay Launcher.

Do not stack both routes on the same already-modified host JAR. Patchbay selects its map using the original JAR hash. To switch, save and normally close Bitwig, restore the original through the existing restore tool, verify it, then use Launcher; alternatively use a separate original installation. Never automatically replace an unknown or upgraded JAR.

Removing/disabling the MOD does not remove the project's `Video/` or `samples/Video/` data. Preserve those folders and project metadata so restoring the MOD recovers the video bindings.

## Upstream references

- [Polarity's repository blueprint](https://github.com/polarity/bitwig-mods)
- [Launcher packaging guide](https://github.com/polarity/bitwig-mods/blob/main/docs/patchbay-launcher.md)
- [Launcher 0.9.2 implementation](https://github.com/arius77R/patchbay-launcher/blob/v0.9.2/src/Mods.java)
