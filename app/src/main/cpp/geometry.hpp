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
}
