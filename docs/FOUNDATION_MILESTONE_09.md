# Foundation milestone 09 — practical hovercraft GLB component workshop

## Goal

Make Model Forge immediately useful for existing Neon Foundry hovercraft projects on Android, not only generated starter boxes. Respect the user's central design principle: complex actions should be staged, understandable, reversible and previewable.

## What v0.9 implements

- **Open a real GLB:** Android Files, unchanged Filament PBR viewer. A defensive glTF 2.0 GLB parser checks boundaries, one embedded BIN buffer, supported scene nodes and file size (80 MB maximum). Unsupported files remain preview-only, with a clear explanation; they are never treated as editable just because they render.
- **Find individual components:** A searchable list of visible scene mesh nodes, using existing node/mesh names such as canopy, wing, intake, body panel, thruster. The original hierarchy and mesh/material references remain unchanged. Nodes controlled by animation, skinned mesh nodes, and GPU-instanced parts are excluded from the safe component editor.
- **Guided part editing:**
  1. Choose a named component;
  2. Choose Move, Rotate or Resize;
  3. Enter 3 axis values, in metres/degrees/factors respectively;
  4. Preview on the entire craft;
  5. Apply, Adjust or Revert.
  Geometry transformations are stored as glTF node matrices rather than re-quantizing original vertices. The pivot comes from the mesh's position accessor bounds where available.
- **Build components on the craft:** Add simple standalone editable armor panels, thruster pods, fins, intake blocks and fairing blanks, generated as actual triangles by the existing C++ geometry kernel. Preview their initial insertion, then choose placement and size. This is a modular construction base—not a detailed, finished vehicle part generator.
- **Undo/Redo:** Lightweight deep-copied JSON checkpoints plus append-only binary geometry; does not copy the original 80 MB BIN data on every edit. Existing native generated-box Undo/Redo remains separate.
- **Export:** Save the *entire edited GLB* back to Android Files, preserving original buffer views/vertex bytes, textures, material metadata, extensions and scene nodes, with appended part geometry and new transforms. Exported GLBs can be reopened in the same workshop.
- **Compatibility:** Reuses the pinned public DEV signer and versionCode 14, retaining in-place installation over v0.8. Existing native box workflow and .mforge project format are unchanged.

## Important boundaries

- This edits **separate mesh components and added accessories**, not the vertices, normals, topology or control loops of an imported body panel. It cannot directly smooth a nose, create a CAD chamfer, cut a recess or weld two imported shells together yet.
- External-bin, multi-buffer and unsupported scene models remain preview-only. Animation/skinning nodes are protected. It is not a complete glTF editing standard implementation.
- The editor uses an in-memory edit history. **SAVE GLB** explicitly before switching files or exiting. Native .mforge is currently only for generated boxes; mesh-node edits persist inside the exported GLB itself.
- Finger tap picking for every individual imported triangle is not yet included; parts are selected reliably by search/name. On-model imported part handles are the next UI step.
- The original GLB's BIN bytes are not modified, but JSON nodes and buffer length are serialized using Gson. Validate finished GLBs in Godot/Blender before treating them as production assets.

## Phone test

1. Install v0.9 over v0.8, without uninstalling. Confirm the existing box workflow, camera joystick, top handles, face tools and GLB export continue working.
2. OPEN GLB an actual hovercraft with multiple separate mesh nodes. Confirm the status displays editable component count and EDIT CRAFT becomes available.
3. Select a named body-kit component using the search box; choose MOVE X = +0.15 m. Preview, inspect the change, then Apply.
4. Select the same part and Resize X=1.10, Y=1.00, Z=1.00. Confirm only this component changes width.
5. Undo, redo and confirm the expected component returns or changes without damaging unrelated parts.
6. Add an Armor Panel or Thruster Pod, confirm preview, then position it. Verify existing materials on the rest of the hovercraft remain unchanged.
7. SAVE GLB, reopen the edited file and confirm adjusted components, newly added geometry, and material colors persist.
8. Open that exported file in Godot or Blender and inspect glTF integrity. Capture before/after screenshots and report any missing part names, unexpected transformations, loading failures or performance issues.

## Next engineering milestone

Generalized part hit testing/selection, editable imported indexed mesh topology, non-destructive panel deformation/bevel, and multi-object project recovery. Keep guided stages with explicit preview/commit, cancellation, validation and Undo/Redo.
