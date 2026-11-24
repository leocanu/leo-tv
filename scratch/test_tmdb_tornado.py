import urllib.request, json

apiKey = '4ef0d7355d9ffb5151e987764708ce96'
url = f'https://api.themoviedb.org/3/search/movie?api_key={apiKey}&query=Tornado&language=es-MX'
req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
with urllib.request.urlopen(req, timeout=10) as resp:
    data = json.loads(resp.read().decode('utf-8'))
    for r in data.get('results', [])[:5]:
        print(f"ID: {r.get('id')} | Title: {r.get('title')} | Release: {r.get('release_date')}")

url_year = f'https://api.themoviedb.org/3/search/movie?api_key={apiKey}&query=Tornado&language=es-MX&year=1996'
req2 = urllib.request.Request(url_year, headers={'User-Agent': 'Mozilla/5.0'})
with urllib.request.urlopen(req2, timeout=10) as resp:
    data2 = json.loads(resp.read().decode('utf-8'))
    print('\nCon year=1996:')
    for r in data2.get('results', [])[:5]:
        print(f"ID: {r.get('id')} | Title: {r.get('title')} | Release: {r.get('release_date')}")
