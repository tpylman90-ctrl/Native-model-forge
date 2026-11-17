#include "geometry.hpp"
#include <cmath>
#include <stdexcept>

namespace forge {
namespace {
using P = std::array<float,3>;
P add(P a, P b) { return {a[0]+b[0], a[1]+b[1], a[2]+b[2]}; }
P neg(P a) { return {-a[0],-a[1],-a[2]}; }
void face(std::vector<Vertex>& out, P c, P u, P v, P n) {
    const P a=add(add(c,neg(u)),neg(v));
    const P b=add(add(c,u),neg(v));
    const P cc=add(add(c,u),v);
    const P d=add(add(c,neg(u)),v);
    for (const P& p : {a,b,cc,a,cc,d})
        out.push_back({p[0],p[1],p[2],n[0],n[1],n[2]});
}
}
std::vector<Vertex> box(float width, float height, float length) {
    if (!std::isfinite(width) || !std::isfinite(height) || !std::isfinite(length) ||
        width < 0.01f || height < 0.01f || length < 0.01f ||
        width > 10000.f || height > 10000.f || length > 10000.f)
        throw std::invalid_argument("dimensions outside finite 0.01..10000 units");
    const float x=width/2.f, y=height/2.f, z=length/2.f;
    std::vector<Vertex> out;
    out.reserve(36);
    face(out,{0,0,z}, {x,0,0}, {0,y,0}, {0,0,1});
    face(out,{0,0,-z},{-x,0,0},{0,y,0}, {0,0,-1});
    face(out,{x,0,0}, {0,0,-z}, {0,y,0}, {1,0,0});
    face(out,{-x,0,0},{0,0,z},  {0,y,0}, {-1,0,0});
    face(out,{0,y,0}, {x,0,0},  {0,0,-z},{0,1,0});
    face(out,{0,-y,0},{x,0,0},  {0,0,z}, {0,-1,0});
    return out;
}
}
