#include "document.hpp"
#include <jni.h>
#include <mutex>
#include <stdexcept>
#include <string>
#include <utility>

namespace {
// The first Android project contains one generated editable box.
// It is independent of Filament and protected against stale/incomplete edits.
forge::Document document;
std::mutex projectMutex;
void javaError(JNIEnv* env, const std::exception& error) {
    jclass klass=env->FindClass("java/lang/IllegalArgumentException");
    if (klass) env->ThrowNew(klass,error.what());
}
std::array<double,3> sizes(jfloat w,jfloat h,jfloat l) { return {{w,h,l}}; }
std::array<double,3> profile(jfloat tw,jfloat tl,jfloat shift) {
    return {{tw,tl,shift}};
}
std::uint64_t boxId() {
    if (document.objects().size()!=1 ||
        document.objects()[0].kind!=forge::GeometryKind::PolygonMesh ||
        document.objects()[0].sourceReference!="generated:profiled_box")
        throw std::invalid_argument("no editable shape in project");
    return document.objects()[0].id;
}
}
extern "C" JNIEXPORT void JNICALL
Java_com_modelforge_nativeeditor_NativeProject_resetAndCreate(
    JNIEnv* env,jobject,jfloat w,jfloat h,jfloat l,jfloat tw,jfloat tl,jfloat shift) {
    std::lock_guard<std::mutex> guard(projectMutex);
    try {
        forge::Document candidate;
        auto id=candidate.insert(forge::GeometryKind::PolygonMesh,"Starter Box",
                                 "generated:profiled_box",sizes(w,h,l),0);
        candidate.reshape(id,sizes(w,h,l),profile(tw,tl,shift),candidate.revision());
        candidate.clearHistory();
        document=std::move(candidate);
    } catch (const std::exception& error) { javaError(env,error); }
}
extern "C" JNIEXPORT void JNICALL
Java_com_modelforge_nativeeditor_NativeProject_reshape(
    JNIEnv* env,jobject,jfloat w,jfloat h,jfloat l,jfloat tw,jfloat tl,jfloat shift) {
    std::lock_guard<std::mutex> guard(projectMutex);
    try {
        document.reshape(boxId(),sizes(w,h,l),profile(tw,tl,shift),document.revision());
    } catch(const std::exception& error) { javaError(env,error); }
}
extern "C" JNIEXPORT void JNICALL
Java_com_modelforge_nativeeditor_NativeProject_setFaceRegion(
    JNIEnv* env,jobject,jint faceIndex,jint kind,jfloat width,jfloat height,
    jfloat offsetU,jfloat offsetV,jfloat depth,jfloatArray curves) {
    std::lock_guard<std::mutex> guard(projectMutex);
    try {
        forge::FaceRegion feature;
        feature.kind=kind;
        feature.width=width;feature.height=height;
        feature.offsetU=offsetU;feature.offsetV=offsetV;
        feature.depthMetres=depth;
        if(!curves || env->GetArrayLength(curves)!=4)
            throw std::invalid_argument("face must have four edge curves");
        jfloat parts[4];
        env->GetFloatArrayRegion(curves,0,4,parts);
        if(env->ExceptionCheck()) return;
        for(int i=0;i<4;++i) feature.curves[static_cast<std::size_t>(i)]=parts[i];
        document.setFaceRegion(boxId(),faceIndex,feature,document.revision());
    } catch(const std::exception& error) { javaError(env,error); }
}
extern "C" JNIEXPORT jboolean JNICALL
Java_com_modelforge_nativeeditor_NativeProject_undo(JNIEnv*,jobject) {
    std::lock_guard<std::mutex> guard(projectMutex);
    return document.undo()?JNI_TRUE:JNI_FALSE;
}
extern "C" JNIEXPORT jboolean JNICALL
Java_com_modelforge_nativeeditor_NativeProject_redo(JNIEnv*,jobject) {
    std::lock_guard<std::mutex> guard(projectMutex);
    return document.redo()?JNI_TRUE:JNI_FALSE;
}
extern "C" JNIEXPORT jboolean JNICALL
Java_com_modelforge_nativeeditor_NativeProject_canUndo(JNIEnv*,jobject) {
    std::lock_guard<std::mutex> guard(projectMutex);
    return document.canUndo()?JNI_TRUE:JNI_FALSE;
}
extern "C" JNIEXPORT jboolean JNICALL
Java_com_modelforge_nativeeditor_NativeProject_canRedo(JNIEnv*,jobject) {
    std::lock_guard<std::mutex> guard(projectMutex);
    return document.canRedo()?JNI_TRUE:JNI_FALSE;
}
extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_modelforge_nativeeditor_NativeProject_currentShape(JNIEnv* env,jobject) {
    std::lock_guard<std::mutex> guard(projectMutex);
    if(document.objects().size()!=1 ||
       document.objects()[0].sourceReference!="generated:profiled_box" ||
       document.objects()[0].kind!=forge::GeometryKind::PolygonMesh)
        return nullptr;
    const auto& obj=document.objects()[0];
    jfloat values[66] = {
        static_cast<jfloat>(obj.sizeMetres[0]),
        static_cast<jfloat>(obj.sizeMetres[1]),
        static_cast<jfloat>(obj.sizeMetres[2]),
        static_cast<jfloat>(obj.topProfile[0]),
        static_cast<jfloat>(obj.topProfile[1]),
        static_cast<jfloat>(obj.topProfile[2])
    };
    for(int i=0;i<6;++i) {
        const auto& f=obj.faceRegions[static_cast<std::size_t>(i)];
        const int start=6+i*10;
        values[start+0]=static_cast<jfloat>(f.kind);
        values[start+1]=static_cast<jfloat>(f.width);
        values[start+2]=static_cast<jfloat>(f.height);
        values[start+3]=static_cast<jfloat>(f.offsetU);
        values[start+4]=static_cast<jfloat>(f.offsetV);
        values[start+5]=static_cast<jfloat>(f.depthMetres);
        for(int e=0;e<4;++e)
            values[start+6+e]=static_cast<jfloat>(f.curves[static_cast<std::size_t>(e)]);
    }
    jfloatArray arr=env->NewFloatArray(66);
    if(arr) env->SetFloatArrayRegion(arr,0,66,values);
    return arr;
}
extern "C" JNIEXPORT jstring JNICALL
Java_com_modelforge_nativeeditor_NativeProject_snapshot(JNIEnv* env,jobject) {
    std::lock_guard<std::mutex> guard(projectMutex);
    const auto value=document.serialize();
    return env->NewStringUTF(value.c_str());
}
extern "C" JNIEXPORT void JNICALL
Java_com_modelforge_nativeeditor_NativeProject_load(
    JNIEnv* env,jobject,jstring raw) {
    if(!raw) {
        jclass klass=env->FindClass("java/lang/IllegalArgumentException");
        if(klass) env->ThrowNew(klass,"project data missing");
        return;
    }
    const char* chars=env->GetStringUTFChars(raw,nullptr);
    if(!chars) return;
    std::string data(chars);
    env->ReleaseStringUTFChars(raw,chars);
    std::lock_guard<std::mutex> guard(projectMutex);
    try {
        auto candidate=forge::Document::parse(data);
        if(candidate.objects().size()!=1 ||
           candidate.objects()[0].sourceReference!="generated:profiled_box" ||
           candidate.objects()[0].kind!=forge::GeometryKind::PolygonMesh)
            throw std::invalid_argument("saved project is not an editable starter shape");
        document=std::move(candidate);
    } catch(const std::exception& error) { javaError(env,error); }
}
extern "C" JNIEXPORT void JNICALL
Java_com_modelforge_nativeeditor_NativeProject_clear(JNIEnv*,jobject) {
    std::lock_guard<std::mutex> guard(projectMutex);
    document=forge::Document();
}
