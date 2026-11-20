import json

with open("fetch_kino_plugins.py", "w") as f:
    pass

import urllib.request
req = urllib.request.Request('https://archive.org/download/kino-app/plugin-catalog.json', headers={'User-Agent': 'Mozilla/5.0'})
data = json.loads(urllib.request.urlopen(req, timeout=10).read().decode('utf-8'))

print("Total plugins in Kino:", len(data['plugins']))
for p in data['plugins']:
    print(f"- [{p.get('id')}] {p.get('name')}: {p.get('description')}")
    print(f"  Repo: {p.get('repo')} | Tags: {p.get('tags')}")
