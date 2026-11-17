#pragma once
#include <array>
#include <cstdint>
#include <string>
#include <vector>

namespace forge {
// The CAD kind describes canonical B-Rep assets; the OCCT adapter is not shipped.
enum class GeometryKind : std::uint8_t { PolygonMesh = 1, CadBrep = 2 };
struct ModelObject {
    std::uint64_t id = 0;
    GeometryKind kind = GeometryKind::PolygonMesh;
    std::string name;
    std::string sourceReference;
    std::array<double, 3> sizeMetres{{1.0, 1.0, 1.0}};
};

// Host-testable, renderer-independent document. No Android or OCCT headers.
// Undo is in-memory at present; save() stores the current revision and objects.
class Document {
public:
    std::uint64_t revision() const noexcept { return revision_; }
    const std::vector<ModelObject>& objects() const noexcept { return objects_; }

    std::uint64_t insert(GeometryKind kind, std::string name,
                         std::string source, std::array<double,3> sizeMetres,
                         std::uint64_t expectedRevision);
    void resize(std::uint64_t objectId, std::array<double,3> sizeMetres,
                std::uint64_t expectedRevision);
    void rename(std::uint64_t objectId, std::string name,
                std::uint64_t expectedRevision);
    void erase(std::uint64_t objectId, std::uint64_t expectedRevision);
    bool undo();
    bool redo();

    std::string serialize() const;
    static Document parse(const std::string& data);

private:
    static constexpr std::size_t maxHistory = 64;
    static constexpr std::size_t maxObjects = 4096;
    void assertRevision(std::uint64_t expectedRevision) const;
    static void validate(const ModelObject& obj);
    void commit(std::vector<ModelObject> next);
    std::uint64_t revision_ = 0;
    std::uint64_t nextId_ = 1;
    std::vector<ModelObject> objects_;
    std::vector<std::vector<ModelObject>> undo_;
    std::vector<std::vector<ModelObject>> redo_;
};
}
