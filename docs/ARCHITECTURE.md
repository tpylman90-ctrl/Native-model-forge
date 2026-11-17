# Native Model Forge — architecture and boundaries

## Why this split exists

A strong phone-friendly editor needs a low-friction interface without sacrificing exact geometry. Rendering cannot own edit history; UI controls cannot be the source of truth for mesh topology. Future Forge AI and community sharing must not require replacing the modeling engine.

| Layer | Foundation | Roadmap |
|---|---|---|
| Android UI | Kotlin + framework Android Views, touch-first beginner flow, collapsing tools | accessible Compose components, advanced mode, undo history and object tree |
| Renderer | Filament 1.69.3 with glTF preview and studio key/fill lighting | PBR HDR studio profiles, occlusion-safe picking, mesh edit overlays |
| Modeling kernel | C++17 NDK exact-dimension box generator + JNI, desktop native tests | indexed half-edge manifold topology, symmetry, face extrude, bevel, modifiers |
| Format | GLB 2.0 native primitive export; GLB preview import through Android Files | owned editable native document retaining history, reference images and layers |
| Commands | Core `box(w,h,l)` implemented | versioned commands validated regardless of touch/script/AI origin |
| Community | No accounts, no networking, no uploads | opted-in publication and remix with ownership, licenses, moderation and reports |
| Forge AI | No agent integration yet | permissioned local command bridge, preview/approval, opt-in edit examples |

## Deliberate technology choices

- Native Android/Filament avoids using Godot as a runtime. Filament is Apache-2.0 but full dependency/license review remains necessary.
- Ship a C++17 modeling kernel compiled for ARM64 **and** for desktop headless correctness tests, avoiding UI-coupled mesh math.
- GLB binary is an interchange format, not the future sole editable-project store; no hidden conversion of imported GLBs to uneditable block geometry.
- Avoid a cloud dependency for basic modeling; community is an opt-in service designed after core offline safety.
- Do not implement arbitrary Python execution in the mobile UI; future embedded scripting needs sandboxing and explicit permissions.

## Next implementation gate

The minimal honest step after this foundation is an actual editable object document with topology and undo. Use the native GLB first box only to prove mesh generation, renderer interoperability, Android document picker, and GLB export. Then add persistent object IDs, safe operations, and independent round-trip tests.
