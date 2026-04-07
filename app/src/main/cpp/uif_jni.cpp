#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <vector>
#include <cmath>
#include <random>
#include <fstream>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "UIF_ENGINE", __VA_ARGS__)

class UIF_Perfect_Engine {
private:
    const int INPUT_DIM = 224 * 224; 
    const int HIDDEN_DIM = 512; // 512-Node Brain for Sub-second Mobile Inversion
    int NUM_CLASSES = 2;        // Object A vs Object B
    float lambda = 0.05f;       // Ridge Regularization (Prevents Singular Matrix Crash)

    std::vector<int8_t> random_lens; // The Fixed Random Projection (Brain)
    std::vector<std::vector<float>> H; // Features
    std::vector<std::vector<float>> Y; // Labels
    std::vector<std::vector<float>> Weights; // Final Trained Brain
    bool is_trained = false;

    // Helper: Matrix Inversion with Partial Pivoting (The Part Gemini Couldn't Write)
    bool invertMatrix(std::vector<std::vector<float>>& mat) {
        int n = mat.size();
        std::vector<std::vector<float>> inv(n, std::vector<float>(n, 0.0f));
        for(int i=0; i<n; i++) inv[i][i] = 1.0f;

        for(int i=0; i<n; i++) {
            // Partial Pivoting (Robustness)
            float max_el = std::abs(mat[i][i]); int max_row = i;
            for(int k=i+1; k<n; k++) {
                if(std::abs(mat[k][i]) > max_el) { max_el = std::abs(mat[k][i]); max_row = k; }
            }
            if(max_el < 1e-6) return false; // Matrix is Singular (Error Prevention)

            std::swap(mat[i], mat[max_row]); std::swap(inv[i], inv[max_row]);

            float pivot = mat[i][i];
            for(int j=0; j<n; j++) { mat[i][j] /= pivot; inv[i][j] /= pivot; }

            for(int k=0; k<n; k++) {
                if(k != i) {
                    float factor = mat[k][i];
                    for(int j=0; j<n; j++) { mat[k][j] -= factor * mat[i][j]; inv[k][j] -= factor * inv[i][j]; }
                }
            }
        }
        mat = inv; return true;
    }

public:
    UIF_Perfect_Engine() {
        std::mt19937 gen(4051); // Master Y's Golden Seed
        std::uniform_int_distribution<> dis(0, 1);
        
        // Create the Fixed "Lens"
        random_lens.resize(INPUT_DIM);
        for(int i=0; i<INPUT_DIM; i++) random_lens[i] = (dis(gen) == 1) ? 1 : -1;
        
        Weights.resize(HIDDEN_DIM, std::vector<float>(NUM_CLASSES, 0.0f));
    }

    // XNOR-like Projection: The True 1-Bit BNN Transformation
    std::vector<float> extractFeatures(uint32_t* pixels) {
        std::vector<float> h(HIDDEN_DIM, 0.0f);
        for (int i = 0; i < HIDDEN_DIM; ++i) {
            float sum = 0.0f;
            // Downsample and project
            for (int j = 0; j < 256; ++j) { 
                int p_idx = (i * 17 + j * 31) % INPUT_DIM;
                int r = (pixels[p_idx] >> 16) & 0xFF;
                int pixel_bit = (r > 127) ? 1 : -1;
                // XNOR equivalence via multiplication
                sum += (pixel_bit * random_lens[p_idx]); 
            }
            h[i] = std::tanh(sum / 32.0f); // Non-linear activation (Sigmoid/Tanh)
        }
        return h;
    }

    void addData(uint32_t* pixels, int class_id) {
        H.push_back(extractFeatures(pixels));
        std::vector<float> label(NUM_CLASSES, -1.0f);
        if(class_id >= 0 && class_id < NUM_CLASSES) label[class_id] = 1.0f;
        Y.push_back(label);
        LOGI("Added data for class %d. Total samples: %d", class_id, (int)H.size());
    }

    bool trainAnalytically() {
        int n = H.size();
        if (n < 2) return false;
        
        // W = (H^T H + lambda I)^-1 H^T Y
        std::vector<std::vector<float>> HtH(HIDDEN_DIM, std::vector<float>(HIDDEN_DIM, 0.0f));
        for(int i=0; i<HIDDEN_DIM; ++i) {
            for(int j=0; j<HIDDEN_DIM; ++j) {
                for(int k=0; k<n; ++k) HtH[i][j] += H[k][i] * H[k][j];
                if(i == j) HtH[i][j] += lambda; // Ridge Regression to prevent crashes
            }
        }

        std::vector<std::vector<float>> HtY(HIDDEN_DIM, std::vector<float>(NUM_CLASSES, 0.0f));
        for(int i=0; i<HIDDEN_DIM; ++i) {
            for(int j=0; j<NUM_CLASSES; ++j) {
                for(int k=0; k<n; ++k) HtY[i][j] += H[k][i] * Y[k][j];
            }
        }

        if(!invertMatrix(HtH)) { LOGE("Matrix Inversion Failed (Singular)!"); return false; }

        for(int i=0; i<HIDDEN_DIM; ++i) {
            for(int j=0; j<NUM_CLASSES; ++j) {
                Weights[i][j] = 0.0f;
                for(int k=0; k<HIDDEN_DIM; ++k) Weights[i][j] += HtH[i][k] * HtY[k][j];
            }
        }
        is_trained = true; LOGI("PERFECT ANALYTIC TRAINING SUCCESSFUL!"); return true;
    }

    int predict(uint32_t* pixels) {
        if (!is_trained) return -1;
        std::vector<float> features = extractFeatures(pixels);
        float best_score = -9999.0f; int best_class = -1;
        for(int j=0; j<NUM_CLASSES; ++j) {
            float score = 0.0f;
            for(int i=0; i<HIDDEN_DIM; ++i) score += features[i] * Weights[i][j];
            if(score > best_score) { best_score = score; best_class = j; }
        }
        return best_class;
    }
};

UIF_Perfect_Engine* trainer = nullptr;

extern "C" {
    JNIEXPORT void JNICALL Java_com_mastery_uif_UIFEngine_initTrainer(JNIEnv *env, jobject thiz) {
        if(trainer == nullptr) trainer = new UIF_Perfect_Engine();
    }
    JNIEXPORT void JNICALL Java_com_mastery_uif_UIFEngine_releaseTrainer(JNIEnv *env, jobject thiz) {
        if (trainer != nullptr) { delete trainer; trainer = nullptr; }
    }
    JNIEXPORT void JNICALL Java_com_mastery_uif_UIFEngine_addTrainingData(JNIEnv *env, jobject thiz, jobject bitmap, jint classId) {
        if(!trainer) return;
        AndroidBitmapInfo info; void *pixels;
        AndroidBitmap_getInfo(env, bitmap, &info); AndroidBitmap_lockPixels(env, bitmap, &pixels);
        trainer->addData((uint32_t*)pixels, classId);
        AndroidBitmap_unlockPixels(env, bitmap);
    }
    JNIEXPORT jboolean JNICALL Java_com_mastery_uif_UIFEngine_trainNow(JNIEnv *env, jobject thiz) {
        if(!trainer) return false; return trainer->trainAnalytically() ? JNI_TRUE : JNI_FALSE;
    }
    JNIEXPORT jint JNICALL Java_com_mastery_uif_UIFEngine_predictRealTime(JNIEnv *env, jobject thiz, jobject bitmap) {
        if(!trainer) return -1;
        AndroidBitmapInfo info; void *pixels;
        AndroidBitmap_getInfo(env, bitmap, &info); AndroidBitmap_lockPixels(env, bitmap, &pixels);
        int result = trainer->predict((uint32_t*)pixels);
        AndroidBitmap_unlockPixels(env, bitmap);
        return result;
    }
}
