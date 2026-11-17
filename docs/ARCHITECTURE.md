# Native Model Forge — architecture and boundaries

## Why this split exists

A strong phone-friendly editor needs a low-friction interface without sacrificing exact geometry. Rendering cannot own edit history; UI controls cannot be the source of truth for mesh topology. Future Forge AI and community sharing must not require replacing the modeling engine.

| Layer | Foundation | Roadmap |
|---|---|---|
| Android UI | Kotlin + framework Android Views, touch-first beginner flow, collapsing tools | accessible Compose components, advanced mode, undo history and object tree |
| Renderer | Filament 1.69.3 with glTF preview and studio key/fill lighting | PBR HDR studio profiles, occlusion-safe picking, mesh edit overlays |
| Modeling kernel | C++17 NDK shape generator + JNI, tested on host and Android | **hybrid** precision CAD B-Rep/NURBS adapter (OCCT candidate, not integrated) and indexed polygon mesh topology for sculpting, imported GLBs, bevel/Boolean mesh operations |
| Format | GLB 2.0 mesh export; GLB preview import through Android Files | native hybrid document retaining CAD objects, mesh objects, operation history, materials; STEP CAD import/export after feasibility proof |
| Commands | Core `box(w,h,l)` implemented | versioned commands validated regardless of touch/script/AI origin |
| Community | No accounts, no networking, no uploads | opted-in publication and remix with ownership, licenses, moderation and reports |
| Forge AI | No agent integration yet | permissioned local command bridge, preview/approval, opt-in edit examples |

## Deliberate technology choices

- Native Android/Filament avoids using Godot as a runtime. Filament is Apache-2.0 but full dependency/license review remains necessary.
- Ship a C++17 modeling kernel compiled for ARM64 **and** for desktop headless correctness tests, avoiding UI-coupled mesh math.
- GLB binary is an interchange format, not the future sole editable-project store; no hidden conversion of imported GLBs to uneditable block geometry.
- Avoid a cloud dependency for basic modeling; community is an opt-in service designed after core offline safety.
- Do not implement arbitrary Python execution in the mobile UI; future embedded scripting needs sandboxing and explicit permissions.

## Accepted product direction: CAD precision and ease of use

**CAD-grade surfaces and accessible Android touch modeling are both core requirements.** Our triangle mesh generator and Filament renderer stay useful, but future precision solids need a true B-Rep/analytic/NURBS source of truth. The tessellated viewport mesh is a render/export representation, not the editable CAD definition. Do not automatically claim precision CAD functionality for imported GLBs.

See [ADR-0001: Hybrid CAD Kernel](ADR-0001_HYBRID_CAD_KERNEL.md) for researched Open CASCADE Technology (OCCT) candidate, license/Android feasibility gates, user actions and risks. Selection and integration remain pending. A separate Android spike must succeed before including heavy CAD dependencies in the production APK.

## Next implementation gate

The minimal honest step after this foundation is an actual editable object document with topology and undo. Use the native GLB first box only to prove mesh generation, renderer interoperability, Android document picker, and GLB export. Then add persistent object IDs, safe operations, and independent round-trip tests.
