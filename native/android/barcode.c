// <barcode> on Android: barcode_supported's question, into BarcodeView (Java) through JNI.
#include <jni.h>
#include "ui_native.h"

int barcode_native_supported(void) {
    JNIEnv *env = ui_jni_env();
    jclass type = ui_find_class("org.eskiu.barcode.BarcodeView");
    if (!type) return 0;
    jboolean has = (*env)->CallStaticBooleanMethod(env, type, (*env)->GetStaticMethodID(env, type, "supported", "()Z"));
    (*env)->DeleteLocalRef(env, type);
    return has ? 1 : 0;
}
