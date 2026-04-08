#ifndef UIF_SIMD_CORE_H
#define UIF_SIMD_CORE_H

#include <cstdint>
#include <cstddef>

#if defined(__ARM_NEON) || defined(__aarch64__)
    #include <arm_neon.h>
    #define UIF_USE_NEON 1
#elif defined(__AVX2__) || defined(__x86_64__)
    #include <immintrin.h>
    #define UIF_USE_AVX2 1
#endif

namespace uif_engine {
    inline int Hardware_XNOR_Popcount(const uint8_t* image_bits, const uint8_t* weight_bits, size_t num_bytes) {
        int total_popcount = 0;
        size_t i = 0;

#ifdef UIF_USE_NEON
        uint32x4_t v_acc = vdupq_n_u32(0); 
        for (; i + 15 < num_bytes; i += 16) {
            uint8x16_t v_img = vld1q_u8(image_bits + i);
            uint8x16_t v_wt  = vld1q_u8(weight_bits + i);
            uint8x16_t v_xnor = vmvnq_u8(veorq_u8(v_img, v_wt));
            uint8x16_t v_cnt = vcntq_u8(v_xnor);
            uint16x8_t v_sum1 = vpaddlq_u8(v_cnt, v_cnt);
            uint32x4_t v_sum2 = vpaddlq_u16(v_sum1, v_sum1);
            v_acc = vaddq_u32(v_acc, v_sum2);
        }
        total_popcount += vgetq_lane_u32(v_acc, 0) + vgetq_lane_u32(v_acc, 1) + 
                          vgetq_lane_u32(v_acc, 2) + vgetq_lane_u32(v_acc, 3);
#endif

        for (; i < num_bytes; ++i) {
            uint8_t xnor_val = ~(image_bits[i] ^ weight_bits[i]);
            total_popcount += __builtin_popcount(xnor_val);
        }
        return total_popcount;
    }
}
#endif // UIF_SIMD_CORE_H
