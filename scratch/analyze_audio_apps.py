import os, zipfile, re

outDir = r"C:\Users\Pc\.gemini\antigravity\brain\2811ca5c-8815-4dd8-b8fd-57a7c13fb3d5\scratch\extracted_apps"

apps = [
    ("Nyx Music Player", "com.awedea.nyx.apk"),
    ("Pi Music Player", "com.Project100Pi.themusicplayer.apk"),
    ("Lyrics Search / Net", "lyr.search.net.apk")
]

for title, filename in apps:
    apk_path = os.path.join(outDir, filename)
    print(f"\n========================================================")
    print(f"=== ANALYZING: {title} ({filename}) ===")
    print(f"========================================================")
    if not os.path.exists(apk_path):
        print("File not found:", apk_path)
        continue
    
    with zipfile.ZipFile(apk_path, 'r') as z:
        names = z.namelist()
        
        # 1. Native libraries (.so)
        so_files = [n for n in names if n.endswith('.so')]
        print(f"\n[Native Audio Engines / Libraries ({len(so_files)})]:")
        for so in sorted(list(set([os.path.basename(s) for s in so_files]))):
            print(f"  * {so}")
        
        # 2. Key Java/Kotlin classes & packages
        audio_classes = set()
        for n in names:
            if n.endswith('.dex'):
                try:
                    data = z.read(n)
                    # Look for visualizer, equalizer, lyrics, audio classes
                    matches = re.findall(rb'L[a-zA-Z0-9_\$/]+/(?:visualizer|equalizer|effects|lyrics|dsp|player|audio|sound|bass|preset)[a-zA-Z0-9_\$]*;', data, re.I)
                    for m in matches:
                        audio_classes.add(m.decode('utf-8', errors='ignore'))
                except:
                    pass
        
        print(f"\n[Relevant Audio / FX / Visualizer Classes ({len(audio_classes)})]:")
        for c in sorted(list(audio_classes))[:25]:
            print(f"  * {c}")
        
        # 3. Web APIs / Remote services / Lyrics endpoints
        api_urls = set()
        for n in names:
            if n.endswith('.dex') or n.endswith('.json') or n.endswith('.js'):
                try:
                    data = z.read(n)
                    urls = re.findall(rb'https?://[a-zA-Z0-9_\-\.]+\.[a-zA-Z]{2,6}(?::[0-9]+)?/[a-zA-Z0-9_\-/\?=%&+#\.]*', data)
                    for u in urls:
                        us = u.decode('utf-8', errors='ignore')
                        if any(k in us.lower() for k in ['lyrics', 'audio', 'track', 'music', 'genius', 'musixmatch', 'spotify', 'lastfm', 'album', 'artist', 'equalizer']):
                            if not any(ign in us.lower() for ign in ['google', 'android', 'firebase', 'facebook', 'crashlytics']):
                                api_urls.add(us)
                except:
                    pass
        
        print(f"\n[APIs & Lyrics Services ({len(api_urls)})]:")
        for a in sorted(list(api_urls))[:15]:
            print(f"  * {a}")
