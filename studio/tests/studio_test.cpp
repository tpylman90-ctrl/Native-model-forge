#include "../forge_studio.h"
#include <cassert>
#include <cmath>
#include <cstring>
#include <iostream>
#include <string>
int main(){
    assert(mf_new_box(4.5f,1.8f,8.4f));
    assert(mf_revision()==1 && mf_vertex_count()==36);
    for(int face=0;face<6;++face) assert(mf_face_vertex_count(face)==6);
    for(int i=0;i<mf_vertex_count()*6;++i) assert(std::isfinite(mf_vertex_data()[i]));
    const auto baseline=std::string(mf_save_project());
    assert(mf_reshape(4.6f,1.9f,8.4f,1.15f,0.92f,0.03f,1));
    assert(mf_revision()==2);
    assert(!mf_reshape(5.f,1.f,3.f,1.f,1.f,0.f,1));
    assert(std::string(mf_last_error()).find("stale")!=std::string::npos);
    assert(mf_set_region(4,2,0.47f,0.61f,0.f,0.f,0.25f,0.08f,-0.06f,0.12f,0.f,2));
    assert(mf_vertex_count()==426 && mf_face_vertex_count(4)==396);
    const auto detailed=std::string(mf_save_project());
    assert(std::string(mf_state_json()).find("\"kind\":2")!=std::string::npos);
    assert(!mf_set_region(4,2,0.99f,0.61f,0.f,0.f,0.25f,0.f,0.f,0.f,0.f,3));
    assert(mf_revision()==3 && std::string(mf_save_project())==detailed);
    assert(mf_vertex_count()==426);
    assert(mf_mirror(4,0,3));
    assert(mf_face_vertex_count(5)==396 && mf_vertex_count()==816);
    assert(mf_undo(4) && mf_vertex_count()==426);
    assert(mf_redo(5) && mf_vertex_count()==816);
    assert(mf_rename("Test Hover Hull",6));
    assert(std::string(mf_state_json()).find("Test Hover Hull")!=std::string::npos);
    const auto saved=std::string(mf_save_project());
    assert(saved.find("MFORGE_DOC 5\n")==0);
    assert(mf_load_project(saved.c_str()));
    assert(std::string(mf_save_project())==saved && mf_vertex_count()==816);
    assert(!mf_undo(mf_revision()));
    assert(!mf_load_project("not a model"));
    assert(std::string(mf_save_project())==saved);
    assert(mf_load_project(baseline.c_str()) && mf_vertex_count()==36);
    assert(!mf_new_box(-1.f,1.f,1.f) && mf_vertex_count()==36);
    std::cout<<"STUDIO_NATIVE_WASM_BRIDGE: PASS native project, shape, curved region, mirror, undo, validation\n";
}
