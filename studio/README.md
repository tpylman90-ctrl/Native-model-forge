# Model Forge AI Studio — initial implementation

This browser editor uses the **same native C++ Document and geometry.cpp mesh kernel** as the existing Native-model-forge Android application. They are compiled to WebAssembly via Emscripten, rather than reimplementing the shape generator in JavaScript.

## Implemented in this phase

- Live orbitable, zoomable Three.js viewport, with real native vertex and normal buffers and six selectable semantic faces.
- Native parametric shape controls for dimensions, top profile, independent rectangular/oval/curved face regions, pull/recess, opposite-face mirror, name, undo/redo.
- Open/save the Android-compatible MFORGE_DOC 5 editable project format, with revisions and atomic preflight validation.
- Export a real GLB of generated geometry, and a wireframe inspection switch.
- Local versioned JSON command console requiring user confirmation. It accepts reshape, set_face_region, mirror_face_region and rename_object, including stale-revision rejection.

### Constraints

This is **not yet a direct ChatGPT remote-control integration**. The command console accepts reviewed JSON manually, and a future authenticated tool bridge could use the same operations. This first browser slice supports only the native starter shape: one generated body, six original faces, one editable region per face. Imported GLB mesh topology remains outside this document's editing engine. Browser region controls are numerical rather than direct curved-edge handles. It is not yet Blender/Plasticity-level surface modeling.

### Run locally

C++ native tests:

    cmake -S studio -B build/studio -DCMAKE_BUILD_TYPE=Debug
    cmake --build build/studio
    ctest --test-dir build/studio --output-on-failure

Compile browser engine (requires em++):

    bash studio/build_web.sh

Run a static server:

    python3 -m http.server 8000 --directory studio/web

Open http://localhost:8000. Do not open the HTML from a file URL; modern browser modules require HTTP. GitHub Actions uploads the complete browser files as the ModelForge-AI-Studio-Browser artifact. A hosted GitHub Pages URL has not yet been configured.

Three.js modules are currently pinned to the v0.170.0 public CDN. Only the viewer dependencies request network access; user projects and the C++ modeling code stay on the device. Vendoring those dependencies is a later offline gate.

### JSON example

Use Insert template in the browser command console for the current object ID and base revision. Example:

    {
      "protocol": "model-forge/command/v1",
      "origin": "assistant",
      "requires_approval": true,
      "object_id": 1,
      "base_revision": 1,
      "operation": "set_face_region",
      "parameters": {
        "face_index": 4,
        "kind": 2,
        "width": 0.5,
        "height": 0.6,
        "offset_u": 0,
        "offset_v": 0,
        "depth_m": 0.25,
        "curves": [0.08,-0.06,0.12,0]
      }
    }

### Next technical gates

1. Validate browser execution, GLB export and Android .mforge interchange on real devices.
2. Build true touch/mouse surface handles (DRAW → SIZE → CURVE → PULL) backed by the shared native kernel.
3. Add multi-object editable topology and imported-GLB mesh support in the canonical project.
4. Add an authenticated/permissioned tool bridge and strict visual approval before remote AI actions.
