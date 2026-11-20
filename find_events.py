import zipfile, re

with zipfile.ZipFile('pruebas-downloader/lunified-v5.7.4.apk', 'r') as z:
    data = z.read('classes.dex')
    
    pos = 0
    while True:
        idx = data.find(b'EventsHome', pos)
        if idx == -1: break
        start = max(0, idx - 300)
        end = min(len(data), idx + 300)
        print("Found EventsHome context:")
        # print printable ascii strings
        raw = data[start:end]
        strings = re.findall(b'[a-zA-Z0-9_\\-\\.:/\\?&=%#+~@!,\\s]{4,}', raw)
        for s in strings:
            print("  ", s.decode('ascii', errors='ignore'))
        print("="*40)
        pos = idx + 10
