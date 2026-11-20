#pragma once
// Portable native/WASM C ABI. Same Document and geometry kernel as Android.
#ifdef __cplusplus
extern "C" {
#endif
const char* mf_last_error();
int mf_new_box(float width,float height,float length);
int mf_load_project(const char* text);
const char* mf_save_project();
const char* mf_state_json();
int mf_revision();
int mf_reshape(float width,float height,float length,float top_width,float top_length,float shift_x,int expected_revision);
int mf_set_region(int face,int kind,float width,float height,float offset_u,float offset_v,float depth,float bottom,float right,float top,float left,int expected_revision);
int mf_mirror(int face,int overwrite,int expected_revision);
int mf_rename(const char* name,int expected_revision);
int mf_undo(int expected_revision);
int mf_redo(int expected_revision);
const float* mf_vertex_data(); // position xyz, normal xyz; owned by native core
int mf_vertex_count();
int mf_face_vertex_count(int face);
#ifdef __cplusplus
}
#endif
