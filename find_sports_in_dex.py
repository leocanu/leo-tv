import zipfile, re

with zipfile.ZipFile('pruebas-downloader/lunified-v5.7.4.apk', 'r') as z:
    for name in ['classes.dex', 'classes2.dex']:
        data = z.read(name)
        # Search for TvProvider or Deportes or sports
        for m in re.finditer(rb'L[a-zA-Z0-9_/$]+(?:TvProvider|Sport|Fctv|Channel|Canal)[a-zA-Z0-9_/$]*;', data):
            print("Found class:", m.group(0).decode('ascii', errors='ignore'))
        
        # Search for occurrences of fc.json or fctv
        idx = 0
        while True:
            pos = data.find(b'fc.json', idx)
            if pos == -1: break
            print("Found fc.json context:")
            start = max(0, pos - 100)
            end = min(len(data), pos + 200)
            print(data[start:end])
            idx = pos + 7
