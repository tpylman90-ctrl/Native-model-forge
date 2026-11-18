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

    // Parametric dimensions and profile are one native transaction.
    const auto beforeProfile=doc.serialize();
    doc.reshape(body,{4.8,2.1,8.4},{1.25,0.8,-0.17},doc.revision());
    assert(doc.objects()[0].topProfile[0]==1.25);
    assert(doc.objects()[0].topProfile[2]==-0.17);
    const std::string profileCheckpoint=doc.serialize();
    bool invalidProfile=false;
    try {doc.reshape(body,{4.8,2.1,8.4},{0.,1.,0.},doc.revision());}
    catch(const std::invalid_argument&) {invalidProfile=true;}
    assert(invalidProfile && doc.serialize()==profileCheckpoint);
    assert(doc.undo() && doc.objects()[0].topProfile[0]==1.0);
    assert(doc.redo() && doc.objects()[0].topProfile[0]==1.25);
    assert(doc.serialize()!=beforeProfile);
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
    assert(reopened.objects()[0].topProfile[0]==1.25);
    const auto legacy=forge::Document::parse(
        "MFORGE_DOC 1\n1 2 1\n1 1 \"Legacy\" \"generated:profiled_box\" 2 1 3\n");
    assert(legacy.objects()[0].topProfile[0]==1.0);

    assert(doc.undo());
    assert(doc.objects()[0].topProfile[0]==1.0);
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
    forge::Document extrudeDoc;
    const auto shape=extrudeDoc.insert(GeometryKind::PolygonMesh, "Extruded",
        "generated:profiled_box", {2.,1.,3.},extrudeDoc.revision());
    extrudeDoc.clearHistory();
    forge::FaceRegion top,front;
    top.kind=0; top.width=0.45; top.height=0.6; top.offsetU=0.4;
    top.depthMetres=0.3;
    front.kind=1; front.width=0.55; front.height=0.35;
    front.offsetV=-0.3;front.depthMetres=-0.2;
    extrudeDoc.setFaceRegion(shape,4,top,extrudeDoc.revision());
    extrudeDoc.setFaceRegion(shape,0,front,extrudeDoc.revision());
    assert(extrudeDoc.objects()[0].faceRegions[4].depthMetres==0.3);
    assert(extrudeDoc.objects()[0].faceRegions[0].kind==1);
    const auto saved=extrudeDoc.serialize();
    assert(saved.find("MFORGE_DOC 5")==0);
    auto restored=forge::Document::parse(saved);
    assert(restored.serialize()==saved);
    assert(restored.objects()[0].faceRegions[0].depthMetres==-0.2);
    auto bad=front;bad.width=0.99;
    bool rejectedRegion=false;
    try { extrudeDoc.setFaceRegion(shape,2,bad,extrudeDoc.revision()); }
    catch(const std::invalid_argument&) { rejectedRegion=true; }
    assert(rejectedRegion && extrudeDoc.serialize()==saved);
    bad=front;bad.width=0.6;bad.depthMetres=-0.9;
    rejectedRegion=false;
    try { extrudeDoc.setFaceRegion(shape,2,bad,extrudeDoc.revision()); }
    catch(const std::invalid_argument&) { rejectedRegion=true; }
    assert(rejectedRegion && extrudeDoc.serialize()==saved);
    assert(extrudeDoc.undo() && extrudeDoc.objects()[0].faceRegions[0].kind==-1);
    assert(extrudeDoc.objects()[0].faceRegions[4].kind==0);
    assert(extrudeDoc.redo() && extrudeDoc.objects()[0].faceRegions[0].kind==1);
    forge::FaceRegion curved=top;
    curved.kind=2;
    curved.curves={{0.12,-0.06,0.15,0.0}};
    extrudeDoc.setFaceRegion(shape,4,curved,extrudeDoc.revision());
    assert(extrudeDoc.objects()[0].faceRegions[4].kind==2);
    const auto curvedText=extrudeDoc.serialize();
    assert(forge::Document::parse(curvedText).objects()[0].faceRegions[4].curves[2]==0.15);
    assert(extrudeDoc.undo() && extrudeDoc.objects()[0].faceRegions[4].kind==0);
    assert(extrudeDoc.redo() && extrudeDoc.objects()[0].faceRegions[4].kind==2);
    auto badCurve=curved;
    badCurve.curves[0]=0.24;
    bool blockedCurve=false;
    try {extrudeDoc.setFaceRegion(shape,3,badCurve,extrudeDoc.revision());}
    catch(const std::invalid_argument&) {blockedCurve=true;}
    assert(blockedCurve);
    forge::FaceRegion removed;
    extrudeDoc.setFaceRegion(shape,0,removed,extrudeDoc.revision());
    assert(extrudeDoc.objects()[0].faceRegions[0].kind==-1);
    assert(extrudeDoc.objects()[0].faceRegions[4].kind==2);
    const auto legacyV2=forge::Document::parse(
        "MFORGE_DOC 2\n1 2 1\n1 1 \"Legacy\" \"generated:profiled_box\" 2 1 3 1 1 0\n");
    assert(legacyV2.objects()[0].faceRegions[4].kind==-1);
    const auto legacyV3=forge::Document::parse(
        "MFORGE_DOC 3\n1 2 1\n1 1 \"Legacy\" \"generated:profiled_box\" 2 1 3 1 1 0 4 0.25\n");
    assert(legacyV3.objects()[0].faceRegions[4].kind==0);
    assert(legacyV3.objects()[0].faceRegions[4].depthMetres==0.25);
    assert(legacyV3.objects()[0].faceRegions[4].width==0.82);
    // A genuine v4 six-value region record migrates without curve fields.
    const auto v4=forge::Document::parse(R"(MFORGE_DOC 4
1 2 1
1 1 "Legacy" "generated:profiled_box" 2 1 3 1 1 0
 -1 0.55 0.55 0 0 0 -1 0.55 0.55 0 0 0
 -1 0.55 0.55 0 0 0 -1 0.55 0.55 0 0 0
 0 0.45 0.6 0 0 0.25 -1 0.55 0.55 0 0 0
)");
    assert(v4.objects()[0].faceRegions[4].kind==0);
    assert(v4.objects()[0].faceRegions[4].curves[0]==0.0);
    std::cout<<"MODEL_FORGE_DOCUMENT: PASS revision guard, stable IDs, hybrid kinds, rollback, save/load, undo/redo\n";
}
