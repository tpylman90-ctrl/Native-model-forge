# Foundation milestone 01 — CAD and native document (2026-11-17)

## Status

**Gate A, desktop precision: PASSED** in [GitHub Actions run 44147790084](https://github.com/tpylman90-ctrl/Native-model-forge/actions/runs/44147790084). **Gate B, Android OCCT: NOT RUN.** The Android APK still uses the existing mesh kernel; it does not edit CAD solids.

## Implemented and tested

- `app/src/main/cpp/document.hpp/.cpp`: independent C++ model document with stable object IDs; explicitly distinguishes polygon mesh objects from external B-Rep CAD references; validates revision before edits; metadata serialization and reloading; bounded 64-step in-session Undo/Redo; atomic validation (failed edits do not modify current state). This is NOT wired to the Android interface or an OCCT object store; CAD references are metadata, not fabricated CAD geometry.
- `cad/occt_probe.cpp`: genuine OCCT B-Rep box with 3 mm fillets and cylindrical pocket cut, volume/validity checks, coarse and fine tessellation, STEP save and reimport.
- `cad/export_preview.py`: validates STL, strips degenerate faces, verifies watertightness and winding, converts mm/Z-up into metres/Y-up and exports PBR GLB for Filament.
- `.github/workflows/cad-kernel.yml`: isolated host job. CAD libraries have not been included in the shipping Android APK.

## Measured Gate A result (Ubuntu 24.04, OCCT 7.6.3)

| Measurement | Result |
|---|---:|
| CAD source box dimensions | 120 × 80 × 30 mm |
| Box volume | 288,000 mm³ |
| Filleted solid volume | 286,259 mm³ (rounded) |
| Pocket-cut volume | 283,847 mm³ (rounded) |
| Preview triangles | 552 |
| Detailed tessellation triangles | 3,056 |
| Degenerate STL faces removed before GLB | 8 |
| Validated watertight GLB triangles | 3,048 |
| STEP reimport volume deviation | ~5.82e-10 mm³ |

These are **single-fixture test outcomes**, not general tolerance or performance guarantees.

CI artifact includes STEP (canonical CAD interchange), STL, GLB preview, JSON validation metrics, and dependency inventory.

## Remaining gates, ordered

1. **Android ARM64 OCCT feasibility**: pin source tag, build only required modules with NDK, track ELF/shared libraries, APK size, startup RAM, CPU performance and license compliance. No production inclusion until reviewed.
2. **CAD document integration**: persist true B-Rep payload, feature parameters and revisions; stable semantic face/edge references; protect against invalid topology edits.
3. **Filament UI vertical slice**: select a real CAD edge, preview an adjustable Round Edge, perform a Cut Recess and rollback/undo, with background computation and on-device checks.
4. **Project and export safety**: atomic autosave, explicit conversion to polygon mesh, STEP for CAD, GLB for games; original CAD geometry never overwritten by tessellation.
5. **Automotive quality benchmark**: curved roof/hood transitions, engineered chamfers, panel gaps, no pinched shading, direct phone touch usability.

## Source links

- [CAD probe](../cad/occt_probe.cpp)
- [GLB conversion validator](../cad/export_preview.py)
- [Native document and tests](../app/src/main/cpp/document.hpp)
- [Accepted hybrid CAD architecture](ADR-0001_HYBRID_CAD_KERNEL.md)

**Important:** a successful desktop OCCT benchmark does not mean Plasticity feature parity, CAD on Android, NURBS editing or native STEP export in the app. Those require Gate B/C integration and tests.
