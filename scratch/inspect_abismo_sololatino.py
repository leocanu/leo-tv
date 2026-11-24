import urllib.request
import re

url = "https://sololatino.net/serie/abismo-de-pasion"
req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'})
with urllib.request.urlopen(req) as r:
    html = r.read().decode('utf-8', errors='ignore')

# Check episodes or season links
episodes = re.findall(r'href=[\"\'](https://sololatino\.net/episodio/[^\"\']+)[\"\']', html)
print(f"Total episode links: {len(episodes)}")
for ep in episodes[:10]:
    print(ep)

# Also check any other episode link formats
ep_relative = re.findall(r'href=[\"\'](/episodio/[^\"\']+)[\"\']', html)
print(f"Total relative episode links: {len(ep_relative)}")
for ep in ep_relative[:10]:
    print(ep)

# Check seasons or tabs
seasons = re.findall(r'data-season=[\"\']([^\"\']+)[\"\']', html)
print(f"Seasons: {seasons}")
