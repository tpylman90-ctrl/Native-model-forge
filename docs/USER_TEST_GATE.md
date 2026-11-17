# The five-minute beginner test

**Product acceptance test, not a statement of achieved on-device results.**

## Intended first-time flow

1. Install the native APK on a mid-range Android phone; launch without signing in or connecting to a network.
2. See a real 3D object immediately. Read instructions without a tutorial video or technical vocabulary.
3. Enter width, height and length; tap **CREATE BOX** once and see the dimensions reflected in an actual GLB mesh.
4. Inspect it using the right orbit control; zoom with two fingers; open and close the bottom tools without losing the view.
5. Tap **SAVE GLB**, save the file to Android Files, reopen via **OPEN GLB** and confirm the model loads, all without external apps.
6. Adjust top width to 60%, top length to 80% and shift to 20%; tap **APPLY SHAPE** and inspect taper and lean. Tap **UNDO** and **REDO** and verify the profile is restored.
7. Export the modified model, reopen it, confirm the modified geometry is preserved and note that reopened GLBs are still preview-only.
8. Confirm the app clearly says imported GLBs are preview-only in this first milestone.

## Benchmarks before a broader release

- At least 8/10 first-time testers should create and save a model in five minutes without intervention; measure failed taps and explanatory text they needed.
- 10-minute sessions should have zero unexplained crashes or lost documents across at least three actual ARM64 Android devices.
- Verify dimensions, face winding, normals, material base color, and GLB validity independently of the in-app viewport.
- Studio lighting and touch targets must remain legible on a 6-inch budget handset.
- Community is out of scope in v0.1; uploads require moderation, explicit public/private selections, license and attribution controls, takedowns and abuse-reporting before any public launch.

## User-facing exclusions for foundation

Do not market as full mesh editor yet: imported models are not currently editable; vertex/edge/face topology and *persistent* undo/redo across sessions are not implemented; only in-session parametric shaping is reversible. AI bridge and Python `bpy` compatibility are not available. This is a working native app foundation and creation proof, not Blender parity.
