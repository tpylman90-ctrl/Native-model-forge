# Foundation milestone 07 — explicit draw, SIZE, curve, pull

## Origin

After confirming v0.7.1 curved outlines persist, the physical tester reported the workflow **skipped the size part**. The existing draw gesture set the initial dimensions on finger release but immediately exposed curve and pull handles, with no direct way to stretch the four original corners after drawing.

## v0.7.2 behavior

- Tap **DRAW AREA**, touch a point on a generated-box face, drag to the opposite point and release. The outlined region is saved through the native document, entering **SIZE** automatically.
- **SIZE** shows four large gold corner handles; drag any corner to stretch the region while its diagonally opposite corner remains anchored. Width and height update as you drag, capped to the existing 15–85% allowed relative dimensions, with offsets clamped to keep the region on its host face.
- The on-screen **NEXT: CURVE** button (visible even with the bottom tools hidden) enters **CURVE**, showing four cyan edge handles. Drag these to bend the four sides independently, retaining the newly selected width and height.
- **NEXT: PULL** enables the white center handle to raise or recess the actual selected region, or enter signed metres in the tool drawer. **EDIT SIZE** cycles back so the region can be resized after bending or extrusion.
- Every committed resize remains a native document transaction, with Undo/Redo, project recovery and real GLB output. Existing curve parameters and depth survive resizing. Negative bulge limits are adjusted when necessary so resizing does not push a curved edge outside the face.
- The established orbit joystick, tool panel, top shaping handles, face selection, imports and exports are not intentionally changed.
- DEV package ID and public development signing certificate are unchanged; Android versionCode is 12.

## Phone checks

1. Install v0.7.2 over v0.7.1 without uninstalling, confirm the previous editable draft restores.
2. Create/open a generated box and tap DRAW AREA. Touch near one top-face corner and drag across it; see a growing highlighted area.
3. Release. Gold corner handles must appear, **not cyan curve handles**. Drag the upper-right gold corner wider, then another inward. The selected region should keep the chosen dimensions after each release.
4. Tap NEXT: CURVE. Gold corner handles disappear; four cyan midpoint handles appear. Bend one side, then another; confirm the curves persist.
5. Tap NEXT: PULL. The white handle appears; raise +0.3 m, then EDIT SIZE and adjust width again. The extrusion and bend should remain, subject to safe boundary clamps.
6. Undo/Redo these edits, close/reopen, and export/reimport the GLB to confirm shape geometry.
7. Test another face, drawer visibility, normal orbit gestures away from handles, and imported GLB preview.

## Scope boundary

This stage adds **four-corner bounding box resize** to the four-edge curved region model; it does not introduce independent moving of each vertex to non-rectangular quadrilaterals, arbitrary spline point insertion, multi-region per face, B-Rep or editing imported GLB topology. Those remain future modeling milestones.
