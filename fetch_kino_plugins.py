import urllib.request

for url in [
    'https://archive.org/download/kino-app/plugin-catalog.json',
    'https://archive.org/download/kino-app/plugin-community.json',
    'https://archive.org/download/kino-app/latest.json'
]:
    print("Fetching:", url)
    try:
        req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
        data = urllib.request.urlopen(req, timeout=10).read().decode('utf-8', errors='ignore')
        print("Length:", len(data))
        print("Content:\n", data[:1000])
        print("="*60)
    except Exception as e:
        print("Error:", e)

