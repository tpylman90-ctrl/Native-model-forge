# Co-op hover hull — collaboration log

## Revision 2 — user edit (2026-11-20)

The user opened the native shared `.mforge` hull in Model Forge AI Studio and returned an intact `#mf1` link. The FNV-1a checksum was verified (`dba30da6`).

- Width unchanged: 4.5 m.
- Height reduced: 1.8 m → approximately 1.3 m.
- Length reduced: 8.4 m → approximately 7.6 m.
- All six face features retained from the starter project.
- Import project revision was 2; source is recoverable from the user's co-op link. No imported GLB geometry has been edited.

## Revision 6 — assistant follow-up

Preserves the user's body dimensions and profile while applying four semantic face edits:

1. Front (+Z) curved region: width 0.62 → 0.70, height 0.48 → 0.42, and depth +0.24 → −0.22 m, yielding a recessed intake-like feature instead of a protrusion. Curves adjusted to [0.06, −0.03, 0.06, −0.03].
2. Right (+X) oval region: width 0.50 → 0.62, height 0.46 → 0.36, and depth +0.14 → −0.12 m, yielding a recessed side duct. Offset U stays −0.10.
3. Left (−X) oval region: matching right-side region with reflected U offset +0.10.
4. Top (+Y) curved region: width 0.52 → 0.58, height 0.72 → 0.70, and depth −0.16 → −0.20 m; curves [0.12, 0.03, 0.12, 0.03].

The existing back face region and bottom face stay unchanged. `MFORGE_DOC 5` remains the editable document format. The revision increases from 2 to 6, reflecting four face updates. The application creates a fresh history baseline on load; the original revision is preserved in the user's co-op link rather than embedded as portable undo history.

**Scope:** One generated profiled box with one region per semantic face. This is a controlled geometry experiment, not a real multi-intake automotive shell or advanced topology. Verify in the 3D viewport, especially the direction and smoothness of recesses, before building more layers.
