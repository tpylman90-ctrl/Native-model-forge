# Foundation milestone 04 — generated-box face selection and extrusion

## Implementation scope (v0.5 DEV)

This is the first real semantic face-edit operation in the Native Model Forge editor, not a Blender-style generalized mesh editor.

- **Tap-to-select:** In FACE MODE the editor projects the current generated mesh triangles through Filament's current view/projection matrices, tests tap position against projected triangles and chooses the nearest visible surface. The base six faces have semantic IDs (front/rear/right/left/top/bottom). Imported GLBs are not made editable.
- **Selection feedback:** The selected face is shaded and outlined. A white PULL handle is drawn on its cap/face, tracking the model through orbit. Off-handle viewport gestures remain the existing Filament camera controls.
- **Face extrusion:** Positive depths create a raised inset panel; negative depths create a recessed panel. The C++ kernel preserves the five unchanged faces and replaces the selected face with an inset border, four walls and a translated cap. This is **real mesh geometry** exported to GLB.
- **Editing controls:** Drag PULL vertically and lift to commit, enter exact signed metres and tap EXTRUDE, or use +/-0.10 m steps. The geometry is rebuilt on commit, not at each gesture frame.
- **Source of truth:** One native parametric extrusion feature per box, storing face ID and signed depth. Existing dimensions and top-profile remain in the same native Document. Native Undo/Redo and app-private recovery apply. Project format increases to `MFORGE_DOC 3`, with v1 and v2 migration.
- **Signer:** Same public TEST-ONLY dev signing identity and `.dev` package as v0.4.1, versionCode incremented to 8. No uninstall intended from that baseline.

## Acceptance tests

1. Install v0.5 DEV over v0.4.1 DEV. Verify Android upgrades the existing DEV app (do not uninstall), and that the last edited box restores.
2. Open a generated box, tap SELECT FACE, rotate camera and select TOP/FRONT/LEFT/RIGHT/REAR/BOTTOM in turn. Verify only visible faces select, and selected polygon remains outlined while orbiting.
3. On TOP: drag PULL upwards to make a raised feature. On FRONT: enter `0.25` m and tap EXTRUDE. Note that first extrusion is replaced (one-feature limit).
4. Select a face, enter `-0.15` m and tap EXTRUDE. Confirm a visibly recessed inset, not a black hole or doubled face.
5. Undo/Redo several edits, close/reopen app, confirm latest extrusion retained. Saved GLB should reopen with the actual new geometry.
6. Check right joystick, pinch, touch orbit away from selected handle, top-width/lean handles in old mode, collapse drawer, and import QA-01 craft as preview.
7. Share screenshots from front, side and top along with any missed taps, unexpected camera turns, crashes or clipped geometry.

## Architectural limitations

- One editable procedural box object and **one** semantic extrusion feature; further face extrusions replace rather than stack.
- Selected edges, vertices, bevels, multi-face history, and editing imported GLBs are **not** implemented.
- Face picking is calculated from the generated display mesh; it is not a permanent CAD B-Rep face identifier or general Filament GPU picking layer.
- Narrow/recessed shapes may need additional self-intersection prevention in later versions. Inward depth is clamped to 40% of the minimum dimension and outward depth to 200%, but this is not a general mesh self-intersection proof.
- Production-quality on-device success is pending physical test even when CI passes.
