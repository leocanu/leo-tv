import urllib.request
import json

req = urllib.request.Request('https://dev.nebulawp.org/stremio/pluto-tv-addon/catalog/tv/pluto_popular.json', headers={'User-Agent': 'Mozilla/5.0'})
with urllib.request.urlopen(req, timeout=10) as r:
    data = json.loads(r.read().decode('utf-8'))
    metas = data.get('metas', [])
    print(f"Total popular Pluto channels: {len(metas)}")
    for m in metas[:35]:
        print(f"  id: '{m.get('id')}', name: '{m.get('name')}', logo: '{m.get('poster')}'")
