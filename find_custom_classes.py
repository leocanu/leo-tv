import zipfile, re

with zipfile.ZipFile('pruebas-downloader/lunified-v5.7.4.apk', 'r') as z:
    for name in ['classes.dex', 'classes2.dex']:
        data = z.read(name)
        # Find package names
        classes = re.findall(rb'Lcom/[a-zA-Z0-9_/$]+;', data)
        custom = set()
        for c in classes:
            s = c.decode('ascii', errors='ignore')
            if not any(k in s for k in ['google', 'android', 'airbnb', 'bumptech', 'squareup', 'facebook']):
                custom.add(s)
        print(f"In {name}, found custom classes: {len(custom)}")
        for c in sorted(custom)[:60]:
            print(c)
