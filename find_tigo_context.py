import zipfile, re

with zipfile.ZipFile('pruebas-downloader/lunified-v5.7.4.apk', 'r') as z:
    data = z.read('classes.dex')
    pos = data.find(b'Eventos Tigo Sports')
    if pos != -1:
        start = max(0, pos - 1000)
        end = min(len(data), pos + 1000)
        raw = data[start:end]
        for s in re.findall(b'[a-zA-Z0-9_\\-\\.:/\\?&=%#+~@!,\\$\\s]{3,}', raw):
            st = s.decode('ascii', errors='ignore').strip()
            if st:
                print("  ", st)
