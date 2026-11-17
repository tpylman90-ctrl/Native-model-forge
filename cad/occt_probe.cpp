// Model Forge: OCCT Gate A desktop proof, separate from the Android app.
#include <BRepAlgoAPI_Cut.hxx>
#include <BRepCheck_Analyzer.hxx>
#include <BRepFilletAPI_MakeFillet.hxx>
#include <BRepGProp.hxx>
#include <BRepMesh_IncrementalMesh.hxx>
#include <BRepPrimAPI_MakeBox.hxx>
#include <BRepPrimAPI_MakeCylinder.hxx>
#include <BRepTools.hxx>
#include <BRep_Tool.hxx>
#include <GProp_GProps.hxx>
#include <IFSelect_ReturnStatus.hxx>
#include <Poly_Triangulation.hxx>
#include <STEPControl_Reader.hxx>
#include <STEPControl_Writer.hxx>
#include <TopExp_Explorer.hxx>
#include <TopoDS.hxx>
#include <TopoDS_Shape.hxx>
#include <TopLoc_Location.hxx>
#include <gp_Ax2.hxx>
#include <gp_Dir.hxx>
#include <gp_Pnt.hxx>
#include <cmath>
#include <cstdlib>
#include <iostream>
#include <stdexcept>
#include <string>

namespace {
void ensure(bool condition, const std::string& reason) {
  if (!condition) throw std::runtime_error(reason);
}
void checkSolid(const TopoDS_Shape& shape, const char* label) {
  ensure(!shape.IsNull(), std::string(label) + " is null");
  ensure(BRepCheck_Analyzer(shape).IsValid(), std::string(label) + " is invalid");
}
double volume(const TopoDS_Shape& shape) {
  GProp_GProps properties;
  BRepGProp::VolumeProperties(shape, properties);
  return properties.Mass();
}
int triangleCount(const TopoDS_Shape& shape) {
  int count=0;
  for(TopExp_Explorer e(shape, TopAbs_FACE);e.More();e.Next()) {
    TopLoc_Location location;
    auto mesh=BRep_Tool::Triangulation(TopoDS::Face(e.Current()), location);
    if (!mesh.IsNull()) count += mesh->NbTriangles();
  }
  return count;
}
}
int main(int argc,char** argv) {
  try {
    const TopoDS_Shape box=BRepPrimAPI_MakeBox(120.,80.,30.).Shape();
    checkSolid(box,"source box");
    ensure(std::abs(volume(box)-288000.0)<0.01,"box volume mismatch");

    BRepFilletAPI_MakeFillet fillet(box);
    int edges=0;
    for (TopExp_Explorer e(box,TopAbs_EDGE);e.More();e.Next()) {
      fillet.Add(3.,TopoDS::Edge(e.Current()));
      ++edges;
    }
    ensure(edges==12,"unexpected box topology");
    fillet.Build();
    ensure(fillet.IsDone(),"fillet kernel failure");
    TopoDS_Shape rounded=fillet.Shape();
    checkSolid(rounded,"rounded box");

    const gp_Ax2 axis(gp_Pnt(60.,40.,18.),gp_Dir(0.,0.,1.));
    const TopoDS_Shape cutter=BRepPrimAPI_MakeCylinder(axis,8.,14.).Shape();
    BRepAlgoAPI_Cut cut(rounded,cutter);
    cut.Build();
    ensure(cut.IsDone(),"pocket cut failed");
    TopoDS_Shape part=cut.Shape();
    checkSolid(part,"part after pocket");
    const double before=volume(rounded),after=volume(part);
    ensure(after>0. && after<before,"pocket did not remove material");

    BRepTools::Clean(part);
    BRepMesh_IncrementalMesh coarse(part,2.0,false,0.6,true);
    ensure(coarse.IsDone(),"coarse mesh failed");
    const int coarseCount=triangleCount(part);
    ensure(coarseCount>0,"coarse triangle count zero");

    BRepTools::Clean(part);
    BRepMesh_IncrementalMesh fine(part,0.20,false,0.20,true);
    ensure(fine.IsDone(),"fine mesh failed");
    const int fineCount=triangleCount(part);
    ensure(fineCount>=coarseCount,"fine mesh fewer triangles");

    const std::string file=argc>1?argv[1]:"model_forge_cad_probe.step";
    STEPControl_Writer writer;
    ensure(writer.Transfer(part,STEPControl_AsIs)==IFSelect_RetDone,"STEP transfer failed");
    ensure(writer.Write(file.c_str())==IFSelect_RetDone,"STEP write failed");
    STEPControl_Reader reader;
    ensure(reader.ReadFile(file.c_str())==IFSelect_RetDone,"STEP read failed");
    ensure(reader.TransferRoots()>0,"STEP transfer roots failed");
    TopoDS_Shape imported=reader.OneShape();
    checkSolid(imported,"STEP reimport");
    const double deviation=std::abs(volume(imported)-after);
    ensure(deviation<0.1,"STEP volume mismatch");

    std::cout<<"MODEL_FORGE_CAD_GATE_A PASS"
             <<" box_volume="<<volume(box)
             <<" filleted_volume="<<before
             <<" pocket_volume="<<after
             <<" coarse_triangles="<<coarseCount
             <<" fine_triangles="<<fineCount
             <<" STEP_volume_deviation="<<deviation<<'\n';
    return EXIT_SUCCESS;
  } catch (const std::exception& ex) {
    std::cerr<<"MODEL_FORGE_CAD_GATE_A FAIL "<<ex.what()<<'\n';
    return EXIT_FAILURE;
  }
}
