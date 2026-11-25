import zipfile, struct

with zipfile.ZipFile(r'C:\Users\Pc\Downloads\tvl1.apk', 'r') as z:
    dex = z.read('classes.dex')

string_ids_off = struct.unpack_from('<I', dex, 60)[0]
type_ids_off = struct.unpack_from('<I', dex, 68)[0]
field_ids_off = struct.unpack_from('<I', dex, 84)[0]
method_ids_off = struct.unpack_from('<I', dex, 92)[0]

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
    return f'{get_type(c_idx)}->{get_str(n_idx)}'

def get_field_name(idx):
    c_idx, t_idx, n_idx = struct.unpack_from('<HHI', dex, field_ids_off + idx * 8)
    return f'{get_type(c_idx)}->{get_str(n_idx)}:{get_type(t_idx)}'

def dump_range(code_off, start_off, end_off):
    insns_size = struct.unpack_from('<I', dex, code_off + 12)[0]
    code_bytes = dex[code_off + 16 : code_off + 16 + insns_size * 2]
    base = code_off + 16
    pc = 0
    while pc < len(code_bytes):
        addr = base + pc
        op = code_bytes[pc]
        line = f'{addr:06x} (+{pc:04x}): op={op:02x}'
        adv = 2
        try:
            if op == 0x1a:
                reg = code_bytes[pc+1]
                s_idx = struct.unpack_from('<H', code_bytes, pc+2)[0]
                line += f' const-string v{reg}, "{get_str(s_idx)}"'
                adv = 4
            elif op in (0x6e, 0x6f, 0x70, 0x71, 0x72, 0x74, 0x75, 0x76, 0x77, 0x78):
                m_idx = struct.unpack_from('<H', code_bytes, pc+2)[0]
                line += f' invoke {get_method_name(m_idx)}'
                adv = 6
            elif op in (0x52, 0x53, 0x54, 0x55, 0x56, 0x57, 0x58, 0x59):
                f_idx = struct.unpack_from('<H', code_bytes, pc+2)[0]
                line += f' iget {get_field_name(f_idx)}'
                adv = 4
            elif op in (0x5b, 0x5c, 0x5d, 0x5e, 0x5f):
                f_idx = struct.unpack_from('<H', code_bytes, pc+2)[0]
                line += f' iput {get_field_name(f_idx)}'
                adv = 4
            elif op == 0x13:
                line += f' const/16 {struct.unpack_from("<h", code_bytes, pc+2)[0]}'
                adv = 4
            elif op == 0x14:
                line += f' const {struct.unpack_from("<i", code_bytes, pc+2)[0]}'
                adv = 6
            elif op in (0x38, 0x39, 0x3a, 0x3b, 0x3c, 0x3d, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37):
                adv = 4
                line += ' if-branch'
            elif op in (0x28, 0x29):
                adv = 2 if op==0x28 else 4
                line += ' goto'
        except Exception as e:
            pass
        if start_off <= addr <= end_off:
            print(line)
        pc += adv

print("--- Dump around 0x3af222 (Lo/l00;->h) ---")
dump_range(0x3ae6e0, 0x3af150, 0x3af350)

print("\n--- Dump around 0x3b58c6 (Lo/s10;->r) ---")
dump_range(0x3b5750, 0x3b5850, 0x3b5950)
