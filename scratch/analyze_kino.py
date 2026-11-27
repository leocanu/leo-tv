import zipfile
import re
import os
import json

apk_path = "scratch/kino.apk"
with zipfile.ZipFile(apk_path, 'r') as z:
    file_list = z.namelist()
    print(f"Total files in APK: {len(file_list)}")
    
    # 1. Assets
    assets = [f for f in file_list if f.startswith("assets/")]
    print(f"\n--- Assets ({len(assets)}) ---")
    for a in assets[:30]:
        print(f"  {a} ({z.getinfo(a).file_size} bytes)")
    
    # 2. Extract and inspect text/json/js/properties in assets
    for a in assets:
        if any(a.endswith(ext) for ext in [".json", ".js", ".txt", ".properties", ".xml", ".m3u", ".m3u8", ".kit"]):
            try:
                data = z.read(a).decode('utf-8', errors='ignore')
                print(f"\n=== Asset: {a} (preview 500 chars) ===")
                print(data[:500])
            except Exception as e:
                pass

    # 3. Analyze DEX files for URLs, endpoints, plugins, repositories
    dex_files = [f for f in file_list if f.endswith(".dex")]
    print(f"\n--- DEX Files: {dex_files} ---")
    
    urls = set()
    kino_keywords = set()
    
    url_pattern = re.compile(rb'https?://[a-zA-Z0-9_\-\./\?=%&#~:;+@]+')
    
    for df in dex_files:
        dex_data = z.read(df)
        print(f"Scanning {df} ({len(dex_data)} bytes)...")
        
        matches = url_pattern.findall(dex_data)
        for m in matches:
            try:
                u = m.decode('utf-8', errors='ignore')
                if not any(ign in u for ign in ["schemas.android.com", "w3.org", "google", "android.support", "androidx", "github.com/google"]):
                    urls.add(u)
            except:
                pass
                
        # Look for plugin/repository endpoints
        for kw in [b'plugin', b'repository', b'stream', b'catalog', b'resolver', b'kino', b'arkiv']:
            pos = 0
            while True:
                idx = dex_data.find(kw, pos)
                if idx == -1 or len(kino_keywords) > 50:
                    break
                snippet = dex_data[max(0, idx-20):min(len(dex_data), idx+60)]
                # try to extract readable ascii
                readable = "".join([chr(b) if 32 <= b <= 126 else "." for b in snippet])
                kino_keywords.add(readable)
                pos = idx + len(kw) + 10

    print(f"\n--- Interesting URLs ({len(urls)}) ---")
    for u in sorted(list(urls))[:60]:
        print(f"  {u}")
        
    print(f"\n--- Keywords / Snippets ---")
    for k in list(kino_keywords)[:30]:
        print(f"  {k}")
