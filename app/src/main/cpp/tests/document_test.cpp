#include "../document.hpp"
#include <cassert>
#include <cmath>
#include <iostream>
#include <stdexcept>
#include <string>

int main() {
    forge::Document doc;
    using forge::GeometryKind;
    assert(doc.revision()==0);
    const auto body=doc.insert(GeometryKind::PolygonMesh, "Body Shell",
                               "generated:profiled_box", {4.5,2.,8.4}, 0);
    assert(body==1 && doc.revision()==1);
    // A CAD object is represented by a *reference*, not a fake converted triangle mesh.
    const auto cad=doc.insert(GeometryKind::CadBrep, "Future CAD",
                              "brep:external-demo-payload", {0.12,0.08,0.03}, 1);
    assert(cad==2 && doc.objects()[1].kind==GeometryKind::CadBrep);
    doc.resize(body, {4.8,2.1,8.4},doc.revision());
    assert(doc.objects()[0].sizeMetres[0]==4.8);

    const std::string checkpoint=doc.serialize();
    bool rejected=false;
    try {doc.resize(body,{0,1,1},doc.revision());}
    catch(const std::invalid_argument&) {rejected=true;}
    assert(rejected && doc.serialize()==checkpoint); // rejected edit is atomic
    rejected=false;
    try {doc.rename(body,"stale",0);}
    catch(const std::runtime_error&) {rejected=true;}
    assert(rejected && doc.serialize()==checkpoint);

    auto reopened=forge::Document::parse(checkpoint);
    assert(reopened.serialize()==checkpoint);
    assert(reopened.objects().size()==2);
    assert(reopened.objects()[1].kind==GeometryKind::CadBrep);
    assert(reopened.objects()[1].sourceReference=="brep:external-demo-payload");

    assert(doc.undo());
    assert(doc.objects()[0].sizeMetres[0]==4.5);
    assert(doc.redo());
    assert(doc.objects()[0].sizeMetres[0]==4.8);
    doc.erase(cad,doc.revision());
    assert(doc.objects().size()==1);
    assert(doc.undo());
    assert(doc.objects().size()==2);
    doc.undo(); // Undo previous resize. ID allocation still monotonic.
    const auto added=doc.insert(GeometryKind::PolygonMesh,"New","generated:box",{1,1,1},doc.revision());
    assert(added>cad && !doc.redo());
    rejected=false;
    try {forge::Document::parse("MFORGE_DOC 999\n");}
    catch(const std::invalid_argument&) {rejected=true;}
    assert(rejected);
    std::cout<<"MODEL_FORGE_DOCUMENT: PASS revision guard, stable IDs, hybrid kinds, rollback, save/load, undo/redo\n";
}
