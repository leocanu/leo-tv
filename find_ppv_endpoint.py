import zipfile, re

with zipfile.ZipFile('pruebas-downloader/lunified-v5.7.4.apk', 'r') as z:
    dex = z.read('classes.dex')
    pos = dex.find(b'api.ppv.st')
    while pos != -1:
        start = max(0, pos - 200)
        end = min(len(dex), pos + 300)
        raw = dex[start:end]
        print("PPV MATCH:")
        for s in re.findall(b'[a-zA-Z0-9_\\-\\.:/\\?&=%#+~@!,\\$]{4,}', raw):
            print("  ", s.decode('ascii', errors='ignore'))
        pos = dex.find(b'api.ppv.st', pos + 10)
