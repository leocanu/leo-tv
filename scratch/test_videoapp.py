import urllib.request, re

url = 'https://videoapp.mov/e/movie/603'
req = urllib.request.Request(url, headers={
    'User-Agent': 'Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    'Referer': 'https://cinehax.org/'
})
with urllib.request.urlopen(req, timeout=10) as r:
    html = r.read().decode('utf-8', errors='ignore')
    iframes = re.findall(r'<iframe\b[^>]*\bsrc\s*=\s*["\']([^"\']+)', html, re.I)
    print('Iframes found in videoapp:', iframes)
    # Check for script or other embed
    scripts = re.findall(r'<script[^>]*src\s*=\s*["\']([^"\']+)', html, re.I)
    print('Scripts found:', scripts)
    for line in html.splitlines():
        if any(k in line.lower() for k in ['vimeos', 'player', 'embed', 'm3u8', 'video']):
            print('Line:', line.strip()[:150])
