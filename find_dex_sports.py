import zipfile, re

with zipfile.ZipFile('pruebas-downloader/lunified-v5.7.4.apk', 'r') as z:
    data = z.read('classes.dex')
    # Find all strings in the string table of dex
    strings = []
    # in dex format, strings are indexed in string_ids
    # let's extract all UTF-8 strings
    found = re.findall(rb'[\x20-\x7e]{5,}', data)
    print("Found total ASCII chunks:", len(found))
    
    # Filter for sports, canales, deport
    sports_hits = set()
    for f in found:
        s = f.decode('ascii', errors='ignore')
        if any(w in s.lower() for w in ['deport', 'espn', 'fox sports', 'tyc sports', 'dsports', 'directv', 'tnt sports', 'win sports', 'gol tv', 'liga profesional', 'champions league', 'copa libertadores', 'f1', 'premier league']):
            sports_hits.add(s)
            
    print("Sports keywords hits:")
    for s in sorted(sports_hits):
        print(" *", s)
