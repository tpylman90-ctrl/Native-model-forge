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
    std::cout << "MODEL_FORGE_NATIVE_CORE: PASS exact dimensions / 12 triangles / hard normals / rejects invalid" << std::endl;
}
