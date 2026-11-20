import zipfile, re

with zipfile.ZipFile('pruebas-downloader/lunified-v5.7.4.apk', 'r') as z:
    data = z.read('classes.dex')
    idx = 0
    while True:
        pos = data.find(b'fanatiz', idx)
        if pos == -1: break
        start = max(0, pos - 200)
        end = min(len(data), pos + 300)
        print("--- Fanatiz match ---")
        raw = data[start:end]
        strings = re.findall(b'[a-zA-Z0-9_\\-\\.:/\\?&=%#+~@!,\\s]{4,}', raw)
        for s in strings:
            print("  ", s.decode('ascii', errors='ignore'))
        idx = pos + 7
