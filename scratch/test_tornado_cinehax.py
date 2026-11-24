import urllib.request, json, re

# Buscar Tornado en CineHax
url = 'https://cinehax.org/api/rest/v1/search?q=tornado&page=1'
req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
try:
    with urllib.request.urlopen(req, timeout=10) as resp:
        data = json.loads(resp.read().decode('utf-8'))
        print('CineHax response items count:', len(data.get('items', [])))
        for it in data.get('items', []):
            print(f"- Title: {it.get('title')} | tmdb: {it.get('tmdb')} | id: {it.get('id')} | year: {it.get('year')} | kind: {it.get('kind')}")
            tmdb_id = it.get('tmdb')
            if tmdb_id:
                route = f'/e/movie/{tmdb_id}'
                v_url = f'https://videoapp.mov{route}'
                try:
                    v_req = urllib.request.Request(v_url, headers={'User-Agent': 'Mozilla/5.0', 'Referer': 'https://cinehax.org/'})
                    with urllib.request.urlopen(v_req, timeout=8) as v_resp:
                        v_html = v_resp.read().decode('utf-8')
                        m = re.findall(r'<iframe\b[^>]*\bsrc\s*=\s*["\']([^"\']+)', v_html)
                        print(f'   videoapp.mov iframe for tmdb {tmdb_id}:', m)
                except Exception as e:
                    print(f'   videoapp.mov error for tmdb {tmdb_id}:', e)
except Exception as e:
    print('CineHax error:', e)
