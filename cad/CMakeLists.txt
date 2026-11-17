cmake_minimum_required(VERSION 3.22)
project(model_forge_occt_gate_a LANGUAGES CXX)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
find_package(OpenCASCADE REQUIRED)
add_executable(cad_probe occt_probe.cpp)
target_include_directories(cad_probe PRIVATE ${OpenCASCADE_INCLUDE_DIR})
# Ubuntu's default OpenCASCADE_LIBRARIES includes optional Draw GUI libraries.
# Link only the precise headless modeling and STEP components this proof needs.
target_link_libraries(cad_probe PRIVATE
  TKSTEP TKSTEPBase TKSTEPAttr TKXSBase TKSTL
  TKFillet TKBO TKBool TKMesh TKShHealing TKPrim TKTopAlgo
  TKBRep TKGeomBase TKG3d TKG2d TKMath TKernel)
enable_testing()
add_test(NAME occt_precision_solid COMMAND cad_probe)
