# Public development signing policy (2026-11-17)

## Decision

Use a **publicly pinned DEVELOPMENT-ONLY signing key** for iterative, non-sensitive phone testing, under a **distinct Android application ID**. This minimizes uninstall/reinstall friction without trusting the public key for a real user-facing release.

- Test package: `com.modelforge.nativeeditor.dev`
- Name: `Model Forge DEV`
- Version starting point: `versionCode 7`, `0.4.1-direct-top-handles-public-dev`
- Keystore (FULL PRIVATE KEY, OPENLY PUBLISHED): `signing/model-forge-public-dev.p12.b64`
- Alias: `forge-public-dev`
- Certificate SHA-256: `46:DB:53:3A:11:04:A5:5E:FC:92:E7:C3:84:E8:3C:03:D8:B7:DD:AF:A9:19:38:17:66:5B:77:66:16:D0:2B:EA`
- Keystore format: PKCS12 / EC secp256r1 / SHA256withECDSA
- Generation: static one-time key on 2026-11-17. It must **not** be regenerated for future DEV builds.

The Gradle debug configuration decodes the checked-in base64 into ignored build output and signs with this exact keystore. CI validates both the signer certificate and package ID using Android build-tools `apksigner` and `aapt`. The release build is not configured to use the public key.

## Why not use this signing identity for the real app?

The published b64 file is **not** a harmless public certificate: it includes the **signing private key** and its password is deliberately visible in the public build script. Anyone can create an apparently signed update for `com.modelforge.nativeeditor.dev`. The security model is therefore inadequate for real users. Never put sensitive data in the DEV app or distribute it as a trustworthy production release.

Android app updates require an unchanged package/application ID and matching signing certificate (or a supported rotation lineage). Ordinary new private keys cannot transparently update a public-key-signed APK. Production signing will use a **different package ID**, `com.modelforge.nativeeditor`, and a secure private signing key from a controlled secret store. Keeping distinct packages allows development and production to coexist.

## Upgrade behavior

- Older debug APKs through v0.4: package `com.modelforge.nativeeditor`, ephemeral GitHub runner debug certificates. They cannot update in place via v0.4.1 DEV; the DEV app installs separately.
- v0.4.1 DEV and later DEV APKs: same `.dev` package and signing cert, so install newer version over installed DEV and retain app-local data (assuming no incompatible data migrations).
- Future production app: separate signed package `com.modelforge.nativeeditor`; does not replace DEV and does not automatically carry over local projects. Use explicit export/import when available.

## Required checks for every APK

1. Native and Android CI compile/tests pass.
2. `apksigner verify --print-certs` reports pinned certificate SHA-256.
3. `aapt dump badging` reports `.dev` package and expected versionCode.
4. Test an update install on the same phone without uninstalling. Verify local project recovery survives.
5. Sign the next build using the **same published key** and repeat install. Never silently change it.
6. Prior to wider distribution: generate a fresh private release keystore, store it in a restricted secret manager, gate release signing on secrets and review, and plan project migration.

This public development signer is a stopgap for trusted developer testing only, not a production key-management solution.
