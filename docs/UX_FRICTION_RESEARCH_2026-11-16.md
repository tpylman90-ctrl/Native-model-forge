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

## Positive feature research — patterns worth adopting (expanded 2026-11-17)

**Positive feature here means either (A) an independently documented capability plus user reports of its usefulness or (B) a clearly identifiable workflow advantage.** These are *inspirations*, not claims that a given company's users universally prefer the feature. "Official" confirms existence and behavior; "user" reflects a self-selected firsthand experience. None of the proposals below should be marked implemented solely because this research is recorded.

| ID | Product & positive feature | Evidence / what users value | Proposed Model Forge equivalent | Priority / implementation boundary |
|---|---|---|---|---|
| G01 | **Shapr3D: direct push/pull with editable dimensions** | User reports touch controls feel intuitive even on small iPad; official sketching documentation describes extrusion and precise inputs [user](https://www.reddit.com/r/Shapr3D/comments/1jqyy02/) · [official](https://support.shapr3d.com/hc/en-us/articles/18857081591964-Sketching-in-Shapr3D) | Tap face → large **Push / Pull** handle → live preview → optional exact mm input → Apply/Cancel. No mandatory sketch workflow for basic mesh editing | **P1**. Requires stable editable face IDs, mesh rebuild and selection; mesh extrusion is *not* CAD B-rep extrusion |
| G02 | **Shapr3D: selection-adaptive tools** | Official documentation: tool suggestions react to selection instead of hunting through menus; users praise clean interface [official](https://support.shapr3d.com/hc/en-us/articles/7873882619548-Adaptive-user-interface) · [user](https://www.reddit.com/r/Shapr3D/comments/1jqyy02/) | Context strip: selecting object shows Move/Scale/Duplicate; face shows Push/Pull/Inset; edge shows Bevel; background shows Add/Import. Always offer full tool search | **P1**. Deterministic rules first, no AI/network needed; avoid hiding advanced commands |
| G03 | **Shapr3D: combine direct edits with parametric history** | Vendor documents both immediate edits and revisitable history [official](https://www.shapr3d.com/content-library/shapr3d-history-based-parametric-modeling) | User can drag a handle now, then adjust the same operation's amount later in a simple history strip | **P2**. Needs persistent project document and stable operations, not only Undo snapshots |
| G04 | **Nomad Sculpt: fast, approachable brush sculpting** | Firsthand app reviews value an intuitive, not overwhelming interface; official manual covers brush tools and mask workflows [user reviews](https://apps.apple.com/us/app/nomad-sculpt/id1519508653) · [official](https://nomadsculpt.com/manual/tools) | **Sculpt** workspace with a small default brush set: Move, Smooth, Build, Flatten, Mask, with visible radius/strength and pressure-independent finger controls | **P2**. Must support localized topology deformation efficiently; sculpting should not compromise exact mechanical meshes by default |
| G05 | **Nomad Sculpt: non-destructive layers and quick remeshing** | Layers hold editable sculpt and paint contributions; topology tools support voxel remesh, dynamic detail and multires [layers](https://nomadsculpt.com/manual/layers) · [topology](https://nomadsculpt.com/manual/topology) | Toggleable **Detail Layers** and **Remesh** (with visible triangle budget + preview), distinct from engineering/profile modeling | **P2/P3**. Requires robust remeshing and data transfer; do not confuse smooth sculpt geometry with quad/production topology |
| G06 | **Blender: non-destructive modifier stack and symmetry** | Blender's manual confirms reorderable modifiers; creators routinely use Mirror in modeling [official](https://docs.blender.org/manual/en/4.5/modeling/modifiers/introduction.html) · [community](https://www.reddit.com/r/blender/comments/1xlcxwu/modifiers_tier_list/) | **Shape Steps**: Mirror, Bevel, Subdivide, Array, each with enable toggle and sliders. Show result without permanently baking until requested | **P2**. First build serializable operation graph and dependency-aware evaluation; don't label history non-destructive before it's real |
| G07 | **Blender: asset browser and reusable parts** | Official Blender Studio teaching highlights browsing models, materials, brushes and node assets; users appreciate reuse but complain about linking/editability [official](https://studio.blender.org/training/blender-fundamentals-45-lts/blender_4-5_lts_asset-browser/) · [user](https://www.reddit.com/r/blender/comments/1et4en4/how_many_of_yall_use_the_asset_browser_i_really/) | **Parts Shelf** with rendered GLB thumbnails, searchable tags, **Insert Copy** or **Linked Instance**, visible "Edit this copy" action, origin/license metadata | **P1 (local shelf)** / **P3 (sharing)**. Local import thumbnails first; public uploads only after rights/moderation |
| G08 | **Plasticity: quick Booleans, chamfers and hard-surface detailing** | Firsthand reports praise reliable Boolean/fillet workflow and quick design iteration; some note limited parametric editability [user](https://www.reddit.com/r/Plasticity3D/comments/1hemml5/) · [user](https://www.reddit.com/r/Plasticity3D/comments/1xfr8rh/) | **Cut Panel**, **Carve Recess**, **Round Edge**, with before/after preview, controllable radius/depth, and reusable cutter shapes | **P2**. Our current kernel is triangle-mesh-based; CAD-style NURBS/B-rep fillet robustness is **not available** and requires a separate feasibility decision |
| G09 | **Tinkercad: simple solids + holes + group** | Users praise quick functional designs; educational guides document intuitive resize grips, hole subtraction and grouping [user](https://www.reddit.com/r/3Dprinting/comments/11ci6vh/) · [guide](https://cplmakerlab.github.io/guide/tinkercad-quickstart-guide/) | Drag cube/cylinder into scene → tap **Solid / Cutter** → group → live subtract preview. Plain-language cutter controls and undo | **P2**. Requires tested watertight mesh Boolean backend; early version could offer only safe built-in cutters, clearly marked |
| G10 | **Onshape: versions and collaborative project history** | Onshape documents mobile parts navigation and version/history workflows; mobile supports richer CAD than viewing alone [official](https://www.onshape.com/en/blog/mobile-cad-onshape-app) · [help](https://cad.onshape.com/help/Content/Home/getting_started_with_onshape.htm) | **Project Revisions**: named local snapshots, comparison, restore; later opt-in share/comment/remix without silently overwriting previous versions | **P0 local revisions; P3 collaboration**. No accounts or cloud dependency for core creation |
| G11 | **Prisma3D: approachable 3D objects and animation timeline** | A 2026 Android reviewer praises learning imported models and keyframes while flagging lag on large rigs [user review](https://play.google.com/store/apps/details?id=com.prisma3D.prisma3D) · [app features](https://play.google.com/store/apps/details?id=com.prisma3D.prisma3Dv3) | **Animate** workspace later, with minimal transform keyframes, easy object selection, timeline playback and GLB/glTF interoperability | **P3**. Don't let animation complexity delay modeling, saving and performance gates |
| G12 | **Nomad: PBR material preview as a creative feedback loop** | Official mobile feature list includes painting, lighting, post processing and render display [features](https://apps.apple.com/us/app/nomad-sculpt/id1519508653) | One-tap viewport presets: **Clay**, **Studio PBR**, **Wireframe**, **Surface Quality**, with a few clear metal/roughness sliders | **P1 simple presets / P2 materials**. Build on existing Filament and preserve accurate GLB PBR properties |

### What the positive evidence changes in our strategy

- **Keep creation immediate:** one start object, one visible edit affordance; shortcuts and modes should accelerate rather than block a beginner. This is the strongest lesson from Shapr3D, Nomad and Tinkercad.
- **Retain professional depth:** easy-mode verbs translate to validated topology operations also accessible through numerical controls and later versioned scripts/Forge AI. Never fork the underlying model representation.
- **Make revision safety visible:** favor Onshape's durable revision concept and Blender/Nomad non-destructive changes, but implement **local** persistence before any social workflow.
- **Treat hard-surface geometry as a differentiator:** Plasticity's mechanical detailing is a valuable benchmark, but our architecture must distinguish polygon mesh tools from true B-rep/CAD constraints.
- **Use the renderer we have:** Filament already enables PBR preview; build clear material presets and inspection modes instead of creating a custom renderer.
- **Borrow principles, not proprietary assets:** no reuse of competitor source, icons, artwork, or interface screenshots in shipping code without license review.

### Positive-first design backlog (proposed; not yet implemented)

| Order | First deliverable | User action | Verifiable done criterion | Technical dependency |
|---|---|---|---|---|
| 1 | **Project Restore** | Reopen and recover latest solid edits | Kill app after 10 committed edits; latest and previous snapshots recover offline; no corrupt file | Owned project schema, transactional writes |
| 2 | **Tap-to-select & context strip** | Tap object/face → see only applicable tools | At least 18/20 first-try hits on small targets in phone test; no orbit gestures turn into edits | Ray picking or ID-buffer picking, stable object/face identifiers |
| 3 | **Direct Push/Pull** | Drag face, snap, optionally type measurement | Exported mesh face position equals desired value; Undo/Redo works | Indexed topology, normal/bounds update |
| 4 | **Parts Shelf** | Insert local saved GLB thumbnail into a scene | 10 objects load as separate named instances; preserve material; never silently overwrite | Multi-object document, GLB import provenance, thumbnail cache |
| 5 | **Mirror & Bevel** | Toggle symmetry, adjust chamfer radius | Edit half enclosure; counterpart reflects; no cracked seams/invalid bevel | Stable topology + modifier evaluation |
| 6 | **Cutter Shapes / Inset Panel** | Place cutout, preview, commit | Two sized recesses produce manifold exported mesh on benchmark solids | Robust mesh Booleans and topology validation |
| 7 | **Material/inspection presets** | Switch between clay, PBR, wire, surface quality | Same geometry and material export unaffected by view-only modes | Filament shading & overlays |
| 8 | **Brush and Detail Layers** | Mask, deform, undo, toggle intensity | Stroke works offline on benchmark meshes and can be adjusted independently | Sculpt kernels, layer delta storage |
| 9 | **Opt-in sharing / keyframe workspace** | Publish attributed remix or animate part | Permission checks, moderation, and timeline functionality pass independent gates | Account/security/legal design or animation data model |

**Design constraint:** Item order is an engineering recommendation based on usability impact and dependency sequence—not a measured popularity ranking. A useful prototype should prove each action is faster and less error-prone on a budget 6-inch Android handset than the current workflow, with real testers.

### Evidence-quality checklist for the next research pass

Record exact comment/review URL, date, product and version when shown, device/input type, sentiment, named workflow, whether the claim is a *feature exists* statement or a *user preference*, and whether the complaint could have been fixed. Avoid treating vote scores or search-result counts as unique users. Keep positive and negative examples for the same tool, and explicitly mark contradictory or device-specific accounts. Target a dated, reproducible sample across stores/forums before estimating frequencies; we have **not** completed a frequency study.


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


## Higher-quality surface modeling is now a product requirement

The user explicitly prioritizes **professional CAD-grade surfaces and accessible touch operation together**, rather than accepting low-quality primitives to simplify the UI. [ADR-0001](ADR-0001_HYBRID_CAD_KERNEL.md) makes a hybrid B-Rep/NURBS + polygon modeling document the target, with Filament only as the tessellated display backend. Research identifies Open CASCADE Technology as a feasible *candidate* for Android arm64 evaluation and Manifold as an optional complementary triangle mesh Boolean library. **Neither is integrated or benchmarked in the current APK.** All previously proposed bevel/panel/loft features must consider exact CAD and mesh domains; prohibit silent loss of editability.

## Decision rule for adding features
Every proposed feature must identify a user task, a failure scenario it removes, an on-phone acceptance test, and whether it belongs to Easy, Advanced or both. Prioritize **reliability and control** ahead of visual effects, AI, or public sharing. Do not market features as implemented before an APK build, real-device gesture check, and GLB regression test.
