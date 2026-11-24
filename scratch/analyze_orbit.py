import zipfile, re, sys, os

apk_path = r'C:\Users\Pc\Downloads\Orbit TV-13EBD8EC.apk'

if not os.path.exists(apk_path):
    print("APK not found at", apk_path)
    sys.exit(1)

print(f"Opening APK: {apk_path} ({os.path.getsize(apk_path):,} bytes)")

with zipfile.ZipFile(apk_path, 'r') as z:
    file_list = z.namelist()
    print(f"Total entries in APK: {len(file_list)}")
    
    # Check technology
    print("\n--- Tech Detection ---")
    if any('libflutter.so' in f for f in file_list):
        print("Detected: Flutter app")
    elif any('index.android.bundle' in f for f in file_list):
        print("Detected: React Native app")
    elif any('assets/www' in f for f in file_list):
        print("Detected: Cordova / Capacitor / WebView app")
    elif any('libxamarin' in f or 'assemblies' in f for f in file_list):
        print("Detected: Xamarin app")
    else:
        print("Detected: Native Java / Kotlin app")
        
    dex_files = [f for f in file_list if f.endswith('.dex')]
    print(f"DEX files: {dex_files}")
    
    # Check assets
    assets = [f for f in file_list if f.startswith('assets/')]
    print(f"Assets ({len(assets)}):", assets[:20])
    
    # Scan for interesting strings in DEX and assets
    interesting_patterns = [
        re.compile(rb'https?://[a-zA-Z0-9_\-\.:]+(?:/[^\s"\'<>\x00-\x1f]*)?'),
        re.compile(rb'[a-zA-Z0-9_\-\.]+\.(?:com|net|org|xyz|tv|io|site|online|live|ru|me|top|club|vip|cc|is|to)(?::\d+)?(?:/[^\s"\'<>\x00-\x1f]*)?'),
        re.compile(rb'player_api\.php\?[^\s"\'<>\x00-\x1f]*'),
        re.compile(rb'username=[^\s"\'<>\x00-\x1f]*'),
    ]
    
    found_urls = set()
    found_xtream = set()
    found_domains = set()
    
    for item in dex_files + [a for a in assets if any(a.endswith(ext) for ext in ['.json', '.js', '.txt', '.xml', '.properties', '.html'])]:
        data = z.read(item)
        for p in interesting_patterns:
            for match in p.findall(data):
                try:
                    s = match.decode('utf-8', errors='ignore').strip()
                    if len(s) < 4 or len(s) > 250:
                        continue
                    if 'player_api.php' in s or 'username=' in s or 'password=' in s:
                        found_xtream.add(s)
                    elif s.startswith('http'):
                        found_urls.add(s)
                    elif '.' in s and not s.startswith('android.') and not s.startswith('androidx.') and not s.startswith('java.'):
                        found_domains.add(s)
                except:
                    pass

    print("\n--- Xtream / IPTV API matches ---")
    for x in sorted(found_xtream):
        print("XTREAM:", x)
        
    print("\n--- Non-Standard HTTP(S) URLs (filtering common noise) ---")
    noise = ['google.com', 'schema.org', 'w3.org', 'android.com', 'apache.org', 'github.com', 'facebook.com', 'cloudflare.com', 'crashlytics', 'firebase']
    custom_urls = [u for u in found_urls if not any(n in u for n in noise)]
    for u in sorted(custom_urls)[:60]:
        print("URL:", u)
        
    print(f"\nTotal custom URLs found: {len(custom_urls)}")
