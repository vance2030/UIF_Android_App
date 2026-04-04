#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <cstdint>

#define LOG_TAG "UIF_ENGINE"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {
    JNIEXPORT jboolean JNICALL
    Java_com_mastery_uif_UIFEngine_initEngine(JNIEnv *env, jobject thiz, jstring modelPath) {
        LOGI("UIF SDK Booting.");
        return JNI_TRUE;
    }

    JNIEXPORT void JNICALL
    Java_com_mastery_uif_UIFEngine_processFrame(JNIEnv *env, jobject thiz, jobject bitmapIn, jobject bitmapOut) {
        AndroidBitmapInfo infoIn, infoOut;
        void *pixelsIn; void *pixelsOut;

        AndroidBitmap_getInfo(env, bitmapIn, &infoIn);
        AndroidBitmap_getInfo(env, bitmapOut, &infoOut);
        AndroidBitmap_lockPixels(env, bitmapIn, &pixelsIn);
        AndroidBitmap_lockPixels(env, bitmapOut, &pixelsOut);

        uint32_t* src = (uint32_t*)pixelsIn;
        uint32_t* dst = (uint32_t*)pixelsOut;
        int w = infoIn.width, h = infoIn.height;
        
        for (int y = 1; y < h - 1; ++y) {
            for (int x = 1; x < w - 1; ++x) {
                int current = src[y * w + x] & 0xFF;
                int left    = src[y * w + (x - 1)] & 0xFF;
                int right   = src[y * w + (x + 1)] & 0xFF;
                int top     = src[(y - 1) * w + x] & 0xFF;
                int bottom  = src[(y + 1) * w + x] & 0xFF;

                int sum = (current * 4) - left - right - top - bottom;
                if (sum < 0) sum = 0; if (sum > 255) sum = 255;

                dst[y * w + x] = (0xFF000000) | (sum << 8); 
            }
        }
        AndroidBitmap_unlockPixels(env, bitmapIn);
        AndroidBitmap_unlockPixels(env, bitmapOut);
    }
}
