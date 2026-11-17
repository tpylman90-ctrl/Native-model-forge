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
    std::cout << "MODEL_FORGE_NATIVE_CORE: PASS exact dimensions / 12 triangles / hard normals / rejects invalid" << std::endl;
}
