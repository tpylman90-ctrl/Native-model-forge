# Compressed GLB object packs — Model Forge DEV v0.10

## What ships now

**Forge Basics** is an offline, original-geometry, CC0-dedicated 18-object catalog. Items: cube, plate, sphere, cylinder, cone, hollow pipe, torus, wedge, tapered fairing, beveled armor blank, aero fin, circular nozzle, thruster housing, hex bolt, hex nut, vent grille, intake surround and wheel/thruster hub. These are useful CAD-like starting primitives, **not finished, high-detail Blender meshes**.

The complete pack is generated reproducibly from `tools/generate_forge_basics.py` (pure Python standard library). Only `forge-basics.mfpack`, a compressed ZIP containing individual glTF 2.0 `.glb` objects, is embedded in the APK during Gradle build. No individual unpacked GLB is installed on disk at startup. On the first library opening, Android copies the compressed pack intact to `noBackupFilesDir/forge-asset-packs/`, then opens its `manifest.json` without extracting model members. Selecting an item extracts **only that GLB**, checks its SHA-256, and sends its triangles into the live hovercraft workshop for preview and optional insertion. No background network access.

## How to use it

1. Open Model Forge → tap **▦ LIBRARY**.
2. Search by name or choose a category, such as Primitive, Bodywork, Mechanical, or Propulsion.
3. Tap an object; the app inflates that selected GLB into memory and shows it on the 3D canvas.
4. Choose **ADD TO CRAFT** to insert it into the currently editable hovercraft, or **START ASSEMBLY** to create a fresh multi-part GLB.
5. Open **EDIT CRAFT** to move, rotate, and resize the inserted part with guided steps. Save the whole finished GLB through **SAVE GLB**.
6. If the model is only a preview or a generated box, starting a new assembly does not edit that original. Save existing work before switching.

## Optional CC0 Blender packs

The **IMPORT PACK** action accepts an `.mfpack` from Android Files. It stores the pack compressed, with a 80 MB compressed size limit. The catalog reads only the manifest; individual GLBs are extracted by name only when chosen. Each object has an uncompressed size cap of 8 MB and a required SHA-256. IDs and entry paths are sanitized to avoid ZIP traversal. Malformed or unsupported GLBs are rejected and do not replace an existing craft.

To build a pack from *verified CC0 Blender-exported* objects:

1. Open the asset in Blender, verify the asset's individual license is CC0, apply transforms (Ctrl+A → All Transforms), convert to mesh if necessary, and export one single mesh as **glTF 2.0 (.glb)** without animations or image textures.
2. Create a JSON source manifest, for example:

```json
{
  "pack_id":"my-blender-cc0",
  "title":"My CC0 Blender Objects",
  "assets":[
    {
      "id":"example_fairing",
      "title":"Example fairing",
      "category":"Bodywork",
      "path":"exports/example_fairing.glb",
      "license":"CC0-1.0",
      "source":"https://original-source.example/cc0-item"
    }
  ]
}
```

3. Run `python3 tools/package_blender_cc0_glbs.py --manifest my-pack.json --output my-blender-cc0.mfpack`.
4. Copy the `.mfpack` to your phone and import it through the Library. The input package must be a single embedded glTF binary mesh with ordinary float32 vertex positions, no texture dependency or node transforms, and triangles (indexed or unindexed). This is a **first-pass format restriction**, not an arbitrary glTF limitation.

## Licensing safety

The **Blender official Essentials asset repository** publishes assets under CC0; however, most Essentials assets are `.blend` files requiring manual conversion and validation before packaging. **Poly Haven** assets are also CC0. Never assume an asset is redistributable just because it is free to download, free in BlenderKit/Blendkit, or included in a demonstration file. In particular, the royalty-free category on Blendkit can restrict redistribution of models as standalone assets. This release **does not bundle arbitrary Blendkit models**. Every future curated downloaded model should retain a source/license record.

Sources: https://projects.blender.org/blender/blender-assets , https://polyhaven.com/license , https://www.blendkit.com/docs/licenses/

## Performance and future improvements

- Catalog browsing and search do **not** decode the objects; only selected assets are inflated. No long-lived per-asset cache is required.
- Existing materials in a loaded hovercraft are preserved. Added library objects currently use a single PBR base color and become new independent glTF nodes. Advanced multi-material/UV/textured Blender models require a future importer.
- Add future packs by category (Hard-Surface/Vehicle, Mechanical, Architecture, Sci-Fi, and Organic) without forcing every user to download them.
- Future enhancement: download approved CC0 packs on request into private storage, show cached thumbnails, allow ZIP member preview without swapping the craft camera, optimize assets using meshopt/Draco when the decoder is present, and add precise weld/Boolean/surface deformation tools.
- A compressed ZIP does not magically reduce runtime mesh RAM after an object is loaded. The efficiency comes from only decoding *one selected object*, not retaining unpacked copies of the entire catalog.
