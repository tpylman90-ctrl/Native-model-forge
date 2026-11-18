#pragma once
#include <array>
#include <vector>

namespace forge {
struct Vertex {
    float px, py, pz;
    float nx, ny, nz;
};
// A standalone mesh kernel API. Renderers/Android UI never own geometry.
std::vector<Vertex> box(float width, float height, float length);
// Top scales 0.35..1.75; shiftX fraction of width in -0.4..0.4.
std::vector<Vertex> profiledBox(float width, float height, float length, float topWidth, float topLength, float shiftX);
// Six independent semantic faces. A face can hold a rectangular or oval
// inset feature with relative width/height and an offset within its margins.
// kind=-1: none; 0: rectangle; 1: oval; 2: 4 independently curved edges.
struct FaceFeature {
    int kind=-1;
    float width=0.55f;
    float height=0.55f;
    float offsetU=0.f; // normalized -0.8..+0.8 of available margin
    float offsetV=0.f;
    float depth=0.f;
    // Signed quadratic edge bulge in fractions of the smaller region side.
    // The four edges run bottom, right, top, left in face-local coordinates.
    std::array<float,4> curves{{0.f,0.f,0.f,0.f}};
};
using FaceFeatures = std::array<FaceFeature,6>;
void validateFaceFeature(const FaceFeature& feature,float minDimension);
int faceVertexCount(const FaceFeature& feature);
std::vector<Vertex> multiFaceBox(float width,float height,float length,
    float topWidth,float topLength,float shiftX,const FaceFeatures& features);
// Backwards-compatible kernel wrapper for the v0.5 test contract.
std::vector<Vertex> faceExtrudedBox(float width, float height, float length,
    float topWidth, float topLength, float shiftX, int faceIndex, float depth);
}
