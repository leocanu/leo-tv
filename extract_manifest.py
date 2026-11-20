import zipfile
import re

with zipfile.ZipFile('kino.apk', 'r') as z:
    manifest_data = z.read('AndroidManifest.xml')
    # Extract readable strings
    strings = re.findall(rb'[\x20-\x7E]{4,}', manifest_data)
    for s in strings:
        s_str = s.decode('ascii', errors='ignore')
        if any(x in s_str for x in ['kino', 'app', 'activity', 'tv', 'video', 'player']):
            print(s_str)
