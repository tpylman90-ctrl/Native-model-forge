# Foundation milestone 06 — draw a region, bend its edges, extrude

## User-driven interaction

The tested v0.6 rectangle/oval sliders worked, but the intended experience is drawing a selection directly on the model: place a point on a face, drag to its opposite corner, change boundary lines into curves, then manipulate the bounded area. This change replaces slider-first creation with an optional gesture-first workflow.

1. Open a generated box and choose **DRAW AREA** (the toolbar or face tools). Drawing mode temporarily takes over touch input so the camera does not orbit during the drag.
2. Touch a visible face, drag across it, and release. A preview region follows the finger in the face's projected UV space. A tiny drag is rejected; the region uses safe 15–85% dimensions and stays within face margins.
3. The drafted region is saved as one editable native face feature with zero extrusion depth. It stays highlighted and Undo/Redo can recover it.
4. Four **cyan edge midpoint handles** adjust the four curved edges independently. Each edge is a quadratic Bézier section with a signed bulge parameter; the shape can be concave or convex within bounded limits. Lifting a finger commits that curve to the native document.
5. Drag the center **PULL** handle or enter signed metres to extrude or recess the same selected region. The native geometry kernel generates the border, walls and cap with 32 boundary samples.
6. Repeat on other original box faces; edits coexist. Save GLB to export real triangle geometry; the app's private native draft retains editability and recovers it on restart.

## Compatibility and limitations

- The native document migrates `MFORGE_DOC` v1–v4 into v5. Four edge parameters per face are stored with the prior kind, dimensions, offsets and depth.
- `versionCode 10` uses the same signed `com.modelforge.nativeeditor.dev` package as v0.6, so it is intended as an in-place update.
- This is not arbitrary freehand sketching or CAD splines. It is one drawable, four-sided Bézier-outline region per original face, with four independently bendable sides and one extrusion. No multiple disconnected regions on a single original face, no automatic Boolean stacking, and no imported-GLB face editing.
- Face projection is currently computed from the existing generated mesh and original face corners. Tests must check complex perspective angles, small hit targets and strong curves.
- Strong curves near face edges are bounded to avoid crossing the host face, but interactions between deep extrusions on adjacent faces are not yet fully tested for self-intersection.

## Physical acceptance

Install over v0.6 without uninstalling. Tap DRAW AREA, drag from one corner to another on TOP and confirm the yellow highlight stretches with the finger; release. Drag each cyan midpoint to bend a side in and out, then PULL +0.25 m. Draw a different region on FRONT and recess -0.15 m. Orbit to ensure both remain. Test undo/redo, reopen draft, save/reload GLB, old orbit joystick, preset rectangle/oval, imported preview and whether tap-to-draw ever triggers unwanted orbit.
