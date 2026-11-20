# Foundation milestone 10 — compressed GLB library on demand

## Request
Add a compact library of basic Blender-style/CAD building blocks; the Android editor must not inflate every asset during install or startup.

## Implemented
- Reproducible standard-library Python generator makes **18** original low/medium-poly CC0-dedicated GLBs under one `forge-basics.mfpack` ZIP bundle.
- Gradle runs the generator before Android asset packaging. APK carries the compact ZIP only, and the generated binary is ignored by git.
- `ForgeAssetPack` stores the compressed pack intact, reads just the manifest for catalog browsing, and loads exactly one GLB on user selection. Implements 80 MB pack, 8 MB member, 1 MB manifest limits; filename/ID allowlists, per-object expected length and SHA-256.
- An optional external `.mfpack` import stores approved packages compressed, not unpacked. A reference Blender CC0 packager is supplied with license and source record requirements.
- In-app LIBRARY offers search, categories, a GLB viewport preview, explicit Add to Craft / Start Assembly and a cancel/back path. Adds each selected object as its own part, preserving existing GLB components and material references.
- Small Kotlin importer supports glTF 2.0 single-mesh triangle GLBs with embedded positions, optional normals and optional indices (uint8/uint16/uint32). Nontrivial transforms, skins, textures and multi-mesh external objects are rejected rather than silently changed.
- Includes JVM tests for all 18 generated GLBs, manifest integrity, geometry decode, blank-assembly insertion and undo/redo, plus CI generation/packaging checks.
- Same pinned public DEV signer, higher versionCode for in-place upgrade.

## Physical acceptance
1. Update the existing DEV APK without uninstalling. Confirm camera, current model and ordinary craft editing still work.
2. Open Library. The 18 built-in objects appear by category, without a long wait. Search for `pipe` and `bolt`; results should filter.
3. Select `Circular nozzle`, inspect its GLB in the viewport, then Cancel; current craft should return untouched.
4. Select `Beveled armor blank`; Add to Craft. Open Edit Craft and position that part on your hovercraft. Undo and Redo, then Save GLB.
5. Reopen exported GLB; verify the original materials survived, and new part remains independently selectable.
6. Select `Torus ring` without a current craft to Start Assembly. Continue adding two other parts, then save GLB.
7. Test the optional importer using a valid verified CC0 `.mfpack`, and confirm unknown/malformed files are rejected without losing the model.

## Still planned
- Direct imported mesh face/edge/vertex editing, complete Blender glTF material import, meshopt/Draco, high-poly curated Blender Essentials assets, thumbnails, dynamic online pack downloads, and world-space on-model component handles.
