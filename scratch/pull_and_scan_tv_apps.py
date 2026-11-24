import subprocess, os, zipfile, re

adb = r"C:\Users\Pc\AppData\Local\Android\Sdk\platform-tools\adb.exe"
out_dir = r"C:\Users\Pc\.gemini\antigravity\brain\2811ca5c-8815-4dd8-b8fd-57a7c13fb3d5\scratch\extracted_apps"

pkgs = [
    "tv.mxliptv.app",
    "tv.mxlmovies.app",
    "com.world.youcinetv",
    "com.i4aukturks.ukturksapp",
    "dev.beamlak.flixquest_v2",
    "com.parky.app.http",
    "com.playfy.tv",
    "com.maxliv.live"
]

for p in pkgs:
    try:
        res = subprocess.check_output([adb, "-s", "emulator-5554", "shell", f"pm path {p}"], text=True).strip()
        if res.startswith("package:"):
            remote_path = res.split("package:")[1].strip()
            local_apk = os.path.join(out_dir, f"{p}.apk")
            if not os.path.exists(local_apk):
                print(f"Pulling {p} from {remote_path}...")
                subprocess.check_call([adb, "-s", "emulator-5554", "pull", remote_path, local_apk])
            
            # Analyze
            print(f"\n=== Analyzing {p} ===")
            with zipfile.ZipFile(local_apk, 'r') as z:
                for n in z.namelist():
                    if n.endswith('.dex') or n.endswith('.js') or n.endswith('.json') or n.startswith('assets/'):
                        try:
                            data = z.read(n)
                            # Look for m3u8 or direct api
                            m3us = re.findall(rb'https?://[^\s\"\'<>]+\.(?:m3u8|m3u|mpd)[^\s\"\'<>]*', data)
                            if m3us:
                                print(f"  [Streams in {n}]:")
                                for u in set(m3us[:8]):
                                    print("   *", u.decode('utf-8', errors='ignore'))
                            
                            # Interesting URLs
                            urls = re.findall(rb'https?://[a-zA-Z0-9_\-\.]+\.[a-zA-Z]{2,6}(?::[0-9]+)?/[a-zA-Z0-9_\-/\?=%&+#\.]*', data)
                            good = set()
                            for u in urls:
                                us = u.decode('utf-8', errors='ignore')
                                if any(k in us.lower() for k in ['api', 'tv', 'stream', 'live', 'mxl', 'youcine', 'turk', 'movie', 'canal']):
                                    if not any(ign in us.lower() for ign in ['google', 'android', 'firebase', 'facebook', 'crashlytics']):
                                        good.add(us)
                            if good:
                                print(f"  [APIs in {n}]:")
                                for g in sorted(list(good))[:6]:
                                    print("   *", g)
                        except Exception as e:
                            pass
    except Exception as e:
        print(f"Error {p}: {e}")
