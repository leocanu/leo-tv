import zipfile
import re

with zipfile.ZipFile('kino.apk', 'r') as z:
    for dex in ['classes.dex', 'classes2.dex']:
        data = z.read(dex)
        print(f"Searching {dex} ({len(data)} bytes)...")
        urls = re.findall(rb'https?://[a-zA-Z0-9_\-\./]+', data)
        for u in set(urls):
            u_str = u.decode('ascii', errors='ignore')
            if any(x in u_str.lower() for x in ['kino', 'arkiv', 'plugin', 'api', 'github', 'stream', 'raw.github']):
                print("  Found URL:", u_str)
