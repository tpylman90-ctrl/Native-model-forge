"""Convert the OCCT sample STL into a checked, render-ready Android GLB.

This is a desktop CI validation adapter, not production CAD support on Android.
OCCT's B-Rep / STEP remains canonical. A GLB is only a tessellated preview.
"""
import json
import sys
from pathlib import Path
import numpy as np
import trimesh


def export(input_stl: Path, output_glb: Path) -> dict:
    mesh = trimesh.load(input_stl, force="mesh", process=True)
    if not isinstance(mesh, trimesh.Trimesh) or len(mesh.faces) < 100:
        raise ValueError("CAD triangulation was empty or unexpectedly small")

    raw_faces = len(mesh.faces)
    good_faces = mesh.nondegenerate_faces()
    removed = int(np.count_nonzero(~good_faces))
    mesh.update_faces(good_faces)
    mesh.remove_unreferenced_vertices()
    # Filleted OCCT corners may have zero-area STL faces; never export those.
    if not mesh.is_watertight or not mesh.is_winding_consistent or mesh.volume <= 0:
        raise ValueError("CAD preview mesh has holes, overlapping or inverted faces")
    if removed > raw_faces * 0.01:
        raise ValueError("Too many degenerate CAD triangles; investigate surface quality")
    original_bounds = mesh.bounds.copy()
    original_volume_mm3 = float(mesh.volume)
    # OCCT test uses mm and Z-up; GLB for Android uses metres and Y-up.
    mesh.apply_translation(-mesh.bounds.mean(axis=0))
    mesh.apply_transform(trimesh.transformations.rotation_matrix(-np.pi / 2, (1, 0, 0)))
    mesh.apply_scale(0.001)
    mesh.visual = trimesh.visual.TextureVisuals(
        material=trimesh.visual.material.PBRMaterial(
            name="Forge Satin Titanium",
            baseColorFactor=[83, 156, 175, 255],
            metallicFactor=0.55,
            roughnessFactor=0.32,
        )
    )
    output_glb.parent.mkdir(parents=True, exist_ok=True)
    scene = trimesh.Scene()
    scene.add_geometry(mesh, geom_name="CAD Precision Solid", node_name="CAD Prototype")
    output_glb.write_bytes(scene.export(file_type="glb"))
    reopened = trimesh.load(output_glb, force="scene")
    if not reopened.geometry or output_glb.stat().st_size < 2000:
        raise ValueError("GLB round-trip failed")
    metrics = {
        "raw_faces": raw_faces,
        "removed_degenerate_faces": removed,
        "export_faces": len(mesh.faces),
        "watertight": True,
        "volume_mm3": original_volume_mm3,
        "CAD_bounds_mm": original_bounds.tolist(),
        "GLB_bounds_metres": reopened.bounds.tolist(),
        "GLB_bytes": output_glb.stat().st_size,
    }
    output_glb.with_suffix(".json").write_text(json.dumps(metrics, indent=2) + "\n")
    return metrics


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit("Usage: export_preview.py input.stl output.glb")
    print("MODEL_FORGE_CAD_PREVIEW: PASS", json.dumps(export(Path(sys.argv[1]), Path(sys.argv[2]))))
