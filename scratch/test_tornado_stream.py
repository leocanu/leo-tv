import urllib.request, re

tmdb_id = 664 # Tornado (1996) de Helen Hunt / Bill Paxton
url = f'https://videoapp.mov/e/movie/{tmdb_id}'
req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)', 'Referer': 'https://cinehax.org/'})
try:
    with urllib.request.urlopen(req, timeout=10) as resp:
        html = resp.read().decode('utf-8')
        print('videoapp.mov HTML len:', len(html))
        m = re.findall(r'<iframe\b[^>]*\bsrc\s*=\s*["\']([^"\']+)', html)
        print('Iframes in videoapp.mov:', m)
        if m:
            vimeos_url = m[0]
            print('Vimeos url:', vimeos_url)
            v_req = urllib.request.Request(vimeos_url, headers={'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)', 'Referer': 'https://videoapp.mov/'})
            with urllib.request.urlopen(v_req, timeout=10) as v_resp:
                v_html = v_resp.read().decode('utf-8')
                print('Vimeos html len:', len(v_html))
                # buscar file o hls
                files = re.findall(r'file:\s*["\']([^"\']+)["\']', v_html)
                print('Vimeos files:', files)
                hls = re.findall(r'https?://[^\s"\'<>]+\.m3u8[^\s"\'<>]*', v_html)
                print('Vimeos m3u8:', hls)
except Exception as e:
    print('Error:', e)
