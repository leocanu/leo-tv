import zipfile, re, os

def scan_apk(apk_path, name):
    print(f"\n==========================================")
    print(f"ESCANEANDO FUENTES Y SERVIDORES: {name}")
    print(f"==========================================")
    if not os.path.exists(apk_path):
        print(f"No existe: {apk_path}")
        return

    with zipfile.ZipFile(apk_path, 'r') as z:
        # Check files
        files = z.namelist()
        dex_files = [f for f in files if f.endswith('.dex')]
        asset_files = [f for f in files if f.startswith('assets/')]
        print(f"Archivos DEX: {dex_files}")
        print(f"Assets ({len(asset_files)}): {asset_files[:10]}")

        urls = set()
        streams = set()
        apis = set()

        for f in dex_files + asset_files:
            try:
                data = z.read(f)
                # Find http/https
                found = re.findall(rb'https?://[a-zA-Z0-9_\-\.:]+(?:/[a-zA-Z0-9_\-\./\?%&=#;~]*)?', data)
                for u in found:
                    try:
                        us = u.decode('utf-8', errors='ignore')
                        # filter out standard schemas, android, google, github, etc.
                        if any(skip in us.lower() for skip in ['google', 'android', 'schema', 'w3.org', 'github', 'apache', 'facebook', 'crashlytics', 'firebase', 'amplitude', 'appsflyer', 'onesignal', 'cloudflare', 'adj.st']):
                            continue
                        urls.add(us)
                        if any(ext in us.lower() for ext in ['.m3u8', '.ts', '.mp4', 'stream', 'live', 'play', 'video', 'hls']):
                            streams.add(us)
                        if any(kw in us.lower() for kw in ['api', 'v1', 'v2', 'v3', 'catalog', 'search', 'player', 'server', 'channels']):
                            apis.add(us)
                    except:
                        pass
            except:
                pass

        print(f"\n[STREAM / VIDEO URLS ENCONTRADOS] ({len(streams)}):")
        for s in sorted(streams)[:20]:
            print("  ▶", s)

        print(f"\n[APIS / BACKENDS ENCONTRADOS] ({len(apis)}):")
        for a in sorted(apis)[:20]:
            print("  🌐", a)

        print(f"\n[OTROS DOMINIOS CLAVE] ({len(urls)}):")
        domains = set()
        for u in urls:
            try:
                dom = u.split('/')[2]
                domains.add(dom)
            except:
                pass
        for d in sorted(domains)[:30]:
            print("  🔹", d)

scan_apk(r'C:\Users\Pc\Downloads\PlayTV_Premium_20.1.apk', 'PlayTV Premium')
scan_apk(r'C:\Users\Pc\Downloads\tvl1.apk', 'tvl1 (CloneX)')
