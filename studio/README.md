# Model Forge AI Studio — initial implementation

This browser editor uses the **same native C++ Document and geometry.cpp mesh kernel** as the existing Native-model-forge Android application for parametric `.mforge` shapes, compiled to WebAssembly. Imported GLB editing uses a separate browser JavaScript kernel, `glb_workshop.mjs`, implementing a guarded subset of the Android Kotlin `GlbWorkshop` and `GlbMeshDeform` operations. The two imported-mesh kernels are not yet unified; file-level interoperability is through standard GLB.

## Implemented in this phase

- Live orbitable, zoomable Three.js viewport, with real native vertex and normal buffers and six selectable semantic faces.
- Native parametric shape controls for dimensions, top profile, independent rectangular/oval/curved face regions, pull/recess, opposite-face mirror, name, undo/redo.
- Open/save the Android-compatible MFORGE_DOC 5 editable project format, with revisions and atomic preflight validation.
- Export a real GLB of generated geometry, and a wireframe inspection switch.
- Local versioned JSON command console requiring user confirmation. It accepts reshape, set_face_region, mirror_face_region and rename_object, including stale-revision rejection.

### Constraints

This is **not yet a direct ChatGPT remote-control integration**. The command console accepts reviewed JSON manually, and a future authenticated tool bridge could use the same operations. The parametric native starter shape still supports only one generated body, six original faces, and one editable region per face. **Imported GLBs are now edited in a separate workspace**, using standard GLB as the editable exchange format rather than attempting to force arbitrary geometry into `.mforge`. The shape-region controls remain numerical. The imported-mesh brush can be placed by tapping a surface, but displacement values are numerical and there are not yet professional topology operations, bevels, edge loops, subdivision cages, or CAD surfaces. It is not yet Blender/Plasticity-level surface modeling.

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

## Co-op test workflow

The default browser scene is the native editable starter hull at studio/web/sessions/hovercraft-hull.mforge. The studio loads it automatically, and Reload shared hovercraft fetches the latest deployed revision. Save your own edits to .mforge, or use Copy link for ChatGPT: the URL fragment carries the exact native document. Paste that link into the ChatGPT conversation, and the assistant can reconstruct the project, modify its native parameters, and return an updated link or publish a new sample to the repository. For shape inspection, Save model screenshot and attach the PNG in chat.

**This is turn-based co-editing, not a live socket, cloud browser, or remotely connected ChatGPT tool.** Links are not encrypted. Anyone with a link can inspect its model. Keep sensitive projects in private files rather than share links. The link payload is capped at 8 KiB; larger models should be exported as .mforge files and attached directly.

GitHub Pages deployment is handled by .github/workflows/studio-pages.yml. A first run will attempt to enable Pages; if GitHub disallows automatic enablement, the repository owner must visit Settings -> Pages, select GitHub Actions as the source, and rerun deployment. The .mforge document remains the editing source of truth; GLB is a separate mesh export with neutral materials.

## Imported-car GLB workshop — browser beta

**Open car GLB** loads an ordinary glTF 2.0 binary file of up to 80 MB, with one embedded buffer and independent mesh nodes, into the same orbitable browser viewport. Select mesh parts using the dropdown or by tapping them in the viewport; the box outline identifies the component. Each part can be moved, rotated or scaled with a preview and explicit Apply/Cancel. In **Reshape mesh**, choose a triangle surface, tap **Pick point on mesh**, tap the car to place the brush, set its radius and local-space XYZ displacement, then preview/apply the actual vertex movement. Smooth falloff and regenerated vertex normals are computed on the original geometry. Undo/Redo and full GLB export are available. Front/side/top camera presets, wireframe and screenshots work in either workspace.

### Safe edit promises and limitations

- Existing GLB buffer data, materials, textures, UV attributes, original mesh hierarchy, node names and extensions are retained during editing. New FLOAT32 position and normal accessors are appended; one affected mesh is cloned when multiple nodes share the source mesh. Unaffected components remain unchanged.
- The original imported GLB is held locally in memory for **Revert to imported original**. A successful preview is not a committed edit until the user taps Apply. Undo checkpoints are local to the browser session; save the entire modified GLB to keep changes across sessions.
- Mesh brush supports only ordinary triangle-list FLOAT32 position accessors and integer triangle indices. Tangent-space, compressed, sparse, animated, skinned and morph-target surfaces are protected against unsafe vertex edits; component transforms remain available on separate safe unanimated mesh nodes. Assets requiring those features can be prepared in Blender first.
- Browser geometry edits currently run in JavaScript and Android geometry edits run in Kotlin. They both preserve GLB files and have similar validation and edit semantics, but the implementations have not yet been consolidated into one C++ kernel. Do not mistake functional parity for a shared imported-mesh engine.
- **Co-op URL sharing applies to small parametric `.mforge` shapes only.** Complex imported GLBs should be saved, then attached directly in the ChatGPT conversation for back-and-forth editing. Do not put full vehicle GLB data in the URL fragment.
- There is no automatic remote AI access to the viewport, no Blender-quality topology editing, and no network-based project synchronization. Complex car model changes need visual approval and high-quality mesh operations that are still in development.

Run browser GLB tests via `node --test studio/tests/glb-workshop.test.mjs`. The Studio workflow runs these, JS syntax checks, the shared native C++ tests and WebAssembly runtime tests before deployment.

**Licensing:** Free downloads are not necessarily CC0 or usable in commercial games. Check the individual model license before modifying or redistributing the result.

## Feature parity additions: Forge Basics and imported GLB command review

- The browser now offers **Forge Basics** directly in the top bar. It uses the *same* reproducible original 18-item CC0 `.mfpack` as native Android. The ZIP is generated by `tools/generate_forge_basics.py` in CI and hosted alongside the WebAssembly app. The browser reads its central directory and compressed manifest, inflates only a chosen GLB, bounds the decoded size, and checks SHA-256. It needs no extra ZIP library or account.
- A chosen part can be opened alone or **preview-inserted** into an existing imported GLB. Apply makes a new independent glTF mesh, node and material while leaving the old vehicle geometry, materials, textures, names and hierarchy untouched; Cancel reverts the preview. In-session Undo/Redo supports this action. New parts initially appear at the origin until repositioned.
- The imported GLB side panel now supports **Co-op GLB edit commands**. The protocol is `model-forge/glb-command/v1`: a JSON command identifies the selected node and expected document revision and allows only `transform` or `brush`. Any proposed command requires a human review and produces a preview; Apply is a separate manual confirmation. The GLB remains local. The assistant cannot remotely click, view, or save the user's browser document.
- Use **Save car inspection screenshot** and **Save edited car GLB** to exchange real mesh work through this conversation. The `.mforge` fragment sharing format is still unsuitable for larger imported models. Android and browser use the interoperable GLB file format, but their mesh-edit engines still need consolidation.

Tests: `node --test studio/tests/glb-workshop.test.mjs studio/tests/forge-pack.test.mjs studio/tests/glb-command.test.mjs` after generating the compressed pack with `python3 tools/generate_forge_basics.py --output studio/web/packs/forge-basics.mfpack`.
