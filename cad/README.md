# Native CAD kernel — isolated Gate A

This is an **experimental desktop precision test**, not part of the distributed Android APK. The app currently ships the established lightweight C++ polygon mesh engine and Filament renderer.

Ubuntu 24.04 test dependencies: Open CASCADE Technology (OCCT) 7.6.3 distro packages.

```bash
sudo apt-get install libocct-modeling-algorithms-dev libocct-modeling-data-dev libocct-data-exchange-dev
cmake -S cad -B build/cad -DCMAKE_BUILD_TYPE=Release
cmake --build build/cad --parallel 2
build/cad/cad_probe build/cad/part.step
```

Expected Gate A proof: dimensional 120 x 80 x 30 **millimeter** exact B-Rep solid, 3 mm edge fillets, cylindrical subtractive recessed pocket, B-Rep solid validity checks, two tessellation qualities from the same shape, STEP write/read and volume comparison. Model Forge's current GLB mesh uses meters; production integration must convert explicit units.

CI: .github/workflows/cad-kernel.yml runs independently of the Android workflow when cad/ or that workflow changes and produces a STEP artifact. This **does not** prove OCCT compiles or performs acceptably in Android. Gate B will pin an upstream OCCT source release, cross-compile to ARM64 NDK, measure library/APK memory footprint, and audit LGPL-2.1 special-exception and transitive dependencies before shipping.

OCCT official source: https://github.com/Open-Cascade-SAS/OCCT
