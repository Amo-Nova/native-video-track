# Notes from the contributed Mac port

The supplied handoff describes a Windows `0.5.0-dev.8` → macOS `0.5.0-dev.14` port. These notes describe that implementation; they are not a claim that the current repository's Windows and Mac editions have identical behavior.

## Platform substitutions

| Windows boundary | Supplied Mac implementation |
|---|---|
| Named-pipe mpv IPC | Unix domain socket using `SocketChannel` and `UnixDomainSocketAddress`; short `/tmp/bwv-<pid>-<id>.sock` paths avoid the macOS socket-path limit |
| Cross-process native preview embedding | mpv's own floating window, `--ontop` and launch-time `--geometry`; no four-edge docking |
| Player executable | Separately installed `mpv`; `MpvBin` resolves an explicit override, optional package resource, Homebrew locations, then PATH |
| Windows local application data | `~/Library/Application Support/BitwigVideo/` for the supplied persistent route |
| Native host names | The Mac 6.1.7 map and exact Mac JAR; the Windows host hash is not reused |

`BITWIG_VIDEO_MPV` has highest priority in the actual resolver, followed by the package's `player/mpv`, `/opt/homebrew/bin/mpv`, `/usr/local/bin/mpv`, then PATH. A Homebrew executable is used in place so its linked libraries remain available. The MOD publishes no player binary.

## Preview lifecycle

- Hidden: disconnect and close the mpv preview process.
- Visible timeline gap: retain the player and clear video to black; restore video on re-entering content.
- User closes the mpv window: a clean unrequested player exit hides the preview rather than immediately reopening it.
- Import: keep the preview closed by default. Explicit Video actions show/hide it; `preview.autoshow` is an optional local setting.
- Geometry: applied when mpv starts. Reset clears saved geometry and restarts the preview. Continuous persistence of every manual resize was not independently validated during this import.

## Host mapping

`compose_remap.py` composes Windows 6.1.7 → Windows 6.1.3 → macOS 6.1.7. It retains duplicate alias rows and reports ambiguous member mappings. The supplied notes identify changes including `epD` → `epE`, `TKq.dD_` → `dC_`, `df.lZ_` → `lY_` and `Jpp.lq_` → `lp_`.

The supplied built manifest contains 10 hooks and two non-public access entries. It declares `architecture: universal` because the Mac host uses a shared Java archive; only Apple Silicon execution was reported. This declaration is not evidence of an Intel runtime test.

## Reduced feature set

The supplied edition removed the control/settings window, floating control toolbar, marker-management UI, manual AV separation, manual audio binding, relinking, timebase switching, frame-step controls, import cancellation controls and manual offset selection. Drag import, automatic audio extraction/binding, absolute-time behavior and project media collection remain.

These differences are preserved for this publication. Adding back Windows interfaces or implementing a shared cross-platform backend requires a separate change and Mac runtime validation.
