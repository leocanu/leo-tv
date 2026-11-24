import os, zipfile, re

apk_dir = r"C:\Users\Pc\.gemini\antigravity\brain\2811ca5c-8815-4dd8-b8fd-57a7c13fb3d5\scratch\extracted_apps"

apks = [
    "com.plavio.tv.apk",
    "com.Project100Pi.themusicplayer.apk",
    "com.awedea.nyx.apk",
    "buzo_tv_YT-ALCH.apk",
    "plavio.apk",
    "mboxtv.apk",
    "ZappTV-TV001.apk"
]

print("=== SCANNING FOR AUDIO / MUSIC STREAMING SOURCES ===")

for apk in apks:
    path = os.path.join(apk_dir, apk)
    if not os.path.exists(path):
        continue
    print(f"\n---------------------------------------------")
    print(f">>> APP: {apk}")
    print(f"---------------------------------------------")
    with zipfile.ZipFile(path, 'r') as z:
        for n in z.namelist():
            if n.endswith('.dex') or n.endswith('.json') or n.endswith('.js'):
                try:
                    data = z.read(n)
                except:
                    continue
                
                # Audio stream extensions
                streams = re.findall(rb'https?://[^\s\"\'<>]+\.(?:mp3|aac|m4a|ogg|opus|flac|wav)[^\s\"\'<>]*', data)
                if streams:
                    print(f"  [Direct Audio Streams in {n}]:")
                    for s in set(streams[:8]):
                        print("    *", s.decode('utf-8', errors='ignore'))
                
                # Streaming providers (SoundCloud, YouTube Audio, Deezer, Jamendo, Bandcamp, Audiomack, Radio, Shoutcast, Icecast)
                urls = re.findall(rb'https?://[a-zA-Z0-9_\-\.]+\.[a-zA-Z]{2,6}(?::[0-9]+)?/[a-zA-Z0-9_\-/\?=%&+#\.]*', data)
                audio_sources = set()
                for u in urls:
                    us = u.decode('utf-8', errors='ignore')
                    if any(k in us.lower() for k in [
                        'soundcloud', 'piped', 'invidious', 'audiomack', 'deezer', 'jamendo', 
                        'shoutcast', 'icecast', 'radio', 'stream', 'audio', 'yt-dlp', 'cobalt',
                        'music', 'mp3', 'tracks', 'spotify', 'tidal', 'qobuz', 'freemusic'
                    ]):
                        if not any(ign in us.lower() for ign in ['google', 'android', 'firebase', 'crashlytics', 'schema.org']):
                            audio_sources.add(us)
                
                if audio_sources:
                    print(f"  [Audio Sources in {n}]:")
                    for a in sorted(list(audio_sources))[:10]:
                        print("    *", a)
