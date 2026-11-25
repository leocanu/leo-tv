import zipfile, struct

with zipfile.ZipFile(r'C:\Users\Pc\Downloads\tvl1.apk', 'r') as z:
    dex = z.read('classes.dex')

string_ids_off = struct.unpack_from('<I', dex, 60)[0]
type_ids_off = struct.unpack_from('<I', dex, 68)[0]
method_ids_off = struct.unpack_from('<I', dex, 92)[0]
field_ids_off = struct.unpack_from('<I', dex, 84)[0]

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

code_off = 0x3b5750
insns_size = struct.unpack_from('<I', dex, code_off + 12)[0]
code_bytes = dex[code_off + 16 : code_off + 16 + insns_size * 2]
base = code_off + 16

pc = 0
while pc < len(code_bytes):
    op = code_bytes[pc]
    addr = base + pc
    adv = 2
    detail = ''
    try:
        if op in (0x6e, 0x6f, 0x70, 0x71, 0x72, 0x74, 0x75, 0x76, 0x77, 0x78):
            m_idx = struct.unpack_from('<H', code_bytes, pc+2)[0]
            detail = f'invoke {get_method_name(m_idx)}'
            adv = 6
        elif op in (0x1a, 0x1b):
            s_idx = struct.unpack_from('<H', code_bytes, pc+2)[0] if op==0x1a else struct.unpack_from('<I', code_bytes, pc+2)[0]
            detail = f'const-string "{get_str(s_idx)}"'
            adv = 4 if op==0x1a else 6
        elif op in (0x52, 0x53, 0x54, 0x55, 0x56, 0x57, 0x58, 0x59, 0x5b, 0x5c, 0x5d, 0x5e, 0x5f):
            f_idx = struct.unpack_from('<H', code_bytes, pc+2)[0]
            c_idx, t_idx, n_idx = struct.unpack_from('<HHI', dex, field_ids_off + f_idx * 8)
            detail = f'field {get_type(c_idx)}->{get_str(n_idx)}'
            adv = 4
        elif op in (0x38, 0x39, 0x3a, 0x3b, 0x3c, 0x3d, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37):
            detail = f'if-jump'
            adv = 4
        elif op in (0x28, 0x29):
            detail = 'goto'
            adv = 2 if op==0x28 else 4
        elif op == 0x13:
            val = struct.unpack_from('<h', code_bytes, pc+2)[0]
            detail = f'const/16 {val}'
            adv = 4
        elif op == 0x14:
            val = struct.unpack_from('<i', code_bytes, pc+2)[0]
            detail = f'const {val}'
            adv = 6
    except Exception as e:
        detail = str(e)
    print(f'{addr:06x}: op={op:02x} {detail}')
    pc += adv
