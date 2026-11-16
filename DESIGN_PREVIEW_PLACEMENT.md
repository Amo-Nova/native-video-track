# Preview placement design

Implemented in 0.5.0-dev.1. Runtime evidence and remaining display limits are in `VALIDATION_PREVIEW_PLACEMENT.md`.

The preview can occupy the top, bottom, left or right of the host workspace, or a floating window. Video clips continue to live in the native arranger; changing preview placement does not edit clips, rebuild audio or add a project undo entry.

## Controls and behavior

- A placement menu is available in the preview, settings and floating window. The selected mode is visible. The initial default is bottom.
- Docked modes occupy real workspace space and expose resizable splitters. Top placement does not cover transport controls. Narrow panels wrap the settings, marker and frame-step controls.
- Video keeps its aspect ratio with black unused space. Floating mode can move and resize, follows host visibility/minimization and is not globally always-on-top.
- Only one preview exists. Switching placement preserves project, transport and source target, reusing the render window and decoder session where possible.
- Closing the floating window only hides it. The Video entry or show-preview action restores it. Reset layout returns to the default bottom placement.
- Placement is selected from a menu. Automatic edge snapping while dragging is not implemented.

## Persistence and failure paths

Mode, per-edge size, visibility and normal floating bounds are machine-local settings. They persist across projects and restarts without storing monitor coordinates in the project file. A transferred project uses the destination machine's preferences.

Floating bounds are checked against available work areas. Invalid/off-screen bounds return to a visible primary-screen area, with usable minimum dimensions. Missing or invalid settings use defaults. A failed switch restores the last valid layout or bottom fallback without changing media or time data.

Empty projects, timeline gaps and missing media keep placement controls usable and do not show stale video from another project. Unsupported host display layouts fall back to floating mode with a reason.

## Implementation

The MOD wraps the workspace with four fixed native splitters while retaining the original arranger widget and view settings. Switching expands only the selected edge; it does not reconstruct the arranger or move unrelated layout nodes. The former bottom-tab registration is replaced by a Video visibility control, while existing show/hide actions remain available.

All docked modes share one Win32 render child. Floating mode reparents that child into a MOD-owned native window in the same process using `SetParent`. Tested switching preserved the HWND and mpv process. Layout preferences use BML local settings, outside document transactions.

Window clipping, suppression of old-pixel copies, old-region invalidation and out-of-bounds hiding remain active. Host layout changes stay on the UI thread; native messages and decoding remain off-thread. Source mapping and synchronization are not reset when placement changes.

## Acceptance scope

The lifecycle covers empty/new projects, importing AV, all five modes while stopped and playing, close/show, size persistence, save/reopen, invalid-coordinate recovery, editing/undo, tempo-locked media and markers, and media collection. Multi-monitor mixed-DPI behavior, modal interactions and full visual stress require additional real-device testing. Geometry and software-clock checks do not substitute for visual evidence.
