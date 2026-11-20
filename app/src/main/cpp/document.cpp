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
    const double minDim=std::min(obj.sizeMetres[0],
        std::min(obj.sizeMetres[1],obj.sizeMetres[2]));
    for(const auto& f:obj.faceRegions) {
        reject(f.kind < -1 || f.kind > 2 ||
               !std::isfinite(f.width)||!std::isfinite(f.height)||
               !std::isfinite(f.offsetU)||!std::isfinite(f.offsetV)||
               !std::isfinite(f.depthMetres) ||
               f.width<0.149999 || f.width>0.8500001 ||
               f.height<0.149999 || f.height>0.8500001 ||
               std::abs(f.offsetU)>0.8000001 || std::abs(f.offsetV)>0.8000001 ||
               f.depthMetres < -0.4*minDim || f.depthMetres>2.0*minDim ||
               (f.kind==-1 && f.depthMetres!=0.0) ||
               (obj.kind==GeometryKind::CadBrep && f.kind!=-1),
               "invalid face region, shape or depth");
        const double cu=0.5+f.offsetU*(1.0-f.width)*0.5;
        const double cv=0.5+f.offsetV*(1.0-f.height)*0.5;
        const double side=std::min(f.width,f.height);
        const double margins[4]={
            cv-f.height*0.5,1.0-(cu+f.width*0.5),
            1.0-(cv+f.height*0.5),cu-f.width*0.5
        };
        for(int edge=0;edge<4;++edge) {
            const double bulge=f.curves[static_cast<std::size_t>(edge)];
            reject(!std::isfinite(bulge) || std::abs(bulge)>0.200001 ||
                   (bulge<0.0 && -bulge*side > margins[edge]*0.8+1e-6) ||
                   (f.kind!=2 && std::abs(bulge)>1e-6),
                   "curved edge leaves its host face");
        }
    }
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
void Document::setFaceRegion(std::uint64_t objectId,int faceId,
                             const FaceRegion& region,std::uint64_t expected) {
    assertRevision(expected);
    reject(faceId<0 || faceId>5,"invalid semantic face index");
    auto next=objects_;
    auto match=std::find_if(next.begin(),next.end(),
        [&](const ModelObject& obj){return obj.id==objectId;});
    reject(match==next.end(),"unknown object ID");
    reject(match->kind!=GeometryKind::PolygonMesh ||
           match->sourceReference!="generated:profiled_box",
           "face editing requires a generated editable box");
    match->faceRegions[static_cast<std::size_t>(faceId)]=region;
    validate(*match);
    commit(std::move(next));
}
int Document::mirrorFaceRegion(std::uint64_t objectId,int sourceFace,
                               bool overwrite,std::uint64_t expected) {
    assertRevision(expected);
    reject(sourceFace<0 || sourceFace>5, "invalid source face index");
    const int targetFace=sourceFace^1;
    auto next=objects_;
    auto match=std::find_if(next.begin(),next.end(),
        [&](const ModelObject& obj){return obj.id==objectId;});
    reject(match==next.end(),"unknown object ID");
    reject(match->kind!=GeometryKind::PolygonMesh ||
           match->sourceReference!="generated:profiled_box",
           "mirroring requires a generated editable box");
    const auto& source=match->faceRegions[static_cast<std::size_t>(sourceFace)];
    reject(source.kind<0,"draw a region on the selected face before mirroring");
    auto& target=match->faceRegions[static_cast<std::size_t>(targetFace)];
    reject(target.kind>=0 && !overwrite,
           "opposite face has an edit; explicit overwrite required");
    FaceRegion reflected=source;
    if(sourceFace<4) {
        // Front/back and left/right use opposing local U axes.
        reflected.offsetU=-source.offsetU;
        reflected.curves={{source.curves[0],source.curves[3],
                           source.curves[2],source.curves[1]}};
    } else {
        // Top/bottom use opposing local V axes.
        reflected.offsetV=-source.offsetV;
        reflected.curves={{source.curves[2],source.curves[1],
                           source.curves[0],source.curves[3]}};
    }
    target=reflected;
    validate(*match);
    commit(std::move(next));
    return targetFace;
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
    stream << "MFORGE_DOC 5\n" << revision_ << ' ' << nextId_ << ' ' << objects_.size() << '\n'
           << std::setprecision(17);
    for (const ModelObject& obj : objects_) {
        stream << obj.id << ' ' << static_cast<int>(obj.kind) << ' '
               << std::quoted(obj.name) << ' ' << std::quoted(obj.sourceReference);
        for (double size : obj.sizeMetres) stream << ' ' << size;
        for (double parameter : obj.topProfile) stream << ' ' << parameter;
        for(const auto& f:obj.faceRegions) {
            stream << ' ' << f.kind << ' ' << f.width << ' ' << f.height
                   << ' ' << f.offsetU << ' ' << f.offsetV << ' ' << f.depthMetres;
            for(double curve:f.curves) stream << ' ' << curve;
        }
        stream << '\n';
    }
    return stream.str();
}
Document Document::parse(const std::string& data) {
    reject(data.size()>4*1024*1024,"project metadata too large");
    std::istringstream stream(data);
    std::string magic;
    int version=0;
    reject(!(stream>>magic>>version) || magic!="MFORGE_DOC" || (version!=1 && version!=2 && version!=3 && version!=4 && version!=5),
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
        if(version==3) {
            int legacyFace=-1;
            double legacyDepth=0.0;
            reject(!(stream>>legacyFace>>legacyDepth),
                   "missing or corrupt legacy extrusion");
            reject(legacyFace < -1 || legacyFace > 5 ||
                   (legacyFace==-1 && legacyDepth!=0.0),
                   "invalid legacy extrusion face");
            if(legacyFace>=0) {
                auto& f=obj.faceRegions[static_cast<std::size_t>(legacyFace)];
                f.kind=0;
                f.width=0.82;
                f.height=0.82;
                f.depthMetres=legacyDepth;
            }
        }
        if(version==4 || version==5) {
            for(auto& f:obj.faceRegions) {
                reject(!(stream>>f.kind>>f.width>>f.height>>
                         f.offsetU>>f.offsetV>>f.depthMetres),
                       "missing or corrupt face region");
                if(version==5) {
                    for(double& bulge:f.curves)
                        reject(!(stream>>bulge),"missing or corrupt curved edge");
                }
            }
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
