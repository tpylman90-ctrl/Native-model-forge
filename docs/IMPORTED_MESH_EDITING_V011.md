# Imported GLB vertex editing — v0.11 alpha

## Why

The original `EDIT CRAFT` flow could only reposition, rotate or resize entire imported mesh nodes. Car panels need editable geometry rather than primitive-only modeling. v0.11 adds the first localized **actual vertex deformation** operation to the existing Android GLB workshop.

## Steps in the Android editor

1. OPEN GLB (ordinary glTF 2.0 binary).
2. EDIT CRAFT → SELECT & EDIT AN EXISTING PART → find a mesh component.
3. RESHAPE MESH → choose the specific triangle surface inside that part.
4. Specify a local-space brush **center**, **radius**, and **XYZ displacement**.
5. PREVIEW; rotate camera and inspect; APPLY, ADJUST or REVERT.
6. Undo/Redo in session; SAVE GLB; reopen to verify the new geometry.

The smooth influence function is `1 - smoothstep(distance / radius)`, giving the strongest move at the center and zero at the radius. Geometry is changed in local coordinates before the node transform, and area-weighted normals are regenerated.

This does not add paint-style touch selection yet. It is a small, safe editable-mesh infrastructure milestone, not professional Blender-equivalent modeling.

## Data safety

- Position and normal attributes are written to **new buffer views and accessors** in a copy-on-write operation; the original binary remains intact.
- Shared mesh nodes are separated by cloning the affected mesh object before the edit.
- Existing materials, texture references, UVs, other mesh primitives, hierarchy, scene nodes and binary source prefix remain unchanged.
- A complete GLB encode succeeds *before* a checkpoint is committed. Rejected edits leave the document and previous undo history untouched.
- JSON + binary length checkpoints work because previous binary data is never rewritten. Undo, redo and save/reopen are tested.
- Unsupported surfaces (morph targets, skinned/animated nodes, tangents requiring regeneration, compressed views, sparse positions, non-triangle primitives) are rejected rather than silently damaged.
- Vertices are limited to 150,000 per surface; 80 MB maximum GLB. Tangent-space meshes should be processed in Blender until a proper tangent recalculator exists.
- A user should retain an unmodified source GLB because saved topology edits are not yet non-destructive modifiers.

## Known boundaries and next milestones

1. **Direct touchscreen selection**: visual brush circle on selected mesh and accurate camera ray-to-triangle mapping.
2. Face/edge/vertex selection and stable undoable multi-selection, including separate named groups and material regions.
3. Surface operations: edit cage, extrusion, inset, bridge, bevel, loop cuts, smoothing/crease control, symmetry and non-destructive modifiers.
4. Normal-map compatibility and imported meshes with morph targets, Draco/meshopt and skinned rigs.
5. Cross-device shared document history and the same full imported-GLB geometry commands in AI Studio's browser interface.
6. Quality targets: controlled automotive panel fit/finish on a legitimately licensed vehicle model, with before/after wireframe and curvature inspection.

Use only assets whose licenses explicitly permit modification and redistribution. Free download alone is not a license.
