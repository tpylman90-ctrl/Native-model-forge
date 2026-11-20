# Model Forge Native

**A genuinely capable modeling studio that happens to fit in your pocket.**

*Create anything. Anywhere. Together.*

A native, offline-first Android 3D creation studio designed for people who have never used a modeling tool **and** for expert creators. An accessible starting experience is a product requirement, not a simplified export format: Easy and Advanced modes will share one document, geometry engine, and high-quality viewport.

## Product principles (baseline, 2026-11-16)

1. **Make creation approachable.** A new user should be able to make, reshape and save a real 3D model without knowing terms such as topology or UVs. Clear labels and visible previews before hidden gestures.
2. **No ceiling on creativity.** Precision mesh operations, direct selection, high-quality PBR materials, modifiers and scripting will build on the *same* document and command format as Easy mode. Do not trap beginners in low-quality primitives.
3. **Designed for one phone.** Large touch targets, right-hand orbit control, collapsible tools, recoverable undo, offline GLB creation, and reliable import/export. Never assume a keyboard or desktop computer.
4. **Creator ownership.** Projects stay private and local unless their owner deliberately shares them. Editable project format and portable GLB are core. No paywall for opening and exporting user-owned work.
5. **Community with consent.** Future upload, gallery, remix, attribution and permissions will be opt-in. No public API or upload in this foundation; implement moderation and safety before launch.
6. **AI augments, never replaces agency.** Forge AI will eventually communicate through a versioned, permissioned modeling command interface. Explicit user approval precedes edits; optional training data requires opt-in. The editor remains useful without AI or internet.
7. **Ship measured improvements.** A feature is not finished until it passes native build, on-device visual/gesture review and import/export regressions. Never claim Blender parity before it exists.

## Quality commitment

**Easy to learn, professional in geometric quality.** The long-term architecture combines a precision CAD solid pathway (B-Rep/NURBS) with polygon editing and sculpting in one Android-native document and interface. CAD surfaces stay mathematical and editable; Filament previews regenerated display meshes. Open CASCADE Technology is a *candidate* undergoing feasibility study, not a shipped dependency. Read [ADR-0001: hybrid CAD kernel](docs/ADR-0001_HYBRID_CAD_KERNEL.md).

## Architecture

- **Kotlin / native Android Views**: first-phase accessible shell, create flow, local Storage Access Framework, right-side orbit control and retractable editing tools. Compose can be introduced later without coupling the document format to UI widgets.
- **Google Filament 1.69.3**: native PBR GLB viewport and orbit camera, never the source of truth for topology.
- **Native C++ (CMake / NDK)**: independent geometry layer. Current implementation generates shape-controlled triangle meshes through JNI. Planned hybrid architecture adds precision CAD B-Rep/NURBS objects alongside editable meshes, gated by Android resource/performance and license tests.
- **glTF/GLB boundary**: GLB is a triangulated interchange/export format, not the precision CAD source or fully editable project. Imported GLBs remain **preview-only in v0.2**. Future native project retains CAD and mesh objects, feature history, materials and commands; STEP interoperability will be added only after CAD kernel tests.
- **Command layer**: see [docs/COMMAND_PROTOCOL.md](docs/COMMAND_PROTOCOL.md) for the future shared API for touch, scripts and Forge AI.

## CAD foundation milestone 01 — verified desktop proof

The native C++ core now includes a **standalone hybrid project-document layer** (stable object IDs, polygon vs B-Rep reference kinds, revision validation, metadata serialization and in-session Undo/Redo). Generated-box shaping now uses the native document over JNI, including crash-safe recovery of the latest shape. CAD payloads and imported-model editing remain unimplemented.

A separate **Open CASCADE Technology (OCCT) desktop benchmark passed**: mathematically defined box with 3 mm fillets and a recessed Boolean pocket, STEP write/read, two display tessellation qualities, and a validated **watertight GLB preview**. See [milestone 01 results](docs/FOUNDATION_MILESTONE_01.md). **OCCT is not yet included in an Android APK.** ARM64 compilation, package size, phone performance and licensing remain open gates.

## v0.2 first shaping pass: implemented vs planned

**Implemented target:** native offline Android APK; high-quality Filament viewport; dimensioned box and native parametric top-face width/length/sideways-lean shaping; recalculated hard normals; bounded 40-step Undo/Redo for shapes during the current session; actual GLB mesh bounds derived from generated vertices; drag or use right orbit pad to inspect; pinch zoom; collapsible tools; import GLB preview and export generated GLB through Android Files. Core geometry unit tests and Android CI.

**Not yet implemented:** editing imported models, arbitrary vertex/edge/face topology, freeform sculpting, persistent undo history across restarts, Boolean/subdivision, Python execution, user accounts, uploads, or live community. Changing box dimensions with CREATE BOX resets the current shaping history; APPLY SHAPE changes dimensions and top profile with Undo/Redo. Buttons for future tools must not claim functionality they do not have.

## Foundation milestone 02 — native box project and recovery

Generated-box shape edits now use the C++ `Document` for stable IDs, atomic profile changes, and in-session Undo/Redo. The latest editable generated shape is saved in a crash-safe Android-private file and restored on the next launch. This is not a portable multi-object project archive; imported GLBs remain preview-only and native CAD payloads are still not shipped. See [foundation milestone 02](docs/FOUNDATION_MILESTONE_02.md).

## v0.4 direct viewport shaping — generated solids only

Tap **EDIT ON MODEL** in the tools drawer while a generated box is open. Three camera-projected handles appear on the top surface: LEAN (moves top sideways), WIDTH (adjusts the top width), and LENGTH (adjusts the top length). Drag a handle and lift your finger to commit the new generated geometry; the same native Document backs slider and direct manipulation Undo/Redo. The UI shows a numeric preview while dragging and regenerates the mesh on release to avoid repeated JNI/Filament asset churn.

This is **not** imported GLB editing or generic mesh face/edge picking. Normal orbit/zoom gestures remain available away from handles. The top handles hide when the top surface is back-facing from the camera. See [milestone 03](docs/FOUNDATION_MILESTONE_03.md).

## v0.5 face selection and extrusion (generated box)

Tap **SELECT FACE**, then tap a visible surface on the generated box. The selected face is outlined and shaded, with a **PULL** handle at its centre. Drag that handle vertically or enter an exact signed depth (metres) under **FACE TOOLS**, then press **EXTRUDE m**. A positive value creates an inset raised panel and a negative one creates an inset recess. The generated mesh has actual new cap, wall and border triangles and exports as a real GLB. There is one parametric extrusion feature per box in this first slice; selecting and extruding another face replaces the previous extrusion rather than stacking arbitrary features.

Undo/Redo works through the native document. `MFORGE_DOC 3` stores the selected extrusion feature and survives app restart; previous v1/v2 generated-box recovery files remain readable. Imported GLBs stay preview-only. Direct top-shaping controls and orbit controls remain separate modes. See [milestone 04](docs/FOUNDATION_MILESTONE_04.md).

## v0.6 independent face regions

Native generated boxes now retain separate rectangle/oval inset features on **each of the six original box faces**. Select a face, choose an area outline (rectangle or oval), set its size (width and height as percentages of the face), move the area with X/Y position sliders and pull it outward or recess it inward. The selected outline previews the area while adjusting sliders. Editing another original face preserves the previous face's feature. Undo/Redo and crash-safe local recovery retain all features through `MFORGE_DOC 4` with migration from v1–v3. The bottom tools are scrollable and do not cover the entire model.

This iteration still limits a face to one editable region, rather than stacking multiple cuts on the same face. Imported GLB geometry, arbitrary edge/vertex operations, circular geometry of fixed real-world radius, and a B-Rep kernel are future work. See [milestone 05](docs/FOUNDATION_MILESTONE_05.md).

## v0.7 draw-and-curve on-model selection

The face editor now has a **DRAW AREA** mode. Tap it and drag across a visible face to establish a region using your finger, much like drawing a marquee rectangle. Its four **cyan edge handles** bend the boundary into a freeform four-edge curved outline; the **PULL** handle extrudes or recesses that exact region. This is a real tessellated geometry operation in the native C++ mesh engine, not just a painted selection. Saved editable projects use `MFORGE_DOC 5` and still migrate v1–v4 drafts. The previous numeric area sliders, rectangle and oval presets, multi-face editing, native Undo/Redo, DEV signing, and GLB export remain available.

The first drawing pass supports one four-sided region per original face, four independent curved sides and one signed extrusion depth. It does not yet support arbitrary freehand outlines, extra control points, multiple disconnected areas on a single face, CAD NURBS/B-Rep, or editing imported GLB topology. See [milestone 06](docs/FOUNDATION_MILESTONE_06.md).

## v0.7.1 curved-region hotfix

The initial v0.7 phone test revealed **Area not applied: nonintegral face shape code** and bent edges snapping straight. Cause: the Android JNI parser was limited to face kinds -1 through 1 while the new four-edge curved region is type 2. v0.7.1 shares one checked face-type decoder between native host tests and JNI, accepts kind 2, and adds tests for curved zero-depth drafts and subsequently pulled geometry. Android now reports the region as saved only after its native commit succeeds. Development package/signing identity is unchanged and versionCode 11 installs over DEV v0.7. User device acceptance remains required.

## v0.7.2 explicit region sizing stage

Following the v0.7.1 phone confirmation that bending works, touch drawing has been refined into **DRAW → SIZE → CURVE → PULL**. After drawing an area, **gold corner handles** appear and stretch the selected rectangle with the opposite corner anchored. The on-screen **NEXT: CURVE** control advances to cyan edge bending. **NEXT: PULL** enables the white center handle, and **EDIT SIZE** returns to the sizing stage for further adjustment. Dragged size changes are committed as native editable face-region parameters with Undo/Redo and project recovery. Curved edges and existing extrusion depth are retained during resizing (incompatible negative bulges are bounded within the face). Existing numeric width/height/position controls still work.

Only the generated box's original faces are editable, still with one selected region per face. This is not arbitrary vertex-based sculpting or imported-GLB topology editing. See [milestone 07](docs/FOUNDATION_MILESTONE_07.md).

## v0.8 Studio Guide, portable projects, symmetry and model inspection

The optional **✦ STUDIO** button opens an eight-step guided path: **Create Base → Shape Body → Draw Detail → Size → Curve → Pull → Mirror → Save**. A small dismissible guide stays visible near the viewport's lower-left corner and activates each existing modeling tool, with Back/Next to navigate at your own pace. It includes starter body blanks and a safety confirmation before replacing the current model. The guide does not add hidden cloud dependencies or block advanced manual controls.

**Editable projects:** **SAVE PROJECT / OPEN PROJECT** use Android Files to save/load the native `.mforge` editable document, preserving box dimensions, face-region shapes, positions, curves and extrusion depths. GLB remains a separate portable *render mesh*, not a round-trippable editable project; imported GLBs remain preview-only. Local Android private autosave remains. Versioned native parser and 1 MB size limit protect project imports.

**Mirror detail:** Select a modified face, choose **MIRROR SELECTED DETAIL**, review the opposite face and any overwrite warning, then confirm. Native C++ reflects offsets and curved edges with a single undoable transaction; the other four faces are untouched. This is a face-to-opposite-face parametric symmetry tool for the generated box, not arbitrary cross-model mirroring.

**Model Check:** Audits generated dimensions, triangle count, basic degeneracy, normal direction and GLB size, then offers Save Editable Project or Export GLB. It is **not** a general watertight/self-intersection proof.

The public DEV signing identity remains pinned, and `versionCode 13` is intended to upgrade from v0.7.2 in place. See [milestone 08](docs/FOUNDATION_MILESTONE_08.md) and [full studio roadmap](docs/STUDIO_ROADMAP.md).

## First real-user gate

A first-time user should create a box, change dimensions, taper and shift the top, undo/redo, inspect every face, collapse/open tools, export to phone storage, reopen the GLB, and see the same size and shading. Measure phone frame rate and crash-free startup. Only after that do we implement a real editable topology core.

## User research and positive-feature design backlog

Our competitive research deliberately tracks **both** user frustrations and successful interaction patterns across Blender, Nomad Sculpt, Shapr3D, Plasticity, Tinkercad, Prisma3D, and Onshape. The [UX Friction Research and Positive Feature Backlog](docs/UX_FRICTION_RESEARCH_2026-11-16.md) maps each finding to an Android-first behavior, proposed priority, architecture dependency, and measurable on-device acceptance test. Findings are qualitative, not statistically representative. New features in this backlog are proposals until coded and tested.

## Public DEV signing — stable upgrades without uninstall

**From v0.4.1 onward, the debug APK is a separate `com.modelforge.nativeeditor.dev` package**, shown as **Model Forge DEV**. It is signed with one pinned, openly published **TEST-ONLY** key from `signing/model-forge-public-dev.p12.b64`. The Gradle build recreates the keystore in ignored local `build/` output and every GitHub Actions APK must match the pinned certificate fingerprint and package identity.

- **Future debug APKs** with the **same `.dev` package ID**, **same signing key**, and **nondecreasing versionCode** can update an installed DEV copy without uninstalling. Increase versionCode when shipping builds.
- **v0.4 and older debug APKs** used `com.modelforge.nativeeditor` with per-run signing keys. **v0.4.1 DEV installs alongside them**, rather than updating them; app-private drafts are not automatically transferred between apps. Export GLBs from the old app before cleaning it up.
- **Warning: this public signing key contains a publicly accessible private key and password.** Anyone can sign an APK that appears to be an update to the DEV package. Install DEV APKs **only from this repository's trusted GitHub Actions builds** and **do not use DEV for secrets, personal or sensitive data, or public distribution**.
- **Production:** keep `com.modelforge.nativeeditor` exclusively for a **new, privately signed release key** held in GitHub Actions secrets/other controlled signing storage. Do not reuse this published test key for production. The production identity is independent and will install alongside the DEV app; a migration/export plan will be required for user projects.

See [Android signing plan](docs/DEV_SIGNING_POLICY.md).

## Build

Android SDK 35, NDK 27.0.12077973, CMake 3.22.1, JDK 17, Gradle 8.11.1 / Android Gradle Plugin 8.9.2, Kotlin 2.0.21. From an Android-capable machine: `gradle :app:assembleDebug`. GitHub Actions runs a host-native C++ geometry test and builds an arm64 APK.

## Licensing

The public repository is source-visible. A project license, community terms and monetization strategy have **not** been selected yet; do not assume third-party reuse rights. Third-party dependency license audits must occur before distribution.
