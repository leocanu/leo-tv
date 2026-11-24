import urllib.request, json, ssl, re

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

providers = {
    'CineCalidad': 'https://cinecalidad.gg/?s=tornado',
    'Cuevana': 'https://cuevana.biz/search?q=tornado',
    'SoloLatino': 'https://sololatino.net/?s=tornado',
    'Seriesflix': 'https://seriesflix.video/?s=tornado',
    'Pelispedia': 'https://pelispedia.mov/?s=tornado',
}

for name, url in providers.items():
    try:
        req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
        with urllib.request.urlopen(req, context=ctx, timeout=6) as r:
            html = r.read().decode(errors='ignore')
            print(f'{name}: status 200, length {len(html)}')
            links = re.findall(r'<a\b[^>]*\bhref\s*=\s*["\']([^"\']+)["\'][^>]*>(.*?)</a>', html, re.I)
            matching = [l for l in links if any(k in l[1].lower() or k in l[0].lower() for k in ['tornado', 'twister'])]
            print(f'   matching links: {len(matching)}')
            for m in matching[:3]:
                print(f'     href: {m[0]}  title: {m[1].strip()[:50]}')
    except Exception as e:
        print(f'{name} error:', e)
