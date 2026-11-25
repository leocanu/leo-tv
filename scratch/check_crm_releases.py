import urllib.request
import json

url = 'https://api.github.com/repos/leocanu/Chain-Reaction-Music-Updates/releases/tags/windows-v0.1.58-rc1'
req = urllib.request.Request(url, headers={'User-Agent': 'Python'})

try:
    with urllib.request.urlopen(req) as resp:
        data = json.loads(resp.read().decode('utf-8'))
        print('Título:', data.get('name'))
        print('Tag:', data.get('tag_name'))
        print('Fecha:', data.get('published_at'))
        print('\nNotas de la versión:')
        print(data.get('body'))
        print('\nArchivos adjuntos (Assets):')
        for a in data.get('assets', []):
            print(f"  - {a.get('name')} ({a.get('size')} bytes)")
            print(f"    URL: {a.get('browser_download_url')}")
except Exception as e:
    print('Error:', e)
