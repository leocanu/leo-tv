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

code_off = 0x138110
insns_size = struct.unpack_from('<I', dex, code_off + 12)[0]
code_bytes = dex[code_off + 16 : code_off + 16 + insns_size * 2]

print('Disassembly of PlayerTvFragment->a0:')
pc = 0
while pc < len(code_bytes):
    op = code_bytes[pc]
    line = f'{pc:04x}: {op:02x} '
    try:
        if op == 0x1a:
            reg = code_bytes[pc+1]
            s_idx = struct.unpack_from('<H', code_bytes, pc+2)[0]
            line += f'const-string v{reg}, "{get_str(s_idx)}"'
            pc += 4
        elif op == 0x1b:
            reg = code_bytes[pc+1]
            s_idx = struct.unpack_from('<I', code_bytes, pc+2)[0]
            line += f'const-string/jumbo v{reg}, "{get_str(s_idx)}"'
            pc += 6
        elif op in (0x6e, 0x74):
            m_idx = struct.unpack_from('<H', code_bytes, pc+2)[0]
            line += f'invoke-virtual {get_method_name(m_idx)}'
            pc += 6
        elif op in (0x70, 0x71, 0x72):
            m_idx = struct.unpack_from('<H', code_bytes, pc+2)[0]
            line += f'invoke-direct/static {get_method_name(m_idx)}'
            pc += 6
        elif op in (0x52, 0x53, 0x54, 0x55, 0x56, 0x57, 0x58, 0x59):
            f_idx = struct.unpack_from('<H', code_bytes, pc+2)[0]
            line += f'iget {get_field_name(f_idx)}'
            pc += 4
        elif op in (0x5b, 0x5c, 0x5d, 0x5e, 0x5f):
            f_idx = struct.unpack_from('<H', code_bytes, pc+2)[0]
            line += f'iput {get_field_name(f_idx)}'
            pc += 4
        else:
            pc += 2
            continue
        print(' ', line)
    except Exception as e:
        pc += 2
