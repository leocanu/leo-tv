import zipfile, struct

with zipfile.ZipFile('pruebas-downloader/lunified-v5.7.4.apk', 'r') as z:
    dex = z.read('classes.dex')
    
    # Let's search for "tigosports" in dex
    pos = dex.find(b'tigosports.com.py')
    print("tigosports pos:", pos)
    
    # Also search for "fctv33"
    pos_fctv = dex.find(b'fctv33')
    print("fctv33 pos:", pos_fctv)

    # Let's search for all URLs containing sport or tigo or fctv
    import re
    for m in re.finditer(rb'https?://[^\x00\"\'\s<>]+', dex):
        s = m.group(0).decode('ascii', errors='ignore')
        if any(k in s.lower() for k in ['tigo', 'fctv', 'fanatiz', 'sport', 'deport', 'canal', 'channel']):
            print(f"Offset {m.start()}: {s}")
