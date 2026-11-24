import urllib.request, ssl, re

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

url = 'https://tvf90.com/1.php?stream=espn'
req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'})
html = urllib.request.urlopen(req, timeout=5, context=ctx).read().decode('utf-8')
print('HTML length:', len(html))

lines = html.splitlines()
for l in lines:
    if any(k in l.lower() for k in ['m3u8', 'source', 'clappr', 'player', 'iframe', 'eval', 'atob', 'var ']):
        print("  ", l.strip()[:140])
