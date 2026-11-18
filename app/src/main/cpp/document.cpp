#include "document.hpp"
#include <algorithm>
#include <cmath>
#include <iomanip>
#include <limits>
#include <sstream>
#include <stdexcept>
#include <utility>

namespace forge {
namespace {
void reject(bool condition, const char* message) {
    if (condition) throw std::invalid_argument(message);
}
}
void Document::assertRevision(std::uint64_t expected) const {
    if (expected != revision_) throw std::runtime_error("stale document revision");
}
void Document::validate(const ModelObject& obj) {
    reject(obj.id == 0, "object ID must be positive");
    reject(obj.kind != GeometryKind::PolygonMesh &&
           obj.kind != GeometryKind::CadBrep, "unknown geometry kind");
    reject(obj.name.empty() || obj.name.size() > 128,
           "object name must contain 1..128 bytes");
    reject(obj.sourceReference.empty() || obj.sourceReference.size() > 2048,
           "geometry source reference must contain 1..2048 bytes");
    for (double size : obj.sizeMetres) {
        reject(!std::isfinite(size) || size < 1e-6 || size > 10000.0,
               "invalid size in metres");
    }
    const auto& p = obj.topProfile;
    reject(!std::isfinite(p[0]) || !std::isfinite(p[1]) || !std::isfinite(p[2]) ||
           p[0] < 0.35 || p[0] > 1.75 || p[1] < 0.35 || p[1] > 1.75 ||
           p[2] < -0.4 || p[2] > 0.4, "invalid top surface profile");
    reject(obj.extrusionFace < -1 || obj.extrusionFace > 5 ||
           !std::isfinite(obj.extrusionDepthMetres), "invalid face extrusion data");
    const double minDim=std::min(obj.sizeMetres[0],
        std::min(obj.sizeMetres[1],obj.sizeMetres[2]));
    reject((obj.extrusionFace==-1 && obj.extrusionDepthMetres!=0.0) ||
           (obj.extrusionFace!=-1 && (obj.extrusionDepthMetres < -0.4*minDim ||
                obj.extrusionDepthMetres > 2.0*minDim)), "face extrusion depth outside safe range");
    reject(obj.kind==GeometryKind::CadBrep && obj.extrusionFace!=-1,
           "mesh face extrusion cannot modify CAD references");
}
void Document::commit(std::vector<ModelObject> next) {
    if (undo_.size() == maxHistory) undo_.erase(undo_.begin());
    undo_.push_back(std::move(objects_));
    objects_ = std::move(next);
    redo_.clear();
    ++revision_;
}
std::uint64_t Document::insert(GeometryKind kind, std::string name,
                               std::string source, std::array<double,3> size,
                               std::uint64_t expected) {
    assertRevision(expected);
    reject(objects_.size() >= maxObjects, "document object limit");
    reject(nextId_ == std::numeric_limits<std::uint64_t>::max(), "object ID exhausted");
    ModelObject obj{nextId_, kind, std::move(name), std::move(source), size};
    validate(obj);
    auto next=objects_;
    next.push_back(obj);
    commit(std::move(next));
    return nextId_++; // IDs never reused, including after an undo.
}
void Document::resize(std::uint64_t objectId, std::array<double,3> size,
                      std::uint64_t expected) {
    assertRevision(expected);
    auto next=objects_;
    auto match=std::find_if(next.begin(),next.end(),[&](const ModelObject& o){return o.id==objectId;});
    reject(match==next.end(),"unknown object ID");
    match->sizeMetres=size;
    validate(*match);
    commit(std::move(next));
}
void Document::reshape(std::uint64_t objectId, std::array<double,3> size,
                       std::array<double,3> profile, std::uint64_t expected) {
    assertRevision(expected);
    auto next=objects_;
    auto match=std::find_if(next.begin(),next.end(),[&](const ModelObject& o){return o.id==objectId;});
    reject(match==next.end(),"unknown object ID");
    reject(match->kind!=GeometryKind::PolygonMesh ||
           match->sourceReference!="generated:profiled_box",
           "profile editing requires a generated editable box");
    match->sizeMetres=size;
    match->topProfile=profile;
    validate(*match);
    commit(std::move(next));
}
void Document::setFaceExtrusion(std::uint64_t objectId, int faceId,
                                double depth, std::uint64_t expected) {
    assertRevision(expected);
    auto next=objects_;
    auto match=std::find_if(next.begin(),next.end(),
        [&](const ModelObject& o){return o.id==objectId;});
    reject(match==next.end(),"unknown object ID");
    reject(match->kind!=GeometryKind::PolygonMesh ||
           match->sourceReference!="generated:profiled_box",
           "face editing requires a generated editable box");
    match->extrusionFace=faceId;
    match->extrusionDepthMetres=depth;
    validate(*match);
    commit(std::move(next));
}
void Document::rename(std::uint64_t objectId, std::string name,
                      std::uint64_t expected) {
    assertRevision(expected);
    auto next=objects_;
    auto match=std::find_if(next.begin(),next.end(),[&](const ModelObject& o){return o.id==objectId;});
    reject(match==next.end(),"unknown object ID");
    match->name=std::move(name);
    validate(*match);
    commit(std::move(next));
}
void Document::erase(std::uint64_t objectId, std::uint64_t expected) {
    assertRevision(expected);
    auto next=objects_;
    next.erase(std::remove_if(next.begin(),next.end(),[&](const ModelObject& o){return o.id==objectId;}),next.end());
    reject(next.size()==objects_.size(),"unknown object ID");
    commit(std::move(next));
}
bool Document::undo() {
    if (undo_.empty()) return false;
    redo_.push_back(std::move(objects_));
    objects_=std::move(undo_.back());
    undo_.pop_back();
    ++revision_;
    return true;
}
bool Document::redo() {
    if (redo_.empty()) return false;
    if (undo_.size() == maxHistory) undo_.erase(undo_.begin());
    undo_.push_back(std::move(objects_));
    objects_=std::move(redo_.back());
    redo_.pop_back();
    ++revision_;
    return true;
}
std::string Document::serialize() const {
    std::ostringstream stream;
    stream << "MFORGE_DOC 3\n" << revision_ << ' ' << nextId_ << ' ' << objects_.size() << '\n'
           << std::setprecision(17);
    for (const ModelObject& obj : objects_) {
        stream << obj.id << ' ' << static_cast<int>(obj.kind) << ' '
               << std::quoted(obj.name) << ' ' << std::quoted(obj.sourceReference);
        for (double size : obj.sizeMetres) stream << ' ' << size;
        for (double parameter : obj.topProfile) stream << ' ' << parameter;
        stream << ' ' << obj.extrusionFace << ' ' << obj.extrusionDepthMetres;
        stream << '\n';
    }
    return stream.str();
}
Document Document::parse(const std::string& data) {
    reject(data.size()>4*1024*1024,"project metadata too large");
    std::istringstream stream(data);
    std::string magic;
    int version=0;
    reject(!(stream>>magic>>version) || magic!="MFORGE_DOC" || (version!=1 && version!=2 && version!=3),
           "unsupported document header");
    Document result;
    std::size_t count=0;
    reject(!(stream>>result.revision_>>result.nextId_>>count) ||
           result.nextId_==0 || count>maxObjects, "invalid document index");
    result.objects_.reserve(count);
    std::uint64_t highestId=0;
    for(std::size_t i=0;i<count;++i) {
        ModelObject obj;
        int rawKind=0;
        reject(!(stream>>obj.id>>rawKind>>std::quoted(obj.name)>>
                     std::quoted(obj.sourceReference)>>
                     obj.sizeMetres[0]>>obj.sizeMetres[1]>>obj.sizeMetres[2]),
               "truncated or corrupt project");
        if (version>=2) {
            reject(!(stream>>obj.topProfile[0]>>obj.topProfile[1]>>obj.topProfile[2]),
                   "missing or corrupt surface profile");
        }
        if (version>=3) {
            reject(!(stream>>obj.extrusionFace>>obj.extrusionDepthMetres),
                   "missing or corrupt face extrusion");
        }
        obj.kind=static_cast<GeometryKind>(rawKind);
        validate(obj);
        reject(obj.id<=highestId || obj.id>=result.nextId_,
               "object IDs must be ordered, unique and below next ID");
        highestId=obj.id;
        result.objects_.push_back(std::move(obj));
    }
    std::string trailing;
    reject(static_cast<bool>(stream>>trailing),"unexpected trailing project data");
    return result;
}
}
