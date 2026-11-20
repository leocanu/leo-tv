import urllib.request

c5n_candidates = [
    'http://45.134.141.161:2200/ARG/C5N/index.m3u8',
    'http://158.172.155.130:8000/play/a0c5/index.m3u8',
    'http://138.59.227.20:8000/play/a05v/index.m3u8',
    'http://c5n.stweb.tv:1935/c5n/live_media/playlist.m3u8',
    'https://c5n.stweb.tv/c5n/live_media/playlist.m3u8',
    'http://45.173.231.22:8000/play/a05v/index.m3u8',
    'http://181.78.79.131:8000/play/a0c5',
    'http://45.184.109.10/live/69393EAE6ADBC65A68F942022362A202/2.m3u8',
    'http://45.184.109.10/live/69393EAE6ADBC65A68F942022362A202/3.m3u8',
    'http://45.184.109.10/live/69393EAE6ADBC65A68F942022362A202/4.m3u8',
    'http://45.184.109.10/live/69393EAE6ADBC65A68F942022362A202/5.m3u8',
]

for url in c5n_candidates:
    try:
        req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
        with urllib.request.urlopen(req, timeout=3) as resp:
            print(f"[FOUND {resp.getcode()}] {url}")
    except Exception as e:
        print(f"[FAIL] {url} -> {e}")

