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
namespace {
using Point3 = std::array<float,3>;
Point3 pointAt(const std::array<Point3,4>& p, float u,float v) {
    // Bilinear interpolation remains on the coplanar trapezoid face.
    Point3 out{{0.f,0.f,0.f}};
    const float w[4] = {(1-u)*(1-v),u*(1-v),u*v,(1-u)*v};
    for(int i=0;i<4;++i)
        for(int j=0;j<3;++j) out[j]+=p[i][j]*w[i];
    return out;
}
void triangle(std::vector<Vertex>& out,Point3 a,Point3 b,Point3 c) {
    const auto ab=delta(b,a),ac=delta(c,a);
    const auto n=cross3(ab,ac);
    const float len=std::sqrt(n[0]*n[0]+n[1]*n[1]+n[2]*n[2]);
    if(!std::isfinite(len) || len<1e-9f)
        throw std::invalid_argument("degenerate face triangle");
    for(const auto& p:{a,b,c})
        out.push_back({p[0],p[1],p[2],n[0]/len,n[1]/len,n[2]/len});
}
}
void validateFaceFeature(const FaceFeature& f,float minDimension) {
    if(f.kind < -1 || f.kind>2 ||
       !std::isfinite(f.width)||!std::isfinite(f.height)||
       !std::isfinite(f.offsetU)||!std::isfinite(f.offsetV)||
       !std::isfinite(f.depth)||
       f.width<0.15f||f.width>0.85f||
       f.height<0.15f||f.height>0.85f||
       std::fabs(f.offsetU)>0.8f||std::fabs(f.offsetV)>0.8f||
       !std::isfinite(minDimension) || minDimension<=0 ||
       f.depth < -0.4f*minDimension ||
       f.depth > 2.f*minDimension ||
       (f.kind == -1 && f.depth!=0.f))
       throw std::invalid_argument("invalid face area, shape or extrusion depth");
    const float cu=0.5f+f.offsetU*(1.f-f.width)*0.5f;
    const float cv=0.5f+f.offsetV*(1.f-f.height)*0.5f;
    const float shortSide=std::fmin(f.width,f.height);
    const float margins[4]={
        cv-f.height*0.5f, 1.f-(cu+f.width*0.5f),
        1.f-(cv+f.height*0.5f), cu-f.width*0.5f
    };
    for(int edge=0;edge<4;++edge) {
        const float curve=f.curves[static_cast<std::size_t>(edge)];
        if(!std::isfinite(curve) || std::fabs(curve)>0.200001f ||
           (curve<0.f && -curve*shortSide>margins[edge]*0.80f+1e-6f) ||
           (f.kind!=2 && std::fabs(curve)>1e-6f))
            throw std::invalid_argument("curved edge extends outside face");
    }
}
int faceVertexCount(const FaceFeature& f) {
    if(f.kind<0 || std::fabs(f.depth)<1e-6f) return 6;
    return f.kind==0 ? 54 : 390;
}
std::vector<Vertex> multiFaceBox(float w,float h,float l,
    float tw,float tl,float sx,const FaceFeatures& features) {
    const auto original=profiledBox(w,h,l,tw,tl,sx);
    const float minDim=std::fmin(w,std::fmin(h,l));
    std::vector<Vertex> mesh;
    mesh.reserve(2400);
    const std::array<int,4> indices{{0,1,2,5}};
    for(int faceId=0;faceId<6;++faceId) {
        const auto& f=features[faceId];
        validateFaceFeature(f,minDim);
        const std::size_t start=static_cast<std::size_t>(faceId)*6;
        if(faceVertexCount(f)==6) {
            mesh.insert(mesh.end(),original.begin()+start,original.begin()+start+6);
            continue;
        }
        std::array<Point3,4> outer;
        for(int i=0;i<4;++i) {
            const Vertex& v=original[start+static_cast<std::size_t>(indices[i])];
            outer[i]={{v.px,v.py,v.pz}};
        }
        const Vertex& vertex=original[start];
        const Point3 normal{{vertex.nx,vertex.ny,vertex.nz}};
        const float cu=0.5f+f.offsetU*(1-f.width)*0.5f;
        const float cv=0.5f+f.offsetV*(1-f.height)*0.5f;
        const int steps=f.kind==0?1:8;
        const int n=4*steps;
        std::vector<Point3> inner,cap;
        inner.reserve(n);cap.reserve(n);
        const float tau=6.2831853071795864769f;
        float angles[5];
        for(int i=0;i<4;++i) {
            const float ux=(i==1||i==2)?1.f:0.f;
            const float vy=(i>=2)?1.f:0.f;
            angles[i]=std::atan2(vy-cv,ux-cu);
            if(i>0) while(angles[i]<=angles[i-1]) angles[i]+=tau;
        }
        angles[4]=angles[0]+tau;
        for(int side=0;side<4;++side) {
            for(int k=0;k<steps;++k) {
                float u,v;
                if(f.kind==0) {
                    u=cu+((side==1||side==2)?f.width*0.5f:-f.width*0.5f);
                    v=cv+((side>=2)?f.height*0.5f:-f.height*0.5f);
                } else if(f.kind==2) {
                    const float u0=cu+((side==1||side==2)?f.width*0.5f:-f.width*0.5f);
                    const float v0=cv+((side>=2)?f.height*0.5f:-f.height*0.5f);
                    const int next=(side+1)%4;
                    const float u1=cu+((next==1||next==2)?f.width*0.5f:-f.width*0.5f);
                    const float v1=cv+((next>=2)?f.height*0.5f:-f.height*0.5f);
                    const float inwardU=(side==1?-1.f:(side==3?1.f:0.f));
                    const float inwardV=(side==0?1.f:(side==2?-1.f:0.f));
                    const float displacement=2.f*f.curves[static_cast<std::size_t>(side)]*
                        std::fmin(f.width,f.height);
                    const float ctrlU=(u0+u1)*0.5f+inwardU*displacement;
                    const float ctrlV=(v0+v1)*0.5f+inwardV*displacement;
                    const float t=static_cast<float>(k)/steps;
                    const float omt=1.f-t;
                    u=omt*omt*u0+2.f*omt*t*ctrlU+t*t*u1;
                    v=omt*omt*v0+2.f*omt*t*ctrlV+t*t*v1;
                } else {
                    const float theta=angles[side]+
                        (angles[side+1]-angles[side])*
                        (static_cast<float>(k)/steps);
                    u=cu+f.width*0.5f*std::cos(theta);
                    v=cv+f.height*0.5f*std::sin(theta);
                }
                const auto p=pointAt(outer,u,v);
                inner.push_back(p);
                Point3 raised=p;
                for(int c=0;c<3;++c) raised[c]+=normal[c]*f.depth;
                cap.push_back(raised);
            }
        }
        // Stitch each original quad edge to the inset perimeter without
        // splitting shared outer edges: no boundary T-junctions.
        for(int side=0;side<4;++side) {
            const int first=side*steps;
            const int last=((side+1)*steps)%n;
            triangle(mesh,outer[side],outer[(side+1)%4],inner[last]);
            for(int k=0;k<steps;++k) {
                const int i=first+k;
                const int j=(i+1)%n;
                triangle(mesh,outer[side],inner[j],inner[i]);
            }
        }
        for(int i=0;i<n;++i) {
            const int j=(i+1)%n;
            profileQuad(mesh,inner[i],inner[j],cap[j],cap[i]);
        }
        for(int i=1;i<n-1;++i) triangle(mesh,cap[0],cap[i],cap[i+1]);
    }
    return mesh;
}
std::vector<Vertex> faceExtrudedBox(float w,float h,float l,
    float tw,float tl,float shift,int faceIndex,float depth) {
    FaceFeatures features{};
    if(faceIndex==-1 && depth==0.f)
        return multiFaceBox(w,h,l,tw,tl,shift,features);
    if(faceIndex<0 || faceIndex>=6)
        throw std::invalid_argument("invalid extrusion face");
    features[faceIndex].kind=0;
    features[faceIndex].width=0.82f;
    features[faceIndex].height=0.82f;
    features[faceIndex].depth=depth;
    return multiFaceBox(w,h,l,tw,tl,shift,features);
}
}
