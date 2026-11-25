import zipfile
import re

with zipfile.ZipFile(r"C:\Users\Pc\Downloads\tvl1.apk", 'r') as z:
    dex = z.read('classes.dex')

print("=== CLASES EN PLAYER TV / MOBILE / SERVER ===")
classes = set(re.findall(rb'Lcom/skidush/clonex/fragments/player/[a-zA-Z0-9_/$]+;', dex))
for c in sorted(classes):
    print("  *", c.decode('ascii'))

print("\n=== MENSAJES DE SERVIDOR Y FUENTE ===")
# Extraer strings UTF-8 en dex
strings = []
matches = re.finditer(rb'[\x20-\x7e\xc2-\xf4][\x20-\x7e\x80-\xbf]{8,100}', dex)
for m in matches:
    try:
        s = m.group(0).decode('utf-8', errors='ignore')
        if any(k in s.lower() for k in ['servidor inestable', 'buscando la fuente', 'cambiando a', 'servidor recomendado', 'reintentando canal']):
            strings.append(s)
    except:
        pass

for s in sorted(set(strings)):
    print("  ->", s)
