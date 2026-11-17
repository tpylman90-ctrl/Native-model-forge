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

## v0.2 first shaping pass: implemented vs planned

**Implemented target:** native offline Android APK; high-quality Filament viewport; dimensioned box and native parametric top-face width/length/sideways-lean shaping; recalculated hard normals; bounded 40-step Undo/Redo for shapes during the current session; actual GLB mesh bounds derived from generated vertices; drag or use right orbit pad to inspect; pinch zoom; collapsible tools; import GLB preview and export generated GLB through Android Files. Core geometry unit tests and Android CI.

**Not yet implemented:** editing imported models, arbitrary vertex/edge/face topology, freeform sculpting, persistent project/undo history across restarts, Boolean/subdivision, Python execution, user accounts, uploads, or live community. Changing box dimensions with CREATE BOX resets the current shaping history; APPLY SHAPE changes dimensions and top profile with Undo/Redo. Buttons for future tools must not claim functionality they do not have.

## First real-user gate

A first-time user should create a box, change dimensions, taper and shift the top, undo/redo, inspect every face, collapse/open tools, export to phone storage, reopen the GLB, and see the same size and shading. Measure phone frame rate and crash-free startup. Only after that do we implement a real editable topology core.

## User research and positive-feature design backlog

Our competitive research deliberately tracks **both** user frustrations and successful interaction patterns across Blender, Nomad Sculpt, Shapr3D, Plasticity, Tinkercad, Prisma3D, and Onshape. The [UX Friction Research and Positive Feature Backlog](docs/UX_FRICTION_RESEARCH_2026-11-16.md) maps each finding to an Android-first behavior, proposed priority, architecture dependency, and measurable on-device acceptance test. Findings are qualitative, not statistically representative. New features in this backlog are proposals until coded and tested.

## Build

Android SDK 35, NDK 27.0.12077973, CMake 3.22.1, JDK 17, Gradle 8.11.1 / Android Gradle Plugin 8.9.2, Kotlin 2.0.21. From an Android-capable machine: `gradle :app:assembleDebug`. GitHub Actions runs a host-native C++ geometry test and builds an arm64 APK.

## Licensing

The public repository is source-visible. A project license, community terms and monetization strategy have **not** been selected yet; do not assume third-party reuse rights. Third-party dependency license audits must occur before distribution.
