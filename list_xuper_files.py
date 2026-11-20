import urllib.request
import json

req = urllib.request.Request('https://api.github.com/repos/xuper-plugin/kino-plugin-xuper/contents', headers={'User-Agent': 'Mozilla/5.0'})
files = json.loads(urllib.request.urlopen(req, timeout=10).read().decode('utf-8'))
for f in files:
    print(f"- {f['name']} ({f['type']}, {f['size']} bytes)")
    if f['name'].endswith('.json') or f['name'].endswith('.js'):
        print("  Download:", f['download_url'])
