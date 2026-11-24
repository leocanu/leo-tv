import zipfile, re

for apk in ['sktechtv.apk', 'playz.apk', 'aksports.apk', 'typhoon.apk']:
    p = r"C:\Users\Pc\.gemini\antigravity\brain\2811ca5c-8815-4dd8-b8fd-57a7c13fb3d5\scratch\extracted_apps\\" + apk
    try:
        with zipfile.ZipFile(p, 'r') as z:
            for n in z.namelist():
                if n.endswith('.dex'):
                    data = z.read(n)
                    # Find all http links
                    urls = re.findall(rb'https?://[a-zA-Z0-9_\-\./\?=%&#:]+', data)
                    good = set()
                    for u in urls:
                        us = u.decode('utf-8', errors='ignore')
                        if any(k in us.lower() for k in ['api', 'channel', 'stream', 'live', 'm3u8', 'tv', 'json']):
                            if not any(ign in us.lower() for ign in ['google', 'android', 'firebase', 'crashlytics', 'facebook']):
                                good.add(us)
                    if good:
                        print(f"=== {apk} ({n}) ===")
                        for g in sorted(list(good))[:15]:
                            print("  ", g)
    except Exception as e:
        print(apk, e)
