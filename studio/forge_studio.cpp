#include "forge_studio.h"
#include "../app/src/main/cpp/document.hpp"
#include "../app/src/main/cpp/geometry.hpp"
#include <climits>
#include <cmath>
#include <cstring>
#include <iomanip>
#include <sstream>
#include <stdexcept>
#include <string>
#include <utility>
#include <vector>
namespace {
forge::Document current;
std::vector<float> packed;
std::string error,saved,state;
const forge::ModelObject& editable(const forge::Document& doc) {
    if(doc.objects().size()!=1 ||
       doc.objects()[0].kind!=forge::GeometryKind::PolygonMesh ||
       doc.objects()[0].sourceReference!="generated:profiled_box")
        throw std::invalid_argument("Studio v1 requires one native editable starter shape");
    return doc.objects()[0];
}
std::vector<float> makeMesh(const forge::Document& doc) {
    const auto& obj=editable(doc);
    forge::FaceFeatures features{};
    for(int i=0;i<6;++i) {
        const auto& f=obj.faceRegions[static_cast<std::size_t>(i)];
        auto& d=features[static_cast<std::size_t>(i)];
        d.kind=f.kind;d.width=static_cast<float>(f.width);
        d.height=static_cast<float>(f.height);
        d.offsetU=static_cast<float>(f.offsetU);
        d.offsetV=static_cast<float>(f.offsetV);
        d.depth=static_cast<float>(f.depthMetres);
        for(int j=0;j<4;++j)
            d.curves[static_cast<std::size_t>(j)]=static_cast<float>(f.curves[static_cast<std::size_t>(j)]);
    }
    const auto mesh=forge::multiFaceBox(
        static_cast<float>(obj.sizeMetres[0]),
        static_cast<float>(obj.sizeMetres[1]),
        static_cast<float>(obj.sizeMetres[2]),
        static_cast<float>(obj.topProfile[0]),
        static_cast<float>(obj.topProfile[1]),
        static_cast<float>(obj.topProfile[2]),features);
    std::vector<float> result;result.reserve(mesh.size()*6);
    for(const auto& v:mesh) result.insert(result.end(),{v.px,v.py,v.pz,v.nx,v.ny,v.nz});
    return result;
}
void accept(forge::Document&& next) {
    if(next.revision()>static_cast<std::uint64_t>(INT_MAX))
        throw std::invalid_argument("project revision exceeds Studio v1 limit");
    auto vertices=makeMesh(next); // preflight geometry before modifying project
    current=std::move(next);packed=std::move(vertices);error.clear();
}
template<typename F> int run(F fn) {
    try {
        forge::Document next=current;fn(next);
        accept(std::move(next));return 1;
    } catch(const std::exception& e) {error=e.what();return 0;}
}
void checkRevision(const forge::Document& doc,int expected) {
    if(expected<0 || static_cast<std::uint64_t>(expected)!=doc.revision())
        throw std::runtime_error("stale revision: reload model before editing");
}
std::string quote(const std::string& txt) {
    std::ostringstream o;const char* hex="0123456789abcdef";o << '"';
    for(unsigned char c:txt) {
        if(c=='"' || c=='\\') o << '\\' << static_cast<char>(c);
        else if(c<32) o << "\\u00" << hex[c>>4] << hex[c&15];
        else o << static_cast<char>(c);
    }
    o << '"';return o.str();
}
}
extern "C" {
const char* mf_last_error(){return error.c_str();}
int mf_new_box(float w,float h,float l){
    try {
        forge::Document next;
        next.insert(forge::GeometryKind::PolygonMesh,"Studio Body","generated:profiled_box",{{w,h,l}},0);
        next.clearHistory();accept(std::move(next));return 1;
    } catch(const std::exception& e){error=e.what();return 0;}
}
int mf_load_project(const char* input) {
    try {
        if(!input) throw std::invalid_argument("project data missing");
        const auto length=std::strlen(input);
        if(length>1024*1024) throw std::invalid_argument("Studio project exceeds 1 MiB");
        auto next=forge::Document::parse(std::string(input,length));
        next.clearHistory();accept(std::move(next));return 1;
    } catch(const std::exception& e){error=e.what();return 0;}
}
const char* mf_save_project(){saved=current.serialize();return saved.c_str();}
int mf_revision(){return current.revision()<=static_cast<std::uint64_t>(INT_MAX)?
    static_cast<int>(current.revision()):-1;}
int mf_reshape(float w,float h,float l,float tw,float tl,float sx,int revision){
    return run([=](forge::Document& doc){
        checkRevision(doc,revision);const auto id=editable(doc).id;
        doc.reshape(id,{{w,h,l}},{{tw,tl,sx}},doc.revision());
    });
}
int mf_set_region(int face,int kind,float width,float height,float offsetU,float offsetV,
                  float depth,float bottom,float right,float top,float left,int revision){
    return run([=](forge::Document& doc){
        checkRevision(doc,revision);
        forge::FaceRegion f;f.kind=kind;f.width=width;f.height=height;
        f.offsetU=offsetU;f.offsetV=offsetV;f.depthMetres=depth;
        f.curves={{bottom,right,top,left}};
        const auto id=editable(doc).id;
        doc.setFaceRegion(id,face,f,doc.revision());
    });
}
int mf_mirror(int face,int overwrite,int revision){
    return run([=](forge::Document& doc){
        checkRevision(doc,revision);const auto id=editable(doc).id;
        doc.mirrorFaceRegion(id,face,overwrite!=0,doc.revision());
    });
}
int mf_rename(const char* name,int revision){
    return run([=](forge::Document& doc){
        checkRevision(doc,revision);if(!name) throw std::invalid_argument("name missing");
        const auto id=editable(doc).id;doc.rename(id,name,doc.revision());
    });
}
int mf_undo(int revision){
    return run([=](forge::Document& doc){
        checkRevision(doc,revision);
        if(!doc.undo()) throw std::runtime_error("nothing to undo");
    });
}
int mf_redo(int revision){
    return run([=](forge::Document& doc){
        checkRevision(doc,revision);
        if(!doc.redo()) throw std::runtime_error("nothing to redo");
    });
}
const float* mf_vertex_data(){return packed.empty()?nullptr:packed.data();}
int mf_vertex_count(){return static_cast<int>(packed.size()/6);}
int mf_face_vertex_count(int face){
    if(face<0||face>=6||current.objects().size()!=1) return 0;
    const auto& f=current.objects()[0].faceRegions[static_cast<std::size_t>(face)];
    forge::FaceFeature tmp;tmp.kind=f.kind;tmp.depth=static_cast<float>(f.depthMetres);
    return forge::faceVertexCount(tmp);
}
const char* mf_state_json(){
    try {
        const auto& o=editable(current);std::ostringstream s;s << std::setprecision(17);
        s << "{\"revision\":" << current.revision() << ",\"id\":" << o.id
          << ",\"name\":" << quote(o.name)
          << ",\"size\":[" << o.sizeMetres[0] << ',' << o.sizeMetres[1] << ','
          << o.sizeMetres[2] << "],\"profile\":[" << o.topProfile[0] << ','
          << o.topProfile[1] << ',' << o.topProfile[2] << "],\"faces\":[";
        for(int i=0;i<6;++i) {
            const auto& f=o.faceRegions[static_cast<std::size_t>(i)];
            if(i) s << ',';
            s << "{\"kind\":" << f.kind << ",\"width\":" << f.width
              << ",\"height\":" << f.height << ",\"offset_u\":" << f.offsetU
              << ",\"offset_v\":" << f.offsetV << ",\"depth_m\":" << f.depthMetres
              << ",\"curves\":[" << f.curves[0] << ',' << f.curves[1] << ','
              << f.curves[2] << ',' << f.curves[3] << "]}";
        }
        s << "]}";state=s.str();error.clear();return state.c_str();
    } catch(const std::exception& e){error=e.what();return nullptr;}
}
}
