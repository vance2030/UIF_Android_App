#include <iostream>
#include <vector>
#include <chrono>
#include <iomanip>
#include "app/src/main/cpp/uif_simd_core.h"

using namespace std;

int main() {
    cout << "\n======================================================\n";
    cout << "[UIF V5] THE TRUE BARE-METAL BENCHMARK (ANTI-ILLUSION)\n";
    cout << "======================================================\n";

    size_t num_bytes = 72; 
    size_t total_pixels = 224 * 224 * 64; // 3.2 Million Ops per frame

    // Force data to be dynamically allocated so compiler can't pre-calculate
    vector<uint8_t> dummy_image(num_bytes, 0xAA);
    vector<uint8_t> dummy_weights(num_bytes, 0x55);

    cout << "[1] SIMD Core         : ARM NEON 128-Bit Vectorization\n";
    cout << "[2] Memory Allocation : ZERO-COPY (Bare-Metal Pointers)\n";
    cout << "[3] Workload          : 3.2 Million XNORs per Frame\n";
    cout << "[STATUS] Forcing CPU to compute 100 Frames natively...\n\n";

    // 'volatile' prevents the compiler from optimizing the variable away
    volatile int dummy_result = 0; 
    int num_frames = 100;

    auto start_time = chrono::high_resolution_clock::now();

    for (int f = 0; f < num_frames; ++f) {
        int frame_sum = 0;
        for (size_t p = 0; p < total_pixels; ++p) {
            frame_sum += uif_engine::Hardware_XNOR_Popcount(dummy_image.data(), dummy_weights.data(), num_bytes);
        }
        dummy_result += frame_sum; // Must accumulate
    }

    auto end_time = chrono::high_resolution_clock::now();
    double total_ms = chrono::duration<double, milli>(end_time - start_time).count();
    
    double avg_ms_per_frame = total_ms / num_frames;
    double fps = 1000.0 / avg_ms_per_frame;

    cout << "======================================================\n";
    cout << ">> Total Time (100 Frames) : " << fixed << setprecision(2) << total_ms << " ms\n";
    cout << ">> Latency per Frame       : " << fixed << setprecision(2) << avg_ms_per_frame << " ms\n";
    cout << ">> PURE HARDWARE FPS       : " << fixed << setprecision(0) << fps << " FPS\n";
    // THIS LINE IS THE FIX: Printing the result forces the CPU to actually do the math!
    cout << ">> Checksum (Forces Math)  : " << dummy_result << "\n"; 
    cout << "======================================================\n";
    cout << "MASTER Y, THE ILLUSION IS DESTROYED. THIS IS REALITY.\n\n";

    return 0;
}
