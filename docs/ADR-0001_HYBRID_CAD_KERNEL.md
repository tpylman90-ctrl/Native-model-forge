# ADR-0001 — CAD-grade geometry and an approachable Android editor

- Date: 2026-11-17
- Status: **Product requirement accepted; kernel selection and integration remain unverified**
- Decision owner: Model Forge project

## Non-negotiable direction

**Professional geometric quality and beginner usability are equal requirements.** Model Forge should support high-quality curved hard-surface and mechanical modeling without forcing users to understand complex CAD terms.

A triangle mesh may look excellent at high resolution, but it approximates circles, fillets and smooth parametric surfaces. A CAD B-Rep (boundary representation) retains mathematically defined surfaces and topology for precise re-editing. Mesh-only subdivision and dense geometry cannot replace that functionality.

### Canonical hybrid document

1. **CAD objects:** canonical analytic/B-Rep/NURBS geometry, double-precision parameters, feature history, stable semantic IDs.
2. **Mesh objects:** editable polygon topology for imported GLBs, game assets, localized sculpting and remeshing.
3. **Display cache:** adjustable tessellation generated from canonical geometry, sent to the existing Filament renderer. Display triangles are not the source of truth for CAD objects.

A single native document owns the object tree, transforms, selection, materials, operation history, save/recovery and user permissions. Easy and Advanced UI modes expose the same geometry commands. Keep the working C++ mesh core and Filament PBR renderer; do not rewrite them just to add precision CAD.

### Precision and interoperability

- **Native editable project:** store object kind, operation graph and parameters, CAD source geometry where applicable, mesh assets, stable object IDs, units, revisions and materials.
- **GLB export:** tessellated visual/game model; not an editable CAD-solid file. Preserve GLB input as polygon data; never silently pretend its triangles reconstruct exact NURBS.
- **STEP export/import:** later CAD interchange for B-Rep solids after official kernel support is tested.
- **Convert CAD to Mesh:** explicit action, with preview and loss-of-editability warning. Keep original CAD object unless user explicitly discards it.
- **Precision:** use double-precision CAD values and deliberate unit/tolerance policy. A CAD kernel has failure cases; never promise mathematically perfect production tolerances merely because geometry is parameterized.
- **Topological identity:** B-Rep face/edge identifiers can change after Booleans and fillets. Track semantic features and ancestry; do not persist raw kernel pointers or assume stable face array indices.

## Recommended candidate — Open CASCADE Technology (OCCT)

**Research-backed candidate for a technical spike, NOT integrated into the app yet.**

- Official C++ CAD kernel: https://github.com/Open-Cascade-SAS/OCCT
- Booleans, fillet/chamfer, solids, lofts/curved surfaces: https://occt3d.com/dev/doc/overview/html/occt_user_guides__modeling_algos.html
- Official cross-compile instructions list Android arm64-v8a: https://occt3d.com/dev/doc/overview/html/build_upgrade__building_occt.html
- Tessellation at different linear/angular deviations: https://occt3d.com/dev/doc/overview/html/occt_user_guides__mesh.html
- STEP processor: https://occt3d.com/dev/doc/overview/html/occt_user_guides__step.html
- License: LGPL-2.1 with additional exception: https://occt3d.com/dev/doc/overview/html/occt_public_license.html

Review all license notices, transitive dependencies and linking/relink provisions before an APK is distributed. This is not legal advice. Official Android build documentation establishes a plausible path, **not** that the current Model Forge app is compatible or that performance and APK size are acceptable.

**Mesh complement:** https://github.com/elalish/manifold — Apache-2.0 triangle-mesh Boolean and manifold operations. Useful alongside OCCT for mesh work, but cannot preserve CAD NURBS/B-Rep definitions. Avoid confusing the two.

## Shared user experience — beginner action, professional result

| Friendly action | Underlying precise operation | Interaction |
|---|---|---|
| Create Body | Parameterized solid primitive | Touch size handles or type exact dimensions |
| Draw Profile | Lines, arcs, splines, sketch constraints | Finger-drawn curve with automatic snapping and editable dimensions |
| Push / Pull | Extrude / supported face offset | Drag arrow; preview; tap Apply or Cancel |
| Round Edge | Fillet/chamfer | Drag radius control; show failure if self-intersecting |
| Cut Recess | Cutter solid plus Boolean difference | Position cutter, set depth, preview subtraction |
| Mirror Shape | Symmetric feature construction | View symmetry plane and editable source side |
| Smooth Body Blend | Loft/controlled surface continuity | Select neighboring sections, preview tangent/smooth blend |
| Quality Preset | Adjustable tessellation, NOT geometry changes | Fast Preview / Studio / Export detail |

The editor must preserve novice-friendly verbs while supporting precise radii, measurements and continuity settings for advanced users.

## Engineering gates before describing this as implemented

### A — Host CAD kernel feasibility
- Pin an upstream OCCT release and dependency/license inventory.
- Headless C++ proof: create dimensioned box, extrude circular/arced profile, subtract recess, fillet selected edge, reject impossible radius without losing original.
- Validate volume/bounds, solid validity, mesh normals; generate coarse and fine tessellation from the *same* B-Rep.
- STEP export and round-trip readback against dimensional tolerances.
- Demonstrate repeated editing without re-fitting CAD curves to its own triangles.
- **Status: PASSED for host proof (Gate A)** in [GitHub Actions run 44147790084](https://github.com/tpylman90-ctrl/Native-model-forge/actions/runs/44147790084): OCCT 7.6.3 generated 3 mm fillets, recessed Boolean pocket, 552/3,056-triangle tessellations, STEP reimport error ~5.82e-10 mm³; STL-to-GLB cleanup yielded a watertight 3,048-triangle GLB. See [milestone evidence](FOUNDATION_MILESTONE_01.md). This is **not** evidence of Android OCCT integration.

### B — Native Android feasibility
- Separate CMake/NDK arm64-v8a cross-build CI job, initially manually triggered; no disruption to existing Android APK.
- Determine required OCCT modules, packed native library/APK size, cold/warm initialization, RAM peak, computation latency and device heat.
- Test on actual affordable ARM64 Android phone; set acceptance thresholds from measured baseline rather than invented numbers.
- Evaluate LGPL distribution compliance and dynamic library handling.
- **Status: NOT RUN.**

### C — Single command vertical slice
- Stable native CAD adapter and versioned command contract independent of Kotlin, view entities and AI.
- One CAD primitive with real selectable edge; Round Edge and Cut Recess preview, exact input, cancel, undo/redo and local save/recovery.
- Filament consumes updated generated mesh without owning CAD source.
- STEP and GLB exports validated independently, then reopened.
- **Status: NOT RUN.**

### D — Automotive-quality acceptance sample
- Dimensioned body with planar panel seams, symmetrical sides, recessed headlight, controlled edge radii and curved roof/hood transitions.
- Evaluate size accuracy, surface continuity, highlight fairness, silhouette quality and manifold correctness at different viewing distances.
- Benchmark on-device beginner success rate, selection reliability and input latency.
- **Status: NOT RUN.**

## Risk register

- OCCT Android build and runtime footprint may be large; profile before any packaging commitment.
- Boolean and fillet operations fail on difficult tangencies, tiny edges or self-intersections; operation must be atomic with understandable recovery.
- B-Rep topology naming, feature IDs and dependency recomputation are hard.
- Mesh-to-CAD reconstruction is not lossless, and CAD-to-GLB necessarily tessellates.
- CAD meshes need consistent material boundaries, normals, UVs and precise selection.
- Heavy computation must run off Android's UI thread; preview resolution can adapt to responsiveness.
- Legal compliance must precede distribution.

## Implementation sequence

1. Preserve and test today's v0.2 C++ mesh kernel and Android viewport.
2. Extend the local document/command design to explicitly distinguish CAD vs mesh objects before adding many topology-only features.
3. Prove OCCT host and arm64 build feasibility independently.
4. Ship a small, verifiable CAD vertical slice: exact round body edge + subtractive recess + STEP export + GLB preview, with save/recovery.
5. Expand to sketch constraints, loft/surface continuity, non-destructive history and hybrid modeling.
6. Keep usability tests, offline ownership and crash recovery mandatory at every stage.

This is an **architecture commitment, not a claim that CAD has been added to the APK**. Matching the experience and output quality of a mature CAD product requires iterative benchmarking and real-device validation.
