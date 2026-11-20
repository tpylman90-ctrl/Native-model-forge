#!/usr/bin/env bash
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
command -v em++ >/dev/null || { echo "Emscripten (em++) required" >&2; exit 1; }
em++ -std=c++17 -O2 \
  studio/forge_studio.cpp app/src/main/cpp/document.cpp app/src/main/cpp/geometry.cpp \
  -s MODULARIZE=1 -s EXPORT_ES6=1 -s EXPORT_NAME=createForgeCore \
  -s ENVIRONMENT=web -s ALLOW_MEMORY_GROWTH=1 \
  -s EXPORTED_RUNTIME_METHODS='["ccall","HEAPF32"]' \
  -s EXPORTED_FUNCTIONS='["_mf_last_error","_mf_new_box","_mf_load_project","_mf_save_project","_mf_state_json","_mf_revision","_mf_reshape","_mf_set_region","_mf_mirror","_mf_rename","_mf_undo","_mf_redo","_mf_vertex_data","_mf_vertex_count","_mf_face_vertex_count"]' \
  -o studio/web/forge_core.js
test -s studio/web/forge_core.wasm
echo "Studio WASM compiled from Android-native document and mesh sources"
