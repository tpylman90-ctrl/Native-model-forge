# Hovercraft-ready modeling plan

## Current shipped milestone v0.9

Import mesh components from GLB, search and select them, relocate/rotate/resize using node transforms, insert modular base shapes, undo/redo, and export an intact modified GLB. No network required. This is the first stage at which a real, existing hovercraft can be incrementally assembled on the phone.

## Required for real vehicle surface editing

1. **Import/edit mesh structures:** Decode glTF POSITION/NORMAL/index buffers including interleaved, separate, quantized and common extension cases. Create an indexed editable mesh with stable vertex/edge/face IDs; preserve transforms and materials.
2. **Direct surface controls:** Camera raycast to select an imported triangle and connected panel, with touch-sized handles. Selection preview should highlight the actual target, not a duplicate UI sticker.
3. **Body panel construction:** user draws region → resizes it → bends/corners → depth/inset → bevel → weld/boolean into main shell, with anti-self-intersection checks.
4. **Hard-surface quality:** chamfer/fillet, bevel profiles, weighted normals, support loops, symmetry, constraints, curvature inspection and surface continuity feedback.
5. **Model structure:** independent parts, pivot placement, duplication, symmetry, grouping, hierarchy and per-object visibility. Reusable aftermarket and futuristic body-kit templates that are genuinely engineered, not arbitrary cubes.
6. **Workflow safety:** imported asset snapshots, editable offline project package (not just GLB), crash recovery, viewport/render fidelity, precise units/snap, and no-change preview before commit.
7. **Performance:** memory budgets, incremental mesh updates, LOD and batching. Test on budget ARM64 Android for complex 150–300 component hovercraft GLBs.

## Universal interaction architecture

For every advanced operation: **Choose goal → Find part → Select region → Preview → Adjust precision → Apply → Undo/Redo**. Use human-friendly labels but expose numeric dimensions for experts. Never hide irreversible changes behind a drag gesture. A dedicated advanced tab can show low-level topology terms without forcing newcomers to understand them.

## Test assets

Use known-good independently produced hovercraft GLBs from the real production repositories and a tiny, deterministic multi-component glTF fixture. Record number of parts, original and exported GLB size, material count, number of mesh primitives, change to selected node only, load in third-party viewers, and measured FPS. Preserve the untouched input as an immutable baseline.
