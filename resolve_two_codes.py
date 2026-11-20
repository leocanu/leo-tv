import urllib.request
import re
import os

codes = ['8827724', '1069032']
headers = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'}

for code in codes:
    url = f'http://go.aftvnews.com/{code}'
    print(f"\n================ Resolving code {code} ================")
    try:
        req = urllib.request.Request(url, headers=headers)
        resp = urllib.request.urlopen(req, timeout=15)
        final_url = resp.geturl()
        html = resp.read().decode('utf-8', errors='ignore')
        
        # Check window.location redirect in aftv page
        m = re.search(r'window\.location\s*=\s*["\']([^"\']+)["\']', html)
        if m:
            target = m.group(1)
        else:
            m = re.search(r'content=["\'][^;]+;\s*url=([^"\']+)["\']', html, re.I)
            if m:
                target = m.group(1)
            else:
                target = final_url
        print(f"Code {code} -> Target URL: {target}")
        with open(f"target_{code}.txt", "w") as f:
            f.write(target)
    except Exception as e:
        print(f"Error resolving {code}: {e}")
