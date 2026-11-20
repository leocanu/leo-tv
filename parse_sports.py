import urllib.request, re, json

url = 'https://www.fctv33go.monster/es'
req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'})
html = urllib.request.urlopen(req).read().decode('utf-8', errors='ignore')

# Save html snippet or inspect scripts
print("HTML length:", len(html))

# Search for channels, m3u8, streams or JSON
for script in re.findall(r'<script[^>]*>(.*?)</script>', html, re.DOTALL):
    if any(k in script.lower() for k in ['channel', 'stream', 'event', 'm3u8', 'fctv', 'partido', 'deporte']):
        print("Found script data:")
        print(script[:1000])
        print("="*40)

# Extract links
links = re.findall(r'<a[^>]+href=["\']([^"\']+)["\'][^>]*>(.*?)</a>', html, re.DOTALL)
print("Total links:", len(links))
for href, content in links[:50]:
    txt = re.sub(r'<[^>]+>', '', content).strip()
    if txt and len(txt) < 80:
        print(f"{txt} -> {href}")
