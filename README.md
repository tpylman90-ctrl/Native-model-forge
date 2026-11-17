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
