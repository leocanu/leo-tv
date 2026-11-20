import urllib.request, concurrent.futures

def check_id(i):
    url = f"http://45.184.109.10/live/69393EAE6ADBC65A68F942022362A202/{i}.m3u8"
    try:
        req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
        with urllib.request.urlopen(req, timeout=2.5) as r:
            if r.getcode() == 200:
                # read first 200 bytes to see title or stream info
                chunk = r.read(300).decode('utf-8', errors='ignore')
                return (i, url, chunk[:60].replace('\n', ' '))
    except Exception:
        pass
    return None

with concurrent.futures.ThreadPoolExecutor(max_workers=25) as ex:
    for res in ex.map(check_id, range(1, 100)):
        if res:
            print(f"ID {res[0]} => {res[1]}")

