#include "geometry.hpp"
#include <jni.h>
#include <stdexcept>
#include <cmath>
#include <type_traits>

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_modelforge_nativeeditor_NativeGeometry_generateBox(
        JNIEnv* env, jobject, jfloat w, jfloat h, jfloat l) {
    try {
        const auto mesh = forge::box(w,h,l);
        jfloatArray result = env->NewFloatArray(static_cast<jsize>(mesh.size()*6));
        if (!result) return nullptr;
        env->SetFloatArrayRegion(result, 0, static_cast<jsize>(mesh.size()*6),
                                 reinterpret_cast<const jfloat*>(mesh.data()));
        return result;
    } catch (const std::exception& error) {
        jclass type=env->FindClass("java/lang/IllegalArgumentException");
        if (type) env->ThrowNew(type,error.what());
        return nullptr;
    }
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_modelforge_nativeeditor_NativeGeometry_generateProfiledBox(
        JNIEnv* env, jobject, jfloat w, jfloat h, jfloat l,
        jfloat topW, jfloat topL, jfloat shiftX) {
    static_assert(sizeof(forge::Vertex) == sizeof(float) * 6, "packed vertex layout");
    try {
        const auto mesh=forge::profiledBox(w,h,l,topW,topL,shiftX);
        jfloatArray result=env->NewFloatArray(static_cast<jsize>(mesh.size()*6));
        if (!result) return nullptr;
        env->SetFloatArrayRegion(result,0,static_cast<jsize>(mesh.size()*6),
                                 reinterpret_cast<const jfloat*>(mesh.data()));
        return result;
    } catch(const std::exception& error) {
        jclass type=env->FindClass("java/lang/IllegalArgumentException");
        if(type) env->ThrowNew(type,error.what());
        return nullptr;
    }
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_modelforge_nativeeditor_NativeGeometry_generateFaceExtrudedBox(
        JNIEnv* env, jobject, jfloat w, jfloat h, jfloat l,
        jfloat tw, jfloat tl, jfloat sx, jint faceIndex, jfloat depth) {
    static_assert(sizeof(forge::Vertex)==sizeof(float)*6,"packed vertex layout");
    try {
        const auto mesh=forge::faceExtrudedBox(w,h,l,tw,tl,sx,faceIndex,depth);
        jfloatArray result=env->NewFloatArray(static_cast<jsize>(mesh.size()*6));
        if (!result) return nullptr;
        env->SetFloatArrayRegion(result,0,static_cast<jsize>(mesh.size()*6),
                                 reinterpret_cast<const jfloat*>(mesh.data()));
        return result;
    } catch(const std::exception& error) {
        jclass type=env->FindClass("java/lang/IllegalArgumentException");
        if(type) env->ThrowNew(type,error.what());
        return nullptr;
    }
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_modelforge_nativeeditor_NativeGeometry_generateMultiFaceBox(
        JNIEnv* env,jobject,jfloat w,jfloat h,jfloat l,
        jfloat tw,jfloat tl,jfloat shift,jfloatArray values) {
    static_assert(sizeof(forge::Vertex)==6*sizeof(float),"packed vertex layout");
    try {
        if(!values || env->GetArrayLength(values)!=60)
            throw std::invalid_argument("face feature array must contain 60 values");
        jfloat parts[60];
        env->GetFloatArrayRegion(values,0,60,parts);
        if(env->ExceptionCheck()) return nullptr;
        forge::FaceFeatures features{};
        for(int i=0;i<6;++i) {
            const int j=10*i;
            const float kind=parts[j];
            if(!std::isfinite(kind) || kind!=std::floor(kind) || kind< -1.f || kind>1.f)
                throw std::invalid_argument("nonintegral face shape code");
            auto& f=features[static_cast<std::size_t>(i)];
            f.kind=static_cast<int>(kind);
            f.width=parts[j+1];f.height=parts[j+2];
            f.offsetU=parts[j+3];f.offsetV=parts[j+4];
            f.depth=parts[j+5];
            for(int e=0;e<4;++e)
                f.curves[static_cast<std::size_t>(e)]=parts[j+6+e];
        }
        const auto mesh=forge::multiFaceBox(w,h,l,tw,tl,shift,features);
        jfloatArray result=env->NewFloatArray(static_cast<jsize>(mesh.size()*6));
        if(!result) return nullptr;
        env->SetFloatArrayRegion(result,0,static_cast<jsize>(mesh.size()*6),
            reinterpret_cast<const jfloat*>(mesh.data()));
        return result;
    } catch(const std::exception& error) {
        jclass type=env->FindClass("java/lang/IllegalArgumentException");
        if(type) env->ThrowNew(type,error.what());
        return nullptr;
    }
}
