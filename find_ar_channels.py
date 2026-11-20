import re
import urllib.request
import concurrent.futures

files = ['ar_iptv.m3u', 'free_tv_ar.m3u']
channels = []

for fn in files:
    try:
        with open(fn, 'r', encoding='utf-8', errors='ignore') as f:
            lines = [l.strip() for l in f if l.strip()]
        for i in range(len(lines)):
            if lines[i].startswith('#EXTINF:'):
                info = lines[i]
                url = ''
                for j in range(i+1, min(i+5, len(lines))):
                    if lines[j].startswith('http'):
                        url = lines[j]
                        break
                if url:
                    m = re.search(r',([^,]+)$', info)
                    name = m.group(1).strip() if m else 'Canal'
                    logo_m = re.search(r'tvg-logo="([^"]+)"', info)
                    logo = logo_m.group(1) if logo_m else ''
                    channels.append({'name': name, 'url': url, 'logo': logo})
    except Exception as e:
        print(f"Error reading {fn}: {e}")

print(f"Total raw channels extracted: {len(channels)}")

def test_channel(ch):
    url = ch['url']
    req = urllib.request.Request(url, headers={
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
        'Referer': 'https://www.google.com/'
    })
    try:
        with urllib.request.urlopen(req, timeout=3.5) as resp:
            code = resp.getcode()
            if code in [200, 301, 302]:
                return (True, ch, code)
    except Exception:
        pass
    return (False, ch, 0)

alive = []
with concurrent.futures.ThreadPoolExecutor(max_workers=30) as executor:
    results = executor.map(test_channel, channels)
    for ok, ch, code in results:
        if ok:
            alive.append(ch)
            print(f"[ALIVE {code}] {ch['name']} => {ch['url']}")

print(f"\nTotal Alive channels: {len(alive)}")
with open('alive_channels.txt', 'w', encoding='utf-8') as out:
    for ch in alive:
        out.write(f"{ch['name']}\t{ch['url']}\t{ch['logo']}\n")

