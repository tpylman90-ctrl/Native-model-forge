# Foundation milestone 05 — multi-face regions (Model Forge DEV v0.6)

## Response to physical v0.5 test

The user confirmed direct face selection, raised extrusion and recessed extrusion on an ARM64 Android phone. The two shortcomings were that a second face edit replaced the first and the inset area was fixed to roughly 82% of the face. This milestone addresses both without disturbing tested camera/GLB paths.

## Implemented design

- One independently editable region on each **original box face** (front, back, right, left, top, bottom). A different face edit no longer deletes the previously edited region.
- Each region is either **rectangle** (four corners) or **oval** (32-vertex outline), and owns width/height of face (15%–85%), left/right and up/down offsets (relative position within remaining face margins), and a signed extrusion depth in metres.
- The FACE TOOLS drawer exposes two shape buttons, four sliders and the existing pull, step, exact depth, and remove buttons. Face selection previews the currently configured region outline. The tools become scrollable (capped at 43% of screen height), leaving room to see the object.
- The C++ kernel creates a border, extruded/recessed sidewalls, and cap from each face's original winding. It preserves the four original outer edges rather than inserting new boundary vertices (avoids topological T-junctions along adjacent unedited box faces). Rectangular region = 54 vertices; oval = 390; an untouched face = 6. The mesh is regenerated as one GLB on commit.
- Native document format `MFORGE_DOC 4` persists all six region definitions. v1–v3 read compatibility migrates the original v0.5 single rectangular extrusion to the corresponding face; native session Undo/Redo remains.
- Public DEV signing and the `.dev` package ID are pinned as before. `versionCode 9` is intended to upgrade the v0.5 DEV install without uninstalling.

## Physical test gate

1. Install v0.6 over v0.5 DEV. Confirm update works without deleting the last saved generated shape.
2. Make a raised **rectangle** on TOP at 40% width × 60% height. Select FRONT and make a shallow oval recess at 35% × 50%; verify the TOP raised region remains visible.
3. Select RIGHT and offset a small rectangular inset to the left/up using the position sliders. Confirm preview outline moves before EXTRUDE is pressed.
4. Inspect front/top/rear/side from multiple camera angles, and check for any holes, shading flips, distorted oval outlines, or missed selection taps.
5. Undo the last operation and redo it. Close/reopen the app and verify that all previously completed face regions are restored, not just the most recent.
6. Save GLB, reopen it as preview and confirm all edited regions were exported.
7. Confirm orbit joystick, pinch zoom, top width/length/lean mode, drawer hide/show and imported GLB preview remain functional.

## Current limits

- One region per original face, up to six in this version; not multi-polygon selections or repeated stacked edits on the same face.
- Oval is defined proportionally on the face (an ellipse), not a perfect equal-metre-radius circle.
- Extreme positive and negative features on neighboring faces may geometrically overlap; automatic self-intersection validation is not implemented.
- Face selection remains based on generated triangle projection and semantic original face IDs, not generalized imported mesh topology or precision CAD B-Rep identifiers.
- Export GLB contains the real triangle mesh and material, while the app's private draft retains editable parameters.
