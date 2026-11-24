import urllib.request
import re

url = "https://sololatino.net/serie/abismo-de-pasion"
req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'})
with urllib.request.urlopen(req) as r:
    html = r.read().decode('utf-8', errors='ignore')

print("Page length:", len(html))
# Find all hrefs on this page
hrefs = re.findall(r'href=[\"\']([^\"\']+)[\"\']', html)
print("Unique hrefs with abismo or pasion or episodio or season:", set([h for h in hrefs if any(k in h.lower() for k in ['episodio', 'season', 'capitulo', 'temp', 'abismo', 'pasion'])]))

# Look for titles or text
title_m = re.findall(r'<title>(.*?)</title>', html, re.I)
print("Title:", title_m)

# Find iframes or players
iframes = re.findall(r'<iframe[^>]+src=[\"\']([^\"\']+)[\"\']', html, re.I)
print("Iframes:", iframes)
