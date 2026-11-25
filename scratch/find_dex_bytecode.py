import zipfile
import struct

with zipfile.ZipFile(r"C:\Users\Pc\Downloads\tvl1.apk", 'r') as z:
    dex = z.read('classes.dex')

# DEX Header parsing
magic = dex[:8]
string_ids_size = struct.unpack_from('<I', dex, 56)[0]
string_ids_off = struct.unpack_from('<I', dex, 60)[0]
type_ids_size = struct.unpack_from('<I', dex, 64)[0]
type_ids_off = struct.unpack_from('<I', dex, 68)[0]
method_ids_size = struct.unpack_from('<I', dex, 88)[0]
method_ids_off = struct.unpack_from('<I', dex, 92)[0]
class_defs_size = struct.unpack_from('<I', dex, 96)[0]
class_defs_off = struct.unpack_from('<I', dex, 100)[0]

print(f"Strings: {string_ids_size}, Methods: {method_ids_size}, Classes: {class_defs_size}")

# Find string indices of target strings
def get_str(str_idx):
    off = struct.unpack_from('<I', dex, string_ids_off + str_idx * 4)[0]
    # ULEB128 length
    len_bytes = 0
    while (dex[off] & 0x80) != 0:
        off += 1
    off += 1
    # read null-terminated string
    end = dex.find(b'\x00', off)
    return dex[off:end].decode('utf-8', errors='ignore')

target_indices = {}
for i in range(string_ids_size):
    try:
        s = get_str(i)
        if "Servidor inestable" in s or "Reintentando canal" in s:
            print(f"String [{i}]: {s}")
            target_indices[i] = s
    except:
        pass

# Search bytecode instructions: const-string vX, string_id (0x1a) or const-string/jumbo (0x1b)
# 0x1a: [1a] [AA] [BB BB] -> string_id BB BB
# 0x1b: [1b] [AA] [BB BB BB BB] -> string_id
for s_idx, s_text in target_indices.items():
    pat1 = struct.pack('<BH', 0x1a, s_idx) # but op is [1a, reg], then [s_idx (2 bytes)]
    # In dex bytecode: byte 0 = 0x1a, byte 1 = reg, byte 2-3 = s_idx
    print(f"\nBuscando referencias a string {s_idx} ({s_text}):")
    # Scan bytecode
    matches = 0
    for offset in range(0, len(dex) - 4, 2):
        if dex[offset] == 0x1a and struct.unpack_from('<H', dex, offset + 2)[0] == s_idx:
            print(f"  -> const-string en offset 0x{offset:x}")
            matches += 1
        elif dex[offset] == 0x1b and struct.unpack_from('<I', dex, offset + 2)[0] == s_idx:
            print(f"  -> const-string/jumbo en offset 0x{offset:x}")
            matches += 1

