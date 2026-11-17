# Model Forge command contract — design baseline

**Status: proposal, not yet an executable API.** Native-model-forge v0.2 implements `create_box` and a `profiled_box` kernel through JNI, with in-session UI Undo/Redo. The versioned external command envelope is still only a proposal. Do not treat this document as a working AI or scripting endpoint.

## Design goal

A user's touch, an accessible editing wizard, and (later) a pasted script or Forge AI request must all resolve to the **same validated geometry commands**. UI state and renderer entities are never permanent project data. Commands act on the canonical modeling document.

## Versioned envelope (planned)

```json
{
  "protocol": "model-forge/command/v1",
  "document_id": "local-project-id",
  "base_revision": 18,
  "origin": "user_touch",
  "requires_approval": false,
  "operation": "create_box",
  "parameters": {
    "name": "Main Hull",
    "size_m": [4.5, 1.3, 8.4]
  }
}
```

The same `create_box` operation could be constructed by an easy-mode form, an advanced tool, or a trusted script runner. Only a local user can grant a future AI bridge permission to submit edits. No arbitrary Python execution or community command execution in the app.

## Required behavior before enabling external calls

- Validate operation, object IDs, finite numeric arguments, affected topology and revision.
- Preview destructive mesh changes, require approval for AI-originated edits, and preserve reversible command history.
- Apply atomically; recompute normals/tangents/bounds/material references when needed; reject invalid results.
- Store source before/after hashes for opt-in training evaluation without silently uploading user work.
- Keep an audit trail separate from personal/community profile information.
- Distinguish complete binary GLB export from the future native, non-destructive project document.

## Early operations (planned, not implemented unless noted)

| Operation | Implementation |
|---|---|
| `create_box` | C++ box geometry implemented, command envelope not yet wired |
| `set_dimensions` | UI-applied via regenerated C++ profile, command envelope planned |
| `profiled_box` | native kernel + Android sliders implemented, versioned command envelope planned |
| `transform_object` | planned |
| `select_surface` | planned |
| `extrude_face`, `bevel_edges`, `boolean_cut` | planned |
| `paint_material` | planned |
| `publish_model` | explicitly out-of-scope until accounts, rights, moderation exist |
