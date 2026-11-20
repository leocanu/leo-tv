import zipfile

with zipfile.ZipFile('kino_1069032.apk', 'r') as z:
    for f in ['plugin/stremio-adapter.js', 'plugin/cloudstream-adapter.js']:
        if f in z.namelist():
            data = z.read(f).decode('utf-8', errors='ignore')
            print(f"=== {f} ({len(data)} bytes) ===")
            print(data[:600])
            print("="*60)
