import json

with open("fanatiz.json", "w") as f:
    pass

import urllib.request
req = urllib.request.Request("https://watch.fanatiz.com/api/generic/cinematics?tag=fanatizProd", headers={"User-Agent": "Mozilla/5.0"})
data = json.loads(urllib.request.urlopen(req).read().decode('utf-8'))
scenes = data['data']['props']['scenes']
print("Scenes available in Fanatiz:")
for k, v in scenes.items():
    print(k, "->", v.get('route'))
    if 'channels' in k.lower() or 'live' in k.lower() or 'event' in k.lower():
        print("  Details:", json.dumps(v)[:500])
