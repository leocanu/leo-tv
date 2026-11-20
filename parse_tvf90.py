with open('test_tvf90.py', 'w') as f:
    pass

import urllib.request
import re

url = 'https://tvf90.com/1.php?stream=espn2'
headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    'Referer': 'https://futbol-libre.app/'
}

html = urllib.request.urlopen(urllib.request.Request(url, headers=headers), timeout=10).read().decode('utf-8', errors='ignore')

# Print all iframes or scripts
iframes = re.findall(r'<iframe[^>]*src=["\']([^"\']+)["\']', html)
print("Iframes:", iframes)

scripts = re.findall(r'<script[^>]*>(.*?)</script>', html, re.S)
for i, s in enumerate(scripts):
    if len(s) > 20:
        print(f"Script {i}:\n{s[:400]}\n")
