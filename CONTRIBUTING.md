# Contributing

Native Video Track is maintained by Amo Nova. Contributions are welcome under the project's MIT license; retain third-party attribution when reusing code.

Start with [BUILDING.md](BUILDING.md), [ARCHITECTURE.md](ARCHITECTURE.md) and [HANDOFF.md](HANDOFF.md). Source checks need only Python 3.11 or newer:

```text
python tools/check_repository.py
```

Full builds require the exact locally supplied Windows Bitwig 6.1.7 SDK and pinned dependencies. Never commit host JARs, transformed host classes, player binaries, local build configuration, real projects or media. Build outputs are ignored by Git.

Keep synchronization and editing changes separate from packaging or host-version upgrades. Describe the triggering action, previous behavior, new behavior and evidence in each pull request. Run the relevant tests on a disposable host and synthetic project; the historical acceptance drivers can edit and close the only running Bitwig instance.

For timing regressions, report the codec, frame rate, GOP structure, project tempo map, edit sequence and whether playback was running. Provide a synthetic reproducer where possible. Software playback clocks alone do not establish physical audio/display latency.

Preserve stable mod ID `video-preview`, project asset identities, collected relative paths and backward-compatible metadata. Do not remove version guards to claim support for another host. Windows 6.1.0 and macOS require actual adaptation and validation.

Release mechanics and acceptance gates are in [RELEASING.md](RELEASING.md).
