# Foundation milestone 03 — on-model handles (v0.4)

## Scope

First real on-model direct shaping on a generated profiled box, not arbitrary GLB editing. Three handles are projected from the actual top surface of the generated native model: LEAN at the top center, WIDTH on the positive X top edge, LENGTH on the positive Z top edge. The same native C++ Document commits every edit and manages Undo/Redo.

## Interaction contract

1. Create or restore a generated box and tap **EDIT ON MODEL** in the bottom tools row.
2. Hide the tools for a full viewport. A touch within a 44dp radius of a visible handle captures the gesture. All other viewport touches fall through to Filament orbit/pan/pinch.
3. Drag along each projected axis. A live numeric percentage indicates the proposed parameter; lift the finger to regenerate the actual GLB and commit a single native undo transaction.
4. View handles track the camera and box proportions, and hide when the top surface is viewed from below. Toggles are disabled for imported GLB previews.
5. Tap the mode button again to return to ordinary viewing. Save/export and recovery remain unchanged.

The first version intentionally applies geometry on finger release rather than rebuilding Filament resources for every touch sample. It is a safe controlled editing experiment, not continuous deformation or CAD topology picking.

## Technical implementation

- Android overlay projects native box top handles using the exact bounding-box center and 2/max-extent normalization used by Filament ModelViewer's transformToUnitCube.
- Column-major camera view/projection matrices map unit-space points to Android pixel positions; projected tangent vectors convert the drag into scale or shift parameter changes independent of camera azimuth.
- All edits follow existing `NativeProject.reshape` and `saveWorkingShape` paths; no tool holds Filament renderer entities as the canonical model.
- Input interception is limited to hit-tested handle touch targets so the existing right orbit pad, swipe, and pinch pipeline is preserved.

## Test gate

1. Open a generated box, orbit above its top, show direct top edit handles.
2. Drag LEAN horizontally and observe a shifted top face after release.
3. Drag WIDTH along its projected axis and observe taper/flare; drag LENGTH similarly for depth.
4. Undo three edits one by one; redo; close app and relaunch to confirm latest geometry is recovered.
5. Test camera swipe away from handles, right joystick, pinch, and hide/reopen tools in edit mode.
6. Import QA-01 GLB. Confirm no top-shape editing is offered and materials remain present. Re-export and reopen.
7. Check whether handle positions, size, and readout are usable across zoom levels and landscape/portrait.

## Non-goals

This does not implement face ID picking, handle occlusion on arbitrary geometry, general extrude/bevel, persistent history, import editing, precision B-Rep/NURBS, or physical-device success without direct testing.
