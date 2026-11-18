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

namespace forge {
namespace {
using Point = std::array<float,3>;
Point delta(Point a,Point b) { return {a[0]-b[0],a[1]-b[1],a[2]-b[2]}; }
Point cross3(Point a,Point b) {
    return {a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]};
}
void profileQuad(std::vector<Vertex>& out,Point a,Point b,Point c,Point d) {
    Point normal=cross3(delta(b,a),delta(c,a));
    float len=std::sqrt(normal[0]*normal[0]+normal[1]*normal[1]+normal[2]*normal[2]);
    if (!std::isfinite(len) || len < 1e-9f) throw std::invalid_argument("degenerate surface");
    for(Point v:{a,b,c,a,c,d})
        out.push_back({v[0],v[1],v[2],normal[0]/len,normal[1]/len,normal[2]/len});
}
}
std::vector<Vertex> profiledBox(float w,float h,float l,float topW,float topL,float shiftX) {
    // Delegate dimension validation to the existing standalone box kernel.
    box(w,h,l);
    if(!std::isfinite(topW)||!std::isfinite(topL)||!std::isfinite(shiftX)||
       topW<0.35f||topW>1.75f||topL<0.35f||topL>1.75f||
       shiftX < -0.4f||shiftX > 0.4f)
        throw std::invalid_argument("profile outside allowed scale or shift");
    const float x=w/2.f,y=h/2.f,z=l/2.f,s=shiftX*w;
    const Point blb={-x,-y,-z},brb={x,-y,-z},blf={-x,-y,z},brf={x,-y,z};
    const Point tlb={-x*topW+s,y,-z*topL},trb={x*topW+s,y,-z*topL};
    const Point tlf={-x*topW+s,y,z*topL},trf={x*topW+s,y,z*topL};
    std::vector<Vertex> out;out.reserve(36);
    profileQuad(out,blf,brf,trf,tlf);
    profileQuad(out,brb,blb,tlb,trb);
    profileQuad(out,brf,brb,trb,trf);
    profileQuad(out,blb,blf,tlf,tlb);
    profileQuad(out,tlf,trf,trb,tlb);
    profileQuad(out,blb,brb,brf,blf);
    return out;
}
}

namespace forge {
std::vector<Vertex> faceExtrudedBox(float w,float h,float l,
    float tw,float tl,float shift,int faceIndex,float depth) {
    const auto base = profiledBox(w,h,l,tw,tl,shift);
    if(faceIndex == -1 && depth == 0.f) return base;
    if(faceIndex < 0 || faceIndex > 5 || !std::isfinite(depth))
        throw std::invalid_argument("extrusion requires a valid face and finite depth");
    const float minDimension = std::fmin(w, std::fmin(h,l));
    if(depth < -0.40f * minDimension ||
       depth > 2.f * minDimension)
        throw std::invalid_argument("extrusion depth exceeds safe model bounds");
    if(std::fabs(depth) < 1e-6f) return base;
    // Preserve adjacent faces. Replace only the selected face with an inset
    // border, four joining walls, and a translated cap. This also produces
    // a proper recessed pocket for negative depths rather than overlapping
    // coplanar neighbor faces.
    std::vector<Vertex> result;
    result.reserve(84);
    for(int faceId=0;faceId<6;++faceId) {
        if(faceId == faceIndex) continue;
        const std::size_t start = static_cast<std::size_t>(faceId) * 6;
        result.insert(result.end(),base.begin()+start,base.begin()+start+6);
    }
    using Point = std::array<float,3>;
    const std::size_t start=static_cast<std::size_t>(faceIndex)*6;
    const std::array<int,4> corners{{0,1,2,5}};
    std::array<Point,4> outer;
    Point center{{0.f,0.f,0.f}};
    for(int i=0;i<4;++i) {
        const Vertex& v=base[start+static_cast<std::size_t>(corners[i])];
        outer[i]={{v.px,v.py,v.pz}};
        for(int c=0;c<3;++c)center[c]+=outer[i][c]*0.25f;
    }
    const Vertex& normal=base[start];
    const Point n{{normal.nx,normal.ny,normal.nz}};
    std::array<Point,4> inset,cap;
    for(int i=0;i<4;++i) {
        for(int c=0;c<3;++c) {
            // A deliberate inset prevents a full-face recess from coinciding
            // with and degenerating the original neighboring boundaries.
            inset[i][c]=center[c]+0.82f*(outer[i][c]-center[c]);
            cap[i][c]=inset[i][c]+n[c]*depth;
        }
    }
    for(int i=0;i<4;++i) {
        const int j=(i+1)%4;
        profileQuad(result,outer[i],outer[j],inset[j],inset[i]);
    }
    for(int i=0;i<4;++i) {
        const int j=(i+1)%4;
        profileQuad(result,inset[i],inset[j],cap[j],cap[i]);
    }
    profileQuad(result,cap[0],cap[1],cap[2],cap[3]);
    if(result.size()!=84u) throw std::runtime_error("extrusion topology error");
    return result;
}
}
