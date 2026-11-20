import urllib.request

channels = [
    'LN+', 'LN', 'LNMAS', 'LANACION', 'CRONICA', 'CRONICATV', 'IP', 'CANAL9', 'ELNUEVE', 'TVPUBLICA', 'TELEFE', 'ELTRECE'
]

for ch in channels:
    url = f"http://45.134.141.161:2200/ARG/{ch}/index.m3u8"
    try:
        req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
        with urllib.request.urlopen(req, timeout=3) as resp:
            print(f"[FOUND 200] {url}")
    except Exception:
        pass

