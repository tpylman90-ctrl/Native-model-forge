# Foundation milestone 02 — Android/native shape document connection

## Implemented in the development branch

The generated-box Android editing flow now calls a canonical `forge::Document` through JNI for each committed shape edit. The C++ model tracks stable object IDs, document revision, width/height/length, and three top-surface profile parameters. The old parallel Kotlin Undo/Redo lists are removed; native transactions own undo and redo.

The latest generated shape is written to Android-private storage using `AtomicFile`, and its parameters are restored at next startup. Existing imported GLBs remain preview-only; viewing an imported asset does not overwrite the editable shape recovery copy. Exported GLBs remain tessellated scene files.

## Format

`MFORGE_DOC 2` adds three top-profile parameters per object. The independent C++ parser accepts version 1, defaulting old documents to top profile `[1, 1, 0]`. The parser rejects unsupported and corrupt records; the native tests cover atomic invalid operations, Undo/Redo and round-trip serialization.

## Explicit limitations

- This is **one generated editable box**, not an arbitrary GLB mesh editor or a real B-Rep source.
- Shape current-state recovery does not persist Undo/Redo stacks across process restarts.
- Draft is a local app-internal recovery copy, not an Android Files project export or a sharing format.
- Actual Android-run validation is required; successful CI alone does not prove gesture quality.
- OCCT remains a separately validated desktop CAD feasibility proof, not an Android dependency. Precision CAD and real face/edge picking are future milestones.

## Verification

CI builds and tests standalone C++ geometry/document, Android Kotlin and JNI, checks retained camera manipulation ownership, and audits packaged ARM64 libraries. On-device gate: create, change top profile, undo/redo, relaunch and confirm shape recovery, import GLB preview, create new box, export and reopen, and verify orbit joystick.
