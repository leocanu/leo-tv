import urllib.request
import re
import json

def test_url(name, url, referer="https://google.com"):
    try:
        req = urllib.request.Request(url, headers={
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'
        })
        if referer:
            req.add_header('Referer', referer)
        with urllib.request.urlopen(req, timeout=8) as r:
            body = r.read().decode('utf-8', errors='ignore')
            # Look for abismo or pasion
            found = re.findall(r'abismo\b[^\<\>\"\'\n]*pasi[oó]n', body, re.I)
            links = re.findall(r'href=[\"\']([^\"\']*(?:abismo|pasion)[^\"\']*)[\"\']', body, re.I)
            print(f"[{name}] status={r.status}, abismo_matches={len(found)}, links={links[:3]}")
    except Exception as e:
        print(f"[{name}] error: {e}")

test_url("SoloLatino", "https://sololatino.net/buscar?q=abismo+de+pasion")
test_url("Cuevana3", "https://wv3.cuevana3.eu/search?q=abismo+de+pasion")
test_url("Pelispedia", "https://pelispedia.is/?s=abismo+de+pasion")
test_url("Seriesflix", "https://pelisflix1.guru/?s=abismo+de+pasion")
test_url("CineCalidad", "https://www.cinecalidad.am/?s=abismo+de+pasion")
