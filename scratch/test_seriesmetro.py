import urllib.request
import re

def test_series(name, url):
    try:
        req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'})
        with urllib.request.urlopen(req, timeout=8) as r:
            body = r.read().decode('utf-8', errors='ignore')
            found = re.findall(r'href=[\"\']([^\"\']*(?:abismo|pasion)[^\"\']*)[\"\'][^>]*>([^<]*)', body, re.I)
            print(f"[{name}] status={r.status}, found={found[:3]}")
    except Exception as e:
        print(f"[{name}] error: {e}")

test_series("SeriesMetro", "https://www3.seriesmetro.net/?s=abismo+de+pasion")
test_series("Pelispedia", "https://pelispedia.is/?s=abismo+de+pasion")
test_series("Seriesflix", "https://pelisflix1.guru/?s=abismo+de+pasion")
