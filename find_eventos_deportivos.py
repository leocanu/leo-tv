import zipfile, re

with zipfile.ZipFile('pruebas-downloader/lunified-v5.7.4.apk', 'r') as z:
    data = z.read('classes.dex')
    idx = 0
    while True:
        pos = data.find(b'Eventos Deportivos', idx)
        if pos == -1: break
        start = max(0, pos - 400)
        end = min(len(data), pos + 600)
        print("=== Eventos Deportivos Context ===")
        raw = data[start:end]
        for s in re.findall(b'[a-zA-Z0-9_\\-\\.:/\\?&=%#+~@!,\\$\\s]{4,}', raw):
            st = s.decode('ascii', errors='ignore').strip()
            if st:
                print("  ", st)
        idx = pos + 18
