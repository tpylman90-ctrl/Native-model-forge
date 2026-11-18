#include "../geometry.hpp"
#include <cassert>
#include <cmath>
#include <iostream>
#include <stdexcept>

int main() {
    auto v=forge::box(4.5f,2.0f,8.4f);
    assert(v.size()==36);
    float maxx=0, maxy=0, maxz=0;
    for(const auto& p:v) {
        maxx=std::fmax(maxx,std::fabs(p.px));
        maxy=std::fmax(maxy,std::fabs(p.py));
        maxz=std::fmax(maxz,std::fabs(p.pz));
        assert(std::fabs(p.nx*p.nx+p.ny*p.ny+p.nz*p.nz-1.f)<1e-5f);
    }
    assert(std::fabs(maxx-2.25f)<1e-5f);
    assert(std::fabs(maxy-1.0f)<1e-5f);
    assert(std::fabs(maxz-4.2f)<1e-5f);
    bool rejected=false;
    try { forge::box(0,1,1); } catch(const std::invalid_argument&) { rejected=true; }
    assert(rejected);
    auto shaped=forge::profiledBox(4.f,2.f,6.f,0.6f,0.8f,0.2f);
    assert(shaped.size()==36);
    float topMinX=100.f,topMaxX=-100.f;
    for(size_t i=0;i<shaped.size();i+=3) {
        const auto& a=shaped[i];const auto& b=shaped[i+1];const auto& c=shaped[i+2];
        const float ux=b.px-a.px,uy=b.py-a.py,uz=b.pz-a.pz;
        const float vx=c.px-a.px,vy=c.py-a.py,vz=c.pz-a.pz;
        assert((uy*vz-uz*vy)*a.nx + (uz*vx-ux*vz)*a.ny +
               (ux*vy-uy*vx)*a.nz > 0.f);
    }
    for(const auto& point:shaped) {
        assert(std::fabs(point.nx*point.nx+point.ny*point.ny+point.nz*point.nz-1.f)<1e-5f);
        if(std::fabs(point.py-1.f)<1e-5f) {
            topMinX=std::fmin(topMinX,point.px);
            topMaxX=std::fmax(topMaxX,point.px);
        }
    }
    assert(std::fabs(topMaxX-topMinX-2.4f)<1e-5f);
    assert(std::fabs((topMaxX+topMinX)*0.5f-0.8f)<1e-5f);
    rejected=false;
    try { forge::profiledBox(4,2,6,0.01f,1,0); }
    catch(const std::invalid_argument&) { rejected=true; }
    assert(rejected);
    for(int face=0;face<6;++face) {
        for(float depth:{0.32f,-0.22f}) {
            auto mesh=forge::faceExtrudedBox(4.f,2.f,6.f,0.8f,1.1f,0.1f,face,depth);
            assert(mesh.size()==84);
            // The cap is the final two triangles and has the correct outward
            // normal for both a raised extrusion and an inward pocket.
            const auto& original=shaped; (void)original;
            for(std::size_t i=0;i<mesh.size();i+=3) {
                const auto& a=mesh[i],&b=mesh[i+1],&c=mesh[i+2];
                const float ux=b.px-a.px,uy=b.py-a.py,uz=b.pz-a.pz;
                const float vx=c.px-a.px,vy=c.py-a.py,vz=c.pz-a.pz;
                const float cross=(uy*vz-uz*vy)*a.nx +
                    (uz*vx-ux*vz)*a.ny+(ux*vy-uy*vx)*a.nz;
                assert(std::isfinite(cross) && cross>0.0f);
            }
            for(std::size_t i=78;i<84;++i) {
                assert(std::fabs(mesh[i].nx*mesh[i].nx+
                    mesh[i].ny*mesh[i].ny+mesh[i].nz*mesh[i].nz-1.f)<1e-5f);
            }
        }
    }
    assert(forge::faceExtrudedBox(2,1,3,1,1,0,-1,0).size()==36);
    rejected=false;
    try { forge::faceExtrudedBox(2,1,3,1,1,0,6,0.2f); }
    catch(const std::invalid_argument&) { rejected=true; }
    assert(rejected);
    rejected=false;
    try { forge::faceExtrudedBox(2,1,3,1,1,0,0,-0.6f); }
    catch(const std::invalid_argument&) { rejected=true; }
    assert(rejected);
    forge::FaceFeatures features{};
    features[0]={0,0.35f,0.65f,-0.3f,0.3f,0.25f};
    features[4]={1,0.58f,0.48f,0.2f,-0.4f,-0.18f};
    const auto multi=forge::multiFaceBox(4.f,2.f,6.f,1.f,1.f,0.f,features);
    assert(multi.size()==54+390+4*6);
    for(std::size_t i=0;i<multi.size();i+=3) {
        const auto& a=multi[i],&b=multi[i+1],&c=multi[i+2];
        const float ux=b.px-a.px,uy=b.py-a.py,uz=b.pz-a.pz;
        const float vx=c.px-a.px,vy=c.py-a.py,vz=c.pz-a.pz;
        const float area=(uy*vz-uz*vy)*a.nx +
            (uz*vx-ux*vz)*a.ny+(ux*vy-uy*vx)*a.nz;
        assert(std::isfinite(area) && area>0.f);
    }
    for(int face=0;face<6;++face) {
        forge::FaceFeatures only{};
        only[face]={1,0.33f,0.75f,-0.6f,0.6f,0.12f};
        auto oval=forge::multiFaceBox(4.f,2.f,6.f,0.65f,1.2f,0.2f,only);
        assert(oval.size()==390+5*6);
    }
    assert(forge::faceVertexCount(features[0])==54);
    assert(forge::faceVertexCount(features[4])==390);
    rejected=false;
    features[2]={0,0.92f,0.2f,0.f,0.f,0.1f};
    try { forge::multiFaceBox(4,2,6,1,1,0,features); }
    catch(const std::invalid_argument&) {rejected=true;}
    assert(rejected);
    for(int side=0;side<4;++side) {
        forge::FaceFeatures curved{};
        auto& f=curved[4];
        f.kind=2; f.width=0.48f; f.height=0.62f; f.depth=0.24f;
        f.curves[static_cast<std::size_t>(side)]=0.18f;
        auto mesh=forge::multiFaceBox(4,2,6,1,1,0,curved);
        assert(mesh.size()==390+5*6);
        for(std::size_t i=0;i<mesh.size();i+=3) {
            const auto& v=mesh[i],&w=mesh[i+1],&x=mesh[i+2];
            const float ax=w.px-v.px,ay=w.py-v.py,az=w.pz-v.pz;
            const float bx=x.px-v.px,by=x.py-v.py,bz=x.pz-v.pz;
            const float signedArea=(ay*bz-az*by)*v.nx+
                (az*bx-ax*bz)*v.ny+(ax*by-ay*bx)*v.nz;
            assert(std::isfinite(signedArea) && signedArea>0.f);
        }
        curved[4].curves[static_cast<std::size_t>(side)]=-0.22f;
        bool blocked=false;
        try { forge::multiFaceBox(4,2,6,1,1,0,curved); }
        catch(const std::invalid_argument&) {blocked=true;}
        // Negative bulges must respect the available outer-face margin.
        assert(blocked || forge::multiFaceBox(4,2,6,1,1,0,curved).size()==420);
    }
    std::cout<<"MODEL_FORGE_CURVE_KERNEL: PASS quadratic face edges and normals"<<std::endl;
    std::cout<<"MODEL_FORGE_MULTIFACE: PASS independent faces and rectangular/oval geometry"<<std::endl;
    std::cout << "MODEL_FORGE_FACE_EXTRUDE: PASS 6 faces, positive/negative inset extrusions, normals and bounds" << std::endl;
    std::cout << "MODEL_FORGE_NATIVE_CORE: PASS exact dimensions / 12 triangles / hard normals / rejects invalid" << std::endl;
}
