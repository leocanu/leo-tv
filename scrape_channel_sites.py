import urllib.request, re

def check_site(name, url):
    print(f"\n=== Checking {name} ({url}) ===")
    req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'})
    try:
        html = urllib.request.urlopen(req, timeout=8).read().decode('utf-8', 'ignore')
        m3u8s = re.findall(r'https?://[^\s"\'<>]+\.m3u8[^\s"\'<>]*', html)
        print(f"M3U8s found: {len(m3u8s)}")
        for m in m3u8s:
            print("  m3u8:", m)
        # Check script srcs
        scripts = re.findall(r'<script[^>]+src=["\']([^"\']+)["\']', html)
        for s in scripts:
            if any(k in s.lower() for k in ['player', 'stream', 'live', 'video']):
                print("  script:", s)
    except Exception as e:
        print("  Error:", e)

check_site("America TV", "https://www.americatv.com.ar/vivo")
check_site("TN", "https://tn.com.ar/envivo/24hs/")
check_site("Canal 9", "https://www.elnueve.com.ar/en-vivo/")
check_site("Canal 26", "https://www.canal26.com/en-vivo/")

