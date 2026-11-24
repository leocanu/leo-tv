import urllib.request, json

url = 'https://cinehax.org/api/rest/v1/search?q=tornado&page=1'
req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
with urllib.request.urlopen(req, timeout=10) as resp:
    data = json.loads(resp.read().decode('utf-8'))
    print(json.dumps(data['items'][:3], indent=2))
