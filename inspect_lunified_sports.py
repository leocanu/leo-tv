import zipfile, re, sys

with zipfile.ZipFile('pruebas-downloader/lunified-v5.7.4.apk', 'r') as z:
    dex_files = [n for n in z.namelist() if n.endswith('.dex')]
    print('DEX files:', dex_files)
    
    url_pattern = re.compile(b'https?://[a-zA-Z0-9_\\-\\.:/\\?&=%#+~@!,]+')
    m3u_pattern = re.compile(b'https?://[a-zA-Z0-9_\\-\\.:/\\?&=%#+~@!,]+\\.(?:m3u8?|ts|json|txt)[a-zA-Z0-9_\\-\\.:/\\?&=%#+~@!,]*')
    sports_keywords = [b'deport', b'sport', b'espn', b'fox', b'tyc', b'directv', b'dsports', b'tnt', b'win', b'gol', b'dazn', b'laliga', b'premier']
    
    found_urls = set()
    found_sports = set()
    
    for df in dex_files:
        data = z.read(df)
        for m in url_pattern.finditer(data):
            u = m.group(0).decode('utf-8', errors='ignore')
            if len(u) > 10 and not any(skip in u for skip in ['schemas.android', 'www.w3.org', 'google.com', 'github.com/google', 'example.com', 'apache.org']):
                found_urls.add(u)
                u_lower = u.lower().encode('utf-8')
                if any(k in u_lower for k in sports_keywords):
                    found_sports.add(u)
                    
print('\n--- ALL RELEVANT URLS (' + str(len(found_urls)) + ') ---')
for u in sorted(found_urls):
    if any(k in u.lower() for k in ['api', 'tv', 'stream', 'live', 'channel', 'm3u', 'iptv', 'deport', 'sport', 'play', 'sudamerica', 'lunified']):
        print(u)

print('\n--- SPORTS SPECIFIC URLS ---')
for u in sorted(found_sports):
    print(u)
