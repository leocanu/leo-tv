import urllib.request

req = urllib.request.Request('https://raw.githubusercontent.com/xuper-plugin/kino-plugin-xuper/main/kino-plugin.json', headers={'User-Agent': 'Mozilla/5.0'})
content = urllib.request.urlopen(req, timeout=10).read().decode('utf-8')
print("kino-plugin.json:\n", content[:2000])

print("\nDownloading plugin.js...")
req2 = urllib.request.Request('https://raw.githubusercontent.com/xuper-plugin/kino-plugin-xuper/main/plugin.js', headers={'User-Agent': 'Mozilla/5.0'})
pjs = urllib.request.urlopen(req2, timeout=15).read().decode('utf-8')
print("plugin.js size:", len(pjs))
with open('xuper_plugin.js', 'w', encoding='utf-8') as f:
    f.write(pjs)
