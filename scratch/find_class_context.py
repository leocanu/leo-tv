import zipfile
import re

with zipfile.ZipFile(r"C:\Users\Pc\Downloads\tvl1.apk", 'r') as z:
    dex_data = z.read('classes.dex')
    
target = "Servidor inestable"
idx = dex_data.find(target.encode('utf-8'))
if idx != -1:
    print(f"Encontrado en classes.dex en offset {idx}")
    context = dex_data[max(0, idx - 500):min(len(dex_data), idx + 800)]
    # Mostrar strings alrededor
    for s in re.findall(rb'[\x20-\x7e]{4,}', context):
        print("  ->", s.decode('latin1'))

# Tambien buscar referencias a ese string en el bytecode
target_bytes = target.encode('utf-8')
print("\nBuscando referencias a clases relacionadas:")
class_matches = re.findall(rb'Lcom/skidush/clonex/[a-zA-Z0-9_/$]+;', dex_data)
for c in sorted(set(class_matches))[:50]:
    if any(k in c.lower() for k in [b'player', b'video', b'stream', b'channel', b'live', b'server', b'source']):
        print(" ", c.decode('latin1'))
