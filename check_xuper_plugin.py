import urllib.request

urls = [
    'https://raw.githubusercontent.com/xuper-plugin/kino-plugin-xuper/main/plugin.json',
    'https://raw.githubusercontent.com/xuper-plugin/kino-plugin-xuper/main/index.js',
    'https://raw.githubusercontent.com/xuper-plugin/kino-plugin-xuper/main/README.md',
    'https://api.github.com/repos/xuper-plugin/kino-plugin-xuper/contents'
]

for u in urls:
    print("Testing:", u)
    try:
        req = urllib.request.Request(u, headers={'User-Agent': 'Mozilla/5.0'})
        res = urllib.request.urlopen(req, timeout=10).read().decode('utf-8', errors='ignore')
        print("Success! Length:", len(res))
        print("Content preview:\n", res[:1200])
        print("="*60)
    except Exception as e:
        print("Failed:", e)
