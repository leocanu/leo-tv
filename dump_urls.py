import zipfile, re

with zipfile.ZipFile('pruebas-downloader/lunified-v5.7.4.apk', 'r') as z:
    dex = z.read('classes.dex')
    chunk = dex[5659000:5670000]
    strings = re.findall(b'https?://[^\x00\"\'\\s<>]+', chunk)
    for s in strings:
        print(s.decode('ascii', errors='ignore'))
