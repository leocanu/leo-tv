import re

with open("check_8827724.py", "w") as f:
    pass

import urllib.request
req = urllib.request.Request('http://go.aftvnews.com/8827724', headers={'User-Agent': 'Mozilla/5.0'})
html = urllib.request.urlopen(req, timeout=10).read().decode('utf-8', errors='ignore')

for m in re.finditer(r'window\.location\s*=\s*["\']([^"\']+)["\']', html):
    print("Redirect:", m.group(1))

for m in re.finditer(r'href=["\']([^"\']+)["\']', html):
    if 'aftvnews' not in m.group(1):
        print("Link:", m.group(1))
