import zipfile, struct

with zipfile.ZipFile(r'C:\Users\Pc\Downloads\tvl1.apk', 'r') as z:
    dex = z.read('classes.dex')

string_ids_off = struct.unpack_from('<I', dex, 60)[0]
type_ids_off = struct.unpack_from('<I', dex, 68)[0]
method_ids_off = struct.unpack_from('<I', dex, 92)[0]
class_defs_size = struct.unpack_from('<I', dex, 96)[0]
class_defs_off = struct.unpack_from('<I', dex, 100)[0]

def get_str(idx):
    off = struct.unpack_from('<I', dex, string_ids_off + idx * 4)[0]
    while (dex[off] & 0x80) != 0: off += 1
    off += 1
    end = dex.find(b'\x00', off)
    return dex[off:end].decode('utf-8', errors='ignore')

def get_type(idx):
    s_idx = struct.unpack_from('<I', dex, type_ids_off + idx * 4)[0]
    return get_str(s_idx)

def get_method_name(idx):
    c_idx, p_idx, n_idx = struct.unpack_from('<HHI', dex, method_ids_off + idx * 8)
    return get_type(c_idx), get_str(n_idx)

def read_uleb128(off):
    result = 0
    shift = 0
    while True:
        b = dex[off]
        off += 1
        result |= (b & 0x7f) << shift
        if (b & 0x80) == 0:
            break
        shift += 7
    return result, off

targets = [0x3af222, 0x3b58c6]

for i in range(class_defs_size):
    c_off = class_defs_off + i * 32
    class_idx, access_flags, superclass_idx, interfaces_off, source_file_idx, annotations_off, class_data_off, static_values_off = struct.unpack_from('<IIIIIIII', dex, c_off)
    if class_data_off == 0:
        continue
    cname = get_type(class_idx)
    off = class_data_off
    static_fields_size, off = read_uleb128(off)
    instance_fields_size, off = read_uleb128(off)
    direct_methods_size, off = read_uleb128(off)
    virtual_methods_size, off = read_uleb128(off)
    
    for _ in range(static_fields_size + instance_fields_size):
        _, off = read_uleb128(off)
        _, off = read_uleb128(off)
        
    m_idx = 0
    for _ in range(direct_methods_size):
        delta, off = read_uleb128(off)
        m_idx += delta
        access, off = read_uleb128(off)
        code_off, off = read_uleb128(off)
        if code_off > 0:
            insns_size = struct.unpack_from('<I', dex, code_off + 12)[0]
            code_end = code_off + 16 + insns_size * 2
            for t in targets:
                if code_off <= t < code_end:
                    print(f'Caller 0x{t:x} in direct method {cname}->{get_method_name(m_idx)} (code_off=0x{code_off:x})')
                    
    m_idx = 0
    for _ in range(virtual_methods_size):
        delta, off = read_uleb128(off)
        m_idx += delta
        access, off = read_uleb128(off)
        code_off, off = read_uleb128(off)
        if code_off > 0:
            insns_size = struct.unpack_from('<I', dex, code_off + 12)[0]
            code_end = code_off + 16 + insns_size * 2
            for t in targets:
                if code_off <= t < code_end:
                    print(f'Caller 0x{t:x} in virtual method {cname}->{get_method_name(m_idx)} (code_off=0x{code_off:x})')
