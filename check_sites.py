import urllib.request, re

sites = {
    'El Nueve': 'https://www.elnueve.com.ar/en-vivo',
    'TV Publica': 'https://www.tvpublica.com.ar/vivo/',
    'Cronica': 'https://www.cronica.com.ar/cronicatv/',
    'LN+': 'https://www.lanacion.com.ar/lnmas/',
    'IP': 'https://ip.digital/en-vivo'
}

for name, url in sites.items():
    try:
        req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'})
        with urllib.request.urlopen(req, timeout=5) as r:
            html = r.read().decode('utf-8', errors='ignore')
            m3u = re.findall(r'https?://[^\s"\'<>]+\.m3u8[^\s"\'<>]*', html)
            yt = re.findall(r'youtube\.com/(?:live|embed|watch)[^\s"\'<>]*', html)
            vids = re.findall(r'videoId["\':\s]+([A-Za-z0-9_-]{11})', html)
            print(f"=== {name} ===")
            if m3u: print("  m3u8:", m3u[:2])
            if yt: print("  yt:", yt[:2])
            if vids: print("  vids:", vids[:2])
    except Exception as e:
        print(f"=== {name} Error: {e} ===")

