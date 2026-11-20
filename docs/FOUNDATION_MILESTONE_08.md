# Foundation milestone 08 — Guided Studio, mirror, editable project portability

## Product promise

A genuinely capable modeling studio that happens to fit in your pocket. Complex operations follow a clear **Choose → Select → Preview → Adjust → Apply → Undo** progression wherever possible. The user must never need to know triangulation, UVs, or CAD jargon to create an attractive editable object.

## Implemented in DEV v0.8

- **Optional Studio Guide:** 8 on-screen tasks: (1) base, (2) shape, (3) draw region, (4) size, (5) curve, (6) pull/recess, (7) mirror, (8) model check/save. The dismissible guidance card is beside the viewport and has an action, Back, Next, Close. All existing manual tools remain accessible; the guide uses the same underlying commands rather than a separate simplified engine.
- **Preset starting blanks:** plain block, hull blank, architectural block and small panel blank. These are parametrically editable profiled boxes, *not finished automotive models*. Starting another model requires explicit overwrite confirmation and recommends saving the current editable project.
- **Mirror detail:** native atomic operation for the six semantic faces, reflecting an existing region onto its opposite face. For front/back or right/left, local U flips (and left/right curve indices swap). For top/bottom, local V flips (and top/bottom curve indices swap). A destination feature is never overwritten without explicit confirmation. Undo/Redo reverts one command. C++ desktop tests cover all three axes, orientation, overwrite rejection, undo/redo and serialization.
- **Portable editable project:** Android Storage Access Framework Open/Save `.mforge`. This preserves six independent face regions, their four curve controls, base dimensions and extrusion, unlike GLB. Validate 1 MB input cap, native project magic and native parser; attempt to restore previous document/preview if load fails. Current crash-recovery draft remains local.
- **Model Check:** generated bounds and dimensions, triangle count, degenerate triangle and orientation tests, and shortcuts to two export formats. It is not a watertight/self-intersection proof.
- **Stable dev signer:** same publicly pinned dev-only certificate, `com.modelforge.nativeeditor.dev`, versionCode 13 so it installs over prior DEV APKs. NEVER ship this signer as production.

## Phone acceptance gate

1. Install v0.8 over v0.7.2 without uninstalling. Confirm the old draft appears and controls still orbit.
2. Tap **✦ STUDIO** (first toolbar button). Navigate all eight steps; confirm Back/Next/Close work and each action activates the expected modeling mode without blocking orbit outside handles.
3. Choose a hull blank from Step 1 and confirm warning appears before replacing any existing project. Verify 4.5 x 2.1 x 8.4 m and top profile remain editable.
4. On TOP, DRAW → SIZE → CURVE → PULL one region. Use FILES **SAVE PROJECT** to write a `.mforge` document.
5. In another editable base, **OPEN PROJECT**, select the prior file, and verify exact curve shape and depth restore. Save a GLB and reopen that as a preview: geometric appearance should remain but imported preview should not falsely claim editability.
6. Select a detail on RIGHT, press **MIRROR SELECTED DETAIL**, review LEFT as destination, confirm, and orbit both sides. Verify offset/curve orientation, Undo/Redo, saved project import, and unchanged edits on other faces.
7. Attempt mirroring onto a face with an existing region; verify an explicit overwrite warning. Cancel should leave model unchanged; confirm is a single Undo step.
8. Run **MODEL CHECK** and confirm actual dimensions, triangle totals and the available export choices match the visible model.
9. Test the existing top handles, orbit joystick, pinch, tool hide/show, basic rectangle/oval presets, camera reset, and imported GLB preview.

## Remaining limits

This is a meaningful foundation, not a completed Blender replacement. One generated box at a time, one region per original face, no arbitrary imported GLB topology edits, no multi-object hierarchy/assembly, no vertex-edge-face mesh operators, no bevel/subdivision/Boolean stack, no precision B-Rep/NURBS in the Android APK, no UV/material editing, and no cloud/community features. User data stays offline unless the user explicitly exports a file.
