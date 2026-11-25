import zipfile
import re

with zipfile.ZipFile(r"C:\Users\Pc\Downloads\tvl1.apk", 'r') as z:
    for name in z.namelist():
        if name.endswith('.dex'):
            data = z.read(name)
            print(f"Buscando en {name} ({len(data)} bytes)...")
            
            # Buscar ocurrencias de palabras clave alrededor de reproductor y cambio de servidor
            keywords = [
                b'inestable', b'inestabilidad', b'cambiando servidor', b'cambiando fuente',
                b'servidor inestable', b'probando otro', b'fuente alternativa',
                b'alternative', b'fallback', b'nextServer', b'changeServer',
                b'onPlayerError', b'onPlaybackStateChanged', b'STATE_BUFFERING',
                b'retry', b'reintent', b'timeout', b'stall'
            ]
            
            for kw in keywords:
                matches = [m.start() for m in re.finditer(kw, data, re.IGNORECASE)]
                if matches:
                    print(f"\nKeyword '{kw.decode()}': {len(matches)} coincidencias")
                    for idx in matches[:5]:
                        start = max(0, idx - 150)
                        end = min(len(data), idx + 200)
                        snippet = data[start:end]
                        # limpiar caracteres no imprimibles
                        clean = "".join([chr(b) if 32 <= b <= 126 else " " for b in snippet])
                        print(f"  [{idx}] ... {clean} ...")
