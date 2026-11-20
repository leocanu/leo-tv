import zipfile, re

with zipfile.ZipFile('pruebas-downloader/lunified-v5.7.4.apk', 'r') as z:
    data = z.read('classes.dex')
    # Find all references to EventsHomeTvFragment
    pos = 0
    while True:
        idx = data.find(b'EventsHomeTvFragment', pos)
        if idx == -1: break
        start = max(0, idx - 400)
        end = min(len(data), idx + 800)
        raw = data[start:end]
        print("=== MATCH ===")
        for s in re.findall(b'[a-zA-Z0-9_\\-\\.:/\\?&=%#+~@!,\\$]{4,}', raw):
            print("  ", s.decode('ascii', errors='ignore'))
        pos = idx + 20
