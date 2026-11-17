#include "geometry.hpp"
#include <jni.h>
#include <stdexcept>

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
