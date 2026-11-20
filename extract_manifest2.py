import zipfile
import re

with zipfile.ZipFile('kino.apk', 'r') as z:
    manifest_data = z.read('AndroidManifest.xml')
    # Match UTF-16LE strings: ASCII chars followed by null bytes
    strings = re.findall(b'(?:[\x20-\x7E]\x00){4,}', manifest_data)
    for s in strings:
        s_str = s.decode('utf-16le', errors='ignore')
        if any(x in s_str.lower() for x in ['kino', 'app', 'package', 'activity', 'tv']):
            print(s_str)
