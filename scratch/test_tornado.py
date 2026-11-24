import urllib.request, json, ssl, re

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

tmdb_key = 'b3d6832db4ab17b738a9e4063da5fd87'
url = f'https://api.themoviedb.org/3/search/movie?api_key={tmdb_key}&query=Tornado&year=1996&language=es-MX'
req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
tmdb_id = 664
try:
    with urllib.request.urlopen(req, context=ctx, timeout=8) as r:
        data = json.loads(r.read().decode())
        print('TMDB search Tornado 1996:')
        for m in data.get('results', [])[:5]:
            print(f'  id={m["id"]} title={m["title"]} orig={m["original_title"]} release={m.get("release_date")}')
            tmdb_id = m['id']
except Exception as e:
    print('TMDB error:', e)

print(f'Testing CineHax for tmdb_id {tmdb_id}...')
ch_url = f'https://videoapp.mov/e/movie/{tmdb_id}'
req2 = urllib.request.Request(ch_url, headers={'User-Agent': 'Mozilla/5.0', 'Referer': 'https://cinehax.org/'})
try:
    with urllib.request.urlopen(req2, context=ctx, timeout=8) as r:
        html = r.read().decode(errors='ignore')
        print(f'CineHax response length: {len(html)}')
        frames = re.findall(r'<iframe\b[^>]*\bsrc\s*=\s*["\']([^"\']+)', html, re.I)
        print('CineHax iframes:', frames)
except Exception as e:
    print('CineHax error:', e)

embed_url = frames[0]
print('Testing Vimeos embed:', embed_url[:80])
req3 = urllib.request.Request(embed_url, headers={'User-Agent': 'Mozilla/5.0', 'Referer': 'https://videoapp.mov/'})
try:
    with urllib.request.urlopen(req3, context=ctx, timeout=8) as r:
        vhtml = r.read().decode(errors='ignore')
        print(f'Vimeos html length: {len(vhtml)}')
        m3u8s = re.findall(r'https?://[^"\'\s<>\\]+\.m3u8[^"\'\s<>\\]*', vhtml, re.I)
        print('Found m3u8s in embed html:', m3u8s)
        # Check packed or script decoder
        if not m3u8s:
            packed = re.findall(r'eval\(function\(p,a,c,k,e,d\).+?\)\)', vhtml)
            print('Found packed scripts:', len(packed))
except Exception as e:
    print('Vimeos error:', e)
