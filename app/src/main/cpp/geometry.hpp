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
}
