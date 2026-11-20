import zipfile, re

with zipfile.ZipFile('pruebas-downloader/lunified-v5.7.4.apk', 'r') as z:
    data = z.read('classes.dex')
    
    # Search for all mentions of channels or sports lists
    matches = re.findall(rb'https?://[^\x00\"\'\s<>]+', data)
    seen = set()
    for m in matches:
        s = m.decode('ascii', errors='ignore')
        if s not in seen:
            seen.add(s)
            if any(k in s.lower() for k in ['json', 'm3u', 'fctv', 'strem', 'tigo', 'fanatiz', 'sudamerica', 'event', 'sport']):
                print(s)
