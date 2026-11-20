import urllib.request
import re

slugs = ['espn', 'espn2', 'espn3', 'espn4', 'espnpremium', 'foxsports', 'foxsports2', 'foxsports3', 'tycsports', 'tntsports', 'dsports', 'goltv']

for s in slugs:
    url = f'https://tvf90.com/5.php?stream={s}'
    headers = {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko)',
        'Referer': f'https://tvf90.com/1.php?stream={s}'
    }
    try:
        html = urllib.request.urlopen(urllib.request.Request(url, headers=headers), timeout=8).read().decode('utf-8', errors='ignore')
        m = re.search(r'(https?://[^\s"\'<>]+\.m3u8[^\s"\'<>]*)', html)
        if m:
            print(f"OK: {s} -> {m.group(1)[:70]}...")
        else:
            print(f"No m3u8: {s}")
    except Exception as e:
        print(f"Error {s}: {e}")
