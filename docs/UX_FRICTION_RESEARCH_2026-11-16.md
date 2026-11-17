# Model Forge — User friction research and UX acceptance gates
**Research date:** 2026-11-16. **Status:** exploratory product research, not representative survey data.

## Method and boundaries
Reviewed publicly accessible first-person Reddit discussions and Google Play reviews for Blender, Nomad Sculpt, Prisma3D, Sculpt+, and Onshape. Grouped repeated *themes* across products, rather than counting comments as unique verified users or estimating population-wide issue frequency. Reviews are self-selected, contain version/device variation, and may describe fixed bugs. No claim here that any competitor currently lacks a feature universally. Emphasize observed user goals, positive counterexamples, and concrete acceptance tests.

### Research findings (ranked by proposed Model Forge impact, NOT measured incidence)

| ID | User friction and evidence | Severity / product consequence | Model Forge design principle | Proposed acceptance test |
|---|---|---|---|---|
| F01 | Work lost after crash, file operation or accidental back/exit: Prisma3D Legacy review (June 2026); Onshape Android review (July 2023); Sculpt+ review (2024). | **Critical:** trust failure; user may abandon app | Versioned local document, atomic autosave, crash restoration, explicit discard warning, bounded undo | Make 10 edits; kill app; reopen; all 10 edits recover. Cancel/outside taps never erase work without confirmation. |
| F02 | Hard to learn controls, modes, hidden shortcuts and unclear terminology: Blender users (2025–2026); Onshape Android tooltip complaint (April 2026). | **High:** beginner abandonment | Plain-language verbs, visible controls, searchable commands, short in-context explainers; Easy + Advanced on same document | New user can select, move, reshape and save without remembering a shortcut; controls explain *what will happen* before editing. |
| F03 | Touch selection and navigation conflict: Prisma3D mobile review (Aug 2026), Android app selection/outlining update notes (Sep 2026), Onshape accidental discard. | **High:** inaccurate model editing, accidental actions | Explicit navigation/edit states, large handles with off-object screen space, screen-space hit slop, axis constraints, zoom/pan independent of edit mode | In 20 attempts to select a small face, achieve >= 18 correct first taps; no accidental mesh changes from orbit gestures; return/undo always visible. |
| F04 | Sculpting tools struggle with sharp mechanical forms, bevels, retopology and booleans: Nomad Sculpt Reddit (2025–2026). | **High** for designed hard-surface niche | Dimensioned geometry, deliberate bevel/inset/extrude, clean profile control, preview-before-commit, face normals + wire/curvature diagnostics | Beginner creates a symmetric machine housing with two recesses and chamfered edges; exports GLB retaining hard edge appearance. |
| F05 | Large/complex scenes produce lag or crashes: Prisma3D review (Apr 2026), Sculpt+ reviews, Nomad workflow discussions. | **High:** reach on budget phones | Per-device budgets, mesh-level profiling, edit/preview LOD, background import parsing with memory caps, graceful degrade | On budget ARM64 phone: maintain responsive camera on set benchmark scenes; never lose document on memory pressure; warn before heavy topology changes. |
| F06 | Import/export limitations, inaccessible tools or paywalled user files: Prisma3D and Nomad/Sculpt+ reviews. | **Medium/High:** interoperability and trust | Free local GLB creation/open/export, honest capability labels; reversible, provenance-preserving project format | Round-trip an exported test mesh through standard glTF validator and back; no account/payment needed for own creations. |
| F07 (positive) | Nomad Sculpt users describe quick, fluid startup and intuitive creation. | **High:** a competitive strength to borrow | Show a real editable object immediately; support spontaneous creation; avoid modal onboarding before first success | First-time model creation and export in 5 minutes; judge clarity with uncoached testers. |

## Actionable roadmap for independent native engine

### P0 — Trust and recoverability (before broader beta)
1. Local project document separate from transient Filament render entities and flattened GLB.
2. Atomic autosave on committed command: write temp, flush, rename, retain previous snapshot.
3. On restart, detect recoverable changes and offer **Restore** / **Discard** with explicit confirmation.
4. Undo/Redo shared by dimension edits and future geometry operations, serialized with project; clarify v0.2 only supports in-memory profiled-solid history.
5. Instrument anonymous **local-only** error counters and optional bug-report export; no data upload without consent.

### P1 — Direct manipulation, not long parameter forms
1. Deterministic object / face / edge / vertex selection with visual outline and explicit mode indicator.
2. Right-side camera navigation pad stays separate from mesh manipulation; offer a large **Edit** handle contextually on selected geometry.
3. Selection handles sized for fingers; drag axes, snapping, exact numeric edit as a secondary advanced control.
4. Make basic operations comprehensible as verbs: **Push/Pull**, **Widen**, **Inset Panel**, **Round Edge**, **Duplicate**, with technical terms in Advanced view.
5. Hit-target and intent tests: orbit must not modify geometry; edits have short preview and visible Cancel/Apply.

### P2 — Hard-surface capabilities
- Retained, editable topology rather than only regenerated triangle soup; transform, face extrude, inset, bevel with real topology.
- Consistent symmetry plane and snap to axes; editable measurement fields.
- Non-destructive operation stack where feasible; explicit warning before destructive conversion.
- Surface-quality diagnostics (face normals, open boundaries, overlapping faces, curvature).
- GLB export validation against third-party glTF tools and on-device viewer.

### P3 — Performance / materials / community
- Device-specific testing on affordable ARM64 phones; memory-safe model parsing, transient LOD for editing; background save and recovery.
- GLB materials/texture fidelity, physically based preview, optional advanced settings.
- Community only **after** private-by-default drafts, attribution/licenses, abuse reports, moderation and permission controls.

## First usability experiments
1. **No tutorial:** novice opens app, selects shape, modifies face width, exports GLB. Measure time, mis-taps, help events, completion rate.
2. **Recovery:** perform 10 edits then force app exit; compare saved revision and mesh hashes with expected state.
3. **Precision:** user selects small region, translates on one axis, undoes, redoes; log first-tap success and unwanted orbit/edit events.
4. **Hard-surface:** create a clean chamfered panel enclosure, evaluate whether result matches specified mm measurements and has manifold mesh.
5. **Import:** test GLBs with multiple meshes, materials/textures, normalized/non-normalized transforms, missing resources and large files. Confirm behavior and constraints explicitly.

## Qualitative evidence (primary conversations and store pages)
- Blender beginners: [overwhelming shortcuts (Nov 2025)](https://www.reddit.com/r/blender/comments/1oywcjx/), [hidden UI commands (Aug 2025)](https://www.reddit.com/r/blenderhelp/comments/1n3toln/), [tutorial barriers (Mar 2025)](https://www.reddit.com/r/blenderhelp/comments/1j1l6oy/), [edit mode confusion (Jun 2026)](https://www.reddit.com/r/blenderhelp/comments/1ucd2qe/).
- Nomad: [hard-surface modeling and bevel constraints (May 2025)](https://www.reddit.com/r/NomadSculpting/comments/1kearm2/), [mechanical modeling difficulty (Nov 2026)](https://www.reddit.com/r/NomadSculpting/comments/1xrc8zu/), [topology/edge handling (Jan 2025)](https://www.reddit.com/r/NomadSculpting/comments/1huhgip/), [accessible creativity positive example (Nov 2025)](https://www.reddit.com/r/NomadSculpting/comments/1orfvj6/).
- [Prisma3D Google Play](https://play.google.com/store/apps/details?id=com.prisma3D.prisma3D), [Prisma3D Legacy Google Play](https://play.google.com/store/apps/details?id=com.prisma3D.prisma3DLegacy): public reviews on navigation, lag, crashes, loss of work, import and subscription restrictions.
- [Onshape Android Google Play](https://play.google.com/store/apps/details?id=com.onshape.app): reviews about no tooltips, mobile creation difficulty, accidental lost drafts.
- [Sculpt+ Google Play](https://play.google.com/store/apps/details?id=com.Endvoid.SculptPlus): reviews about crashes on save/subdivision and import restrictions.

## Decision rule for adding features
Every proposed feature must identify a user task, a failure scenario it removes, an on-phone acceptance test, and whether it belongs to Easy, Advanced or both. Prioritize **reliability and control** ahead of visual effects, AI, or public sharing. Do not market features as implemented before an APK build, real-device gesture check, and GLB regression test.
