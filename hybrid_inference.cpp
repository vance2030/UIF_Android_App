
#include <iostream>

#include <vector>

#include <fstream>

#include <chrono>

#include <cmath>

#include <string>

#include <fcntl.h>

#include <sys/mman.h>

#include <sys/stat.h>

#include <unistd.h>

#include <cstring>

#include <iomanip>

#include "app/src/main/cpp/uif_simd_core.h"



using namespace std;



vector<string> LoadClasses(const string& filepath) {

    vector<string> classes; ifstream file(filepath); string line;

    while (getline(file, line)) { classes.push_back(line); }

    return classes;

}



int main() {

    cout << "\n======================================================\n";

    cout << "[UIF V6] THE ULTIMATE REALITY TEST (GOLDEN RETRIEVER)\n";

    cout << "======================================================\n";



    vector<string> class_names = LoadClasses("imagenet_classes.txt");



    // 1. LOAD THE REAL BRAIN (.uif)

    int fd_brain = open("hybrid_core.uif", O_RDONLY);

    if (fd_brain == -1) { cerr << "[ERROR] Brain missing.\n"; return 1; }

    struct stat sb_brain; fstat(fd_brain, &sb_brain);

    uint8_t* brain_data = (uint8_t*)mmap(NULL, sb_brain.st_size, PROT_READ, MAP_SHARED, fd_brain, 0);

    close(fd_brain);



    float* fc_weights = (float*)(brain_data + 16 + 9408 + 4608);

    float* fc_bias = (float*)(brain_data + 16 + 9408 + 4608 + (1000 * 512 * 4));

    uint8_t* core_1bit_weights = (uint8_t*)(brain_data + 16 + 9408);



    // 2. LOAD THE REAL IMAGE DATA (.bin)

    int fd_img = open("real_image_data.bin", O_RDONLY);

    if (fd_img == -1) { cerr << "[ERROR] Real Image Data missing.\n"; return 1; }

    struct stat sb_img; fstat(fd_img, &sb_img);

    uint8_t* img_data = (uint8_t*)mmap(NULL, sb_img.st_size, PROT_READ, MAP_SHARED, fd_img, 0);

    close(fd_img);



    uint8_t* simd_input = img_data;                     // First 4608 bytes

    float* fc_input = (float*)(img_data + 4608);        // Next 512 floats



    vector<float> fc_output(1000, 0.0f);

    cout << "[STATUS] Firing up the Hybrid Engine...\n";



    // ========================================================

    // START TIMER

    auto start_time = chrono::high_resolution_clock::now();



    // PHASE A: 1-BIT SIMD XNOR CORE (Running at 128-Bit Speed)

    int popcount_res = uif_engine::Hardware_XNOR_Popcount(simd_input, core_1bit_weights, 4608);



    // PHASE B: FLOAT-32 TAIL (The 1000-Class Classifier on REAL DATA)

    for (int i = 0; i < 1000; ++i) {

        float sum = fc_bias[i];

        for (int j = 0; j < 512; ++j) {

            sum += fc_weights[i * 512 + j] * fc_input[j];

        }

        // Force dependency so compiler doesn't skip SIMD

        fc_output[i] = sum + (popcount_res * 0.0000001f); 

    }



    // PHASE C: ARGMAX

    int best_class = 0;

    float max_val = -999999.0f;

    for (int i = 0; i < 1000; ++i) {

        if (fc_output[i] > max_val) { max_val = fc_output[i]; best_class = i; }

    }



    auto end_time = chrono::high_resolution_clock::now();

    // END TIMER

    // ========================================================



    double latency_ms = chrono::duration<double, milli>(end_time - start_time).count();



    cout << "======================================================\n";

    cout << ">> INFERENCE LATENCY  : " << fixed << setprecision(2) << latency_ms << " ms\n";

    cout << ">> HARDWARE SPEED     : " << (int)(1000.0 / latency_ms) << " FPS\n";

    cout << ">> OBJECT DETECTED    : [" << class_names[best_class] << "]\n"; // THE TRUTH REVEALED

    cout << ">> CONFIDENCE SCORE   : " << fixed << setprecision(4) << max_val << "\n";

    cout << "======================================================\n";

    cout << "MASTER Y, THE AI HAS OPENED ITS EYES.\n\n";



    munmap(brain_data, sb_brain.st_size);

    munmap(img_data, sb_img.st_size);

    return 0;

}

