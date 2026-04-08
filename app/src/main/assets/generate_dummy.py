import struct
with open("dummy.uif", "wb") as f:
    # 1. Header: magic('UIF4'), version(40), num_nodes(1), memory_arena(1MB)
    f.write(struct.pack('<4sIII', b'UIF4', 40, 1, 1024*1024))
    # 2. NodeMeta: type(1=CONV2D_XNOR_SCALE), id, in1, in2, in_c(64), in_h(224), in_w(224), out_c(64), out_h(224), out_w(224), k(3), s, p
    # w_offset = 16 bytes (header) + 84 bytes (NodeMeta) = 100
    f.write(struct.pack('<13I4Q', 1, 0, 0, 0, 64, 224, 224, 64, 224, 224, 3, 1, 1, 100, 0, 0, 0))
    # 3. Weights Payload: 64 * 64 * 3 * 3 / 8 = 4608 bytes of random 1-bit weights
    f.write(b'\xAA' * 4608)
print("[UIF SYSTEM] Dummy SIMD Model generated.")
