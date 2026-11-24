import urllib.request
import re
import json

# 1. CineHax route
url = 'https://videoapp.mov/e/serie/42280/1/1'
req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0', 'Referer': 'https://cinehax.org/'})
try:
    with urllib.request.urlopen(req, timeout=10) as r:
        content = r.read().decode('utf-8', errors='ignore')
        print('CineHax status:', r.status)
        iframes = re.findall(r'<iframe\b[^>]*\bsrc\s*=\s*["\']([^"\']+)', content, re.I)
        print('CineHax iframes:', iframes)
except Exception as e:
    print('CineHax error:', e)

# 2. How did the user search for Abismo de pasion?
# In CineHax catalog search:
search_url = 'https://cinehax.org/api/rest/v1/serie?q=abismo+de+pasion'
req2 = urllib.request.Request(search_url, headers={'User-Agent': 'Mozilla/5.0', 'Referer': 'https://cinehax.org/'})
try:
    with urllib.request.urlopen(req2, timeout=10) as r:
        data = json.loads(r.read().decode())
        print('CineHax search data count:', len(data.get('data', [])))
        for d in data.get('data', []):
            print(d.get('id'), d.get('title'), d.get('tmdb'), d.get('slug'))
except Exception as e:
    print('CineHax search error:', e)

# 3. SoloLatino
sololatino_url = 'https://sololatino.net/series?q=abismo+de+pasion'
req3 = urllib.request.Request(sololatino_url, headers={'User-Agent': 'Mozilla/5.0'})
try:
    with urllib.request.urlopen(req3, timeout=10) as r:
        c = r.read().decode('utf-8', errors='ignore')
        print('SoloLatino status:', r.status, 'len:', len(c))
except Exception as e:
    print('SoloLatino error:', e)
