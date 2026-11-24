import urllib.request
import re

url = "https://sololatino.net/serie/abismo-de-pasion/temporada-1/episodio-1"
req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'})
with urllib.request.urlopen(req) as r:
    html = r.read().decode('utf-8', errors='ignore')

print("Episode 1 Status:", r.status, "len:", len(html))
# Find iframes or players
iframes = re.findall(r'<iframe[^>]+src=[\"\']([^\"\']+)[\"\']', html, re.I)
print("Iframes:", iframes)

data_servers = re.findall(r'data-(?:server|player|url)=[\"\']([^\"\']+)[\"\']', html, re.I)
print("Data servers:", data_servers)

# Find any links to video hosts like mp4upload, streamtape, voe, dood, etc.
hosts = re.findall(r'https?://[^\s\"\'\<\>]+(?:embed|player|stream|video|voe|mp4upload|uqload|filemoon|streamwish)[^\s\"\'\<\>]*', html, re.I)
print("Hosts found:", hosts[:10])
