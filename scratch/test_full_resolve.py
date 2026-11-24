import urllib.request, re, urllib.parse

def clean(text):
    return text.replace("&amp;", "&").replace("&#38;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("\\/", "/")

def video_host(address):
    host = urllib.parse.urlparse(address).netloc.lower()
    return host.endswith("vimeos.net") or host.endswith("vimeos.zip") or host in ("vimeos.net", "vimeos.zip")

def resolve_cinehax(tmdb_id):
    route = f"/e/movie/{tmdb_id}"
    req = urllib.request.Request(f"https://videoapp.mov{route}", headers={"User-Agent": "Mozilla/5.0", "Referer": "https://cinehax.org/"})
    with urllib.request.urlopen(req, timeout=10) as r:
        page = r.read().decode('utf-8')
    m = re.findall(r'<iframe\b[^>]*\bsrc\s*=\s*["\']([^"\']+)', page, re.IGNORECASE)
    embed = None
    for cand in m:
        c = clean(cand)
        if video_host(c):
            embed = c
            break
    print("Embed:", embed)
    if not embed: return None
    # Now VimeosResolver
    req2 = urllib.request.Request(embed, headers={"User-Agent": "Mozilla/5.0", "Referer": "https://videoapp.mov/"})
    with urllib.request.urlopen(req2, timeout=10) as r2:
        html = clean(r2.read().decode('utf-8'))
    LINKS = re.compile(r'https://[^"\'\s<>\\]+\.m3u8[^"\'\s<>\\]*', re.IGNORECASE)
    for mat in LINKS.finditer(html):
        stream = mat.group(0)
        print("Candidate stream:", stream[:70])
        # test fetch
        try:
            req3 = urllib.request.Request(stream, headers={"User-Agent": "Mozilla/5.0", "Referer": "https://vimeos.net/"})
            with urllib.request.urlopen(req3, timeout=10) as r3:
                txt = r3.read().decode('utf-8')
                if txt.strip().startswith("#EXTM3U"):
                    print("SUCCESS! Valid stream verified.")
                    return stream
        except Exception as e:
            print("Stream fetch error:", e)
    return None

resolve_cinehax(664)
