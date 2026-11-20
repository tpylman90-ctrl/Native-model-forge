# Model Forge — Full Studio Product and Engineering Roadmap

**North star:** A genuinely capable modeling studio that happens to fit in your pocket.

**Non-negotiable product values:** native/offline-first Android, free and legal toolchain, dependable project ownership, genuine precision rather than low-quality placeholders, optional advanced tools, clean one-hand/two-thumb gestures, reversible modeling operations, user consent for export/sharing and optional AI.

## Interaction pattern for EVERY complex modeling action

1. **Choose goal:** use plain-language verbs (Add, Resize, Cut, Bend, Duplicate, Smooth) instead of making users hunt through jargon.
2. **Select subject:** tap a visible object, face, edge or vertex; show a clearly legible outline and label.
3. **Preview:** use geometry-linked translucent/highlighted controls with finger-size hit targets. Never commit irreversible changes during a preview drag.
4. **Adjust:** direct handles for novice work, numeric units/snap for precision, opt-in advanced panels.
5. **Apply:** one native document transaction; preserve original geometry and consistent undo/redo.
6. **Verify:** cheap diagnostic, check output, automated geometry tests, device test; make Cancel/Back as obvious as Next.

## Capability ladder

### A. Foundation (implemented through v0.8; device validation pending for latest)

- Stable PBR GLB viewer, joystick orbit, pinch, tool drawer, generated box and shaping handles.
- Draw/size/curve/pull face regions independently across the six original faces.
- Optional eight-stage Studio Guide, four base blanks, native opposite-face mirroring, editable .mforge Save/Open, Model Check, native Undo/Redo and local recovery.
- The actual native project remains a parameterized single-box document. Imported GLBs are preview-only.

### B. Next essential modeling operations — highest priority

- **Multi-object scene:** Add/duplicate/move/rotate/scale independent primitives; object list, rename, hide/show, grouping, instancing, parent/child transforms. Per-object stable IDs with scene-wide undo.
- **Editable mesh topology:** Indexed vertices, edges, faces with stable selection mapping; connected-component selection; proper mesh welding, normals/tangents. Separate view selection from geometry changes. Imported GLB editable only after conversion with explicit warning about lost modifiers.
- **Generalized direct sketch:** Multiple areas per face, freeform curve points, corner movement, snap/grid/angle constraints, inset/extrude/bridge/knife with correctness tests and non-self-intersection alerts.
- **Modifiers:** non-destructive bevel/chamfer, mirror, subdivision, weighted normals, solidify, array and Boolean union/difference/intersect; each with guided previews and exact distances.
- **Project format:** versioned, portable `.mforge` archive with referenced assets, saved multi-object layers, external texture copies when consented, history/feature tree and crash-safe checkpoints.

### C. High-quality surface and detailing

- **Hard-surface workflow:** editable control loops, curvature visualization, fillets, surface continuity feedback and dimension-driven panels; model-mode and material-mode separation.
- **Materials/UVs:** PBR presets, per-face/material slots, local texture painting, smart unwrap and packed ORM, physically consistent lighting presets.
- **Sculpting:** multi-resolution brushes with pressure/touch fallbacks, masks, symmetry, smoothing and retopology tools.
- **References and measurements:** photo boards, multi-view alignment, transparent tracing, perspective calibration, world/unit grids, snapping and calipers.
- **Performance:** screen-space brush hit testing, draw-call budgeting, lower-resolution previews and explicit apply quality, ARM64 profiling on modest Android devices.

### D. Engineering-grade modeling

- Independently evaluate Open CASCADE Technology or a legally compliant alternative under Android ARM64, library size, memory and license constraints.
- True solid B-Rep/NURBS construction, robust Boolean/fillet/shell, constraints and dimensional sketches, STEP import/export.
- Dual representation: preserve analytic CAD as document authority, produce triangle meshes only for preview/GLB export. Do not silently replace accurate surfaces with approximate triangulated editing.

### E. Creator platform and optional AI

- Asset catalog and offline reusable libraries, documented file licenses, versioning and remix attribution.
- Explicit opt-in upload/gallery/community features after accounts, security, privacy and moderation are implemented and tested.
- Forge AI command adapter using versioned, permissioned preview/apply/undo modeling operations; app still fully useful without network or AI.
- Code/Python-like recipes only inside a sandbox with permissions, clear runtime limits and deterministic replay.
- Secure production Android signing with a separate private key, installation migration plan and release QA.

## Release gates for every milestone

- Native and Android compilation/tests pass; pinned DEV signer remains stable until production.
- Previous project versions reopen and a new project can survive app termination.
- Undo/Redo and cancellation never leave native state out of sync with viewport.
- Verify GLB output geometry in another viewer, not just the internal viewport.
- Test on physical Android at least one low/mid-range phone; measure performance, memory, finger target size, dark/light readability and portrait/landscape.
- Feature documentation must distinguish **Implemented**, **CI verified**, **phone verified**, **planned**, and **experimental**. A successful build is not proof of a smooth modeling experience.

## Product metric

An absolute beginner should create, reshape, add a curved/recessed detail, mirror it, save an editable project and export a valid GLB without a video tutorial. Advanced creators should be able to repeat those operations with precise measurements and a transparent history. Architectural progress is judged by those user journeys, not by button count.
