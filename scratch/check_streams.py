import urllib.request, json, ssl, re

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

# 1. Twister 1996 (TMDB 664)
# 2. Twisters 2024 (TMDB 762441)

for name, tmdb_id in [('Tornado 1996', 664), ('Tornados 2024', 762441)]:
    url = f'https://videoapp.mov/e/movie/{tmdb_id}'
    req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0', 'Referer': 'https://cinehax.org/'})
    try:
        with urllib.request.urlopen(req, context=ctx, timeout=8) as r:
            html = r.read().decode(errors='ignore')
            frames = re.findall(r'<iframe\b[^>]*\bsrc\s*=\s*["\']([^"\']+)', html, re.I)
            print(f'{name} (tmdb={tmdb_id}):')
            if frames:
                embed = frames[0]
                print(f'   embed: {embed[:70]}')
                # get embed m3u8
                req_em = urllib.request.Request(embed, headers={'User-Agent': 'Mozilla/5.0', 'Referer': 'https://videoapp.mov/'})
                with urllib.request.urlopen(req_em, context=ctx, timeout=8) as rem:
                    ehtml = rem.read().decode(errors='ignore')
                    m3u8s = re.findall(r'https?://[^"\'\s<>\\]+\.m3u8[^"\'\s<>\\]*', ehtml, re.I)
                    if m3u8s:
                        m_url = m3u8s[0]
                        print(f'   m3u8 master: {m_url[:80]}')
                        # read master playlist
                        req_m = urllib.request.Request(m_url, headers={'User-Agent': 'Mozilla/5.0', 'Referer': 'https://vimeos.net/'})
                        with urllib.request.urlopen(req_m, context=ctx, timeout=8) as rm:
                            pl = rm.read().decode(errors='ignore')
                            print('   Playlist lines:')
                            for l in pl.splitlines()[:12]:
                                print('     ', l)
            else:
                print('   No iframe found')
    except Exception as e:
        print(f'{name} error:', e)
