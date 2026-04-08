#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <fcntl.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <unistd.h>
#include <cstring>
#include <cstdlib>
#include <vector>
#include "uif_simd_core.h"

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "UIF_SDK", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "UIF_SDK", __VA_ARGS__)

#pragma pack(push, 1)
struct ModelHeader { char magic[4]; uint32_t version; uint32_t num_nodes; uint32_t memory_arena; };
struct NodeMeta { 
    uint32_t type; uint32_t id, in1, in2, in_c, in_h, in_w, out_c, out_h, out_w, k, s, p; 
    uint64_t w_offset, t_offset, s_offset, out_mem_offset; 
};
#pragma pack(pop)

#define CONV2D_XNOR_SCALE 1

class UIF_UniversalEngine {
private:
    uint8_t* mmap_data = nullptr;
    size_t file_size = 0;
    ModelHeader* header = nullptr;
    NodeMeta* nodes = nullptr;
    uint8_t* memory_arena = nullptr;
    bool is_loaded = false;

public:
    UIF_UniversalEngine(const char* model_path) {
        int fd = open(model_path, O_RDONLY);
        if (fd == -1) { LOGE("Cannot load .uif model."); return; }
        
        struct stat sb; fstat(fd, &sb); file_size = sb.st_size;
        mmap_data = (uint8_t*)mmap(NULL, file_size, PROT_READ, MAP_SHARED, fd, 0);
        close(fd);

        header = reinterpret_cast<ModelHeader*>(mmap_data);
        nodes = reinterpret_cast<NodeMeta*>(mmap_data + sizeof(ModelHeader));
        
        if (posix_memalign((void**)&memory_arena, 64, header->memory_arena) != 0) return;
        memset(memory_arena, 0, header->memory_arena);
        is_loaded = true;
    }

    int ExecuteInference(const uint8_t* input_image_argb, int width, int height) {
        if(!is_loaded) return -1;
        
        // 1. [THE GAME CHANGER]: LIVE CAMERA BIT-PACKING (RGB -> 1-Bit Packed)
        int num_pixels = width * height;
        int packed_size = (num_pixels + 7) / 8; // Compress 8 pixels into 1 byte
        std::vector<uint8_t> packed_input(packed_size, 0);
        
        for(int i = 0; i < num_pixels; ++i) {
            // Extract RGB from ARGB_8888
            int r = input_image_argb[i * 4];
            int g = input_image_argb[i * 4 + 1];
            int b = input_image_argb[i * 4 + 2];
            int gray = (r + g + b) / 3; // Grayscale
            
            // Binarize & Pack: If bright enough (>127), set the bit to 1
            if (gray > 127) {
                packed_input[i / 8] |= (1 << (7 - (i % 8)));
            }
        }

        int mock_prediction = 0;

        // 2. RUN BARE-METAL SIMD NEON XNOR ON LIVE PACKED DATA
        for (uint32_t i = 0; i < header->num_nodes; ++i) {
            NodeMeta& node = nodes[i];
            if (node.type == CONV2D_XNOR_SCALE) {
                const uint8_t* layer_weights = mmap_data + node.w_offset;
                size_t num_bytes = (node.in_c * node.k * node.k) / 8; 
                
                // Call SIMD directly with the live camera packed bits
                int popcount_result = uif_engine::Hardware_XNOR_Popcount(packed_input.data(), layer_weights, num_bytes);
                
                int total_bits = num_bytes * 8;
                mock_prediction = (popcount_result * 2) - total_bits; 
            }
        }
        return mock_prediction;
    }

    ~UIF_UniversalEngine() {
        if(memory_arena) free(memory_arena);
        if(mmap_data) munmap(mmap_data, file_size);
    }
};

UIF_UniversalEngine* engine = nullptr;

extern "C" {
    JNIEXPORT jboolean JNICALL Java_com_mastery_uif_UIFEngine_loadModel(JNIEnv *env, jobject thiz, jstring modelPath) {
        if(engine != nullptr) { delete engine; }
        const char *path = env->GetStringUTFChars(modelPath, 0);
        engine = new UIF_UniversalEngine(path);
        env->ReleaseStringUTFChars(modelPath, path);
        return JNI_TRUE;
    }
    
    JNIEXPORT void JNICALL Java_com_mastery_uif_UIFEngine_releaseEngine(JNIEnv *env, jobject thiz) {
        if (engine != nullptr) { delete engine; engine = nullptr; }
    }

    JNIEXPORT jint JNICALL Java_com_mastery_uif_UIFEngine_runInference(JNIEnv *env, jobject thiz, jobject bitmapIn) {
        if(!engine) return -1;
        AndroidBitmapInfo infoIn; void *pixelsIn;
        AndroidBitmap_getInfo(env, bitmapIn, &infoIn);
        AndroidBitmap_lockPixels(env, bitmapIn, &pixelsIn);
        
        // Pass the live ARGB pixels and dimensions to C++ Engine
        int prediction = engine->ExecuteInference((uint8_t*)pixelsIn, infoIn.width, infoIn.height);
        
        AndroidBitmap_unlockPixels(env, bitmapIn);
        return prediction;
    }
}
