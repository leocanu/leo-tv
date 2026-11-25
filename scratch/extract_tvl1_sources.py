import zipfile, re

with zipfile.ZipFile(r'C:\Users\Pc\Downloads\tvl1.apk', 'r') as z:
    for dex_name in ['classes.dex', 'classes2.dex']:
        dex = z.read(dex_name)
        print(f"=== {dex_name} ===")
        for pat in [rb'tcore[a-zA-Z0-9_\-\.]+', rb'api\.ppv\.[a-zA-Z0-9_\-\./]+', rb'fctv[a-zA-Z0-9_\-\.]+', rb'https?://[a-zA-Z0-9_\-\.]+\.ru/[a-zA-Z0-9_\-\./]+']:
            matches = set(re.findall(pat, dex))
            for m in sorted(matches):
                try:
                    print(" ", m.decode('latin1'))
                except:
                    pass
