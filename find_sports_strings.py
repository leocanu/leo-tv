import zipfile, re

with zipfile.ZipFile('pruebas-downloader/lunified-v5.7.4.apk', 'r') as z:
    data = z.read('classes.dex')
    # Search for all strings containing 'http' or 'api'
    matches = re.findall(b'https?://[a-zA-Z0-9_\\-\\.:/\\?&=%#+~@!,]+', data)
    sports_apis = []
    for m in matches:
        s = m.decode('ascii', errors='ignore')
        if any(w in s.lower() for w in ['sport', 'event', 'canal', 'channel', 'futbol', 'deport', 'fctv', 'live', 'tv']):
            sports_apis.append(s)
    print("Sports/TV related strings:")
    for s in set(sports_apis):
        print(" ->", s)
