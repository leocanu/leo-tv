import subprocess, os, zipfile, re

adb = r"C:\Users\Pc\AppData\Local\Android\Sdk\platform-tools\adb.exe"
out_dir = r"C:\Users\Pc\.gemini\antigravity\brain\2811ca5c-8815-4dd8-b8fd-57a7c13fb3d5\scratch\extracted_apps"

new_pkgs = [
    "com.world.youcinetv",
    "com.zini.tevi",
    "alphastudio.adrama",
    "com.exodus.kodi",
    "com.animfanz22.animapp"
]

print("=== PULLING & ANALYZING USER'S NEW APKS ===")

for p in new_pkgs:
    try:
        res = subprocess.check_output([adb, "-s", "emulator-5554", "shell", f"pm path {p}"], text=True).strip()
        for line in res.splitlines():
            if line.startswith("package:") and "base.apk" in line:
                remote_apk = line.split("package:")[1].strip()
                local_apk = os.path.join(out_dir, f"{p}.apk")
                if not os.path.exists(local_apk):
                    print(f"\nPulling {p} from {remote_apk}...")
                    subprocess.check_call([adb, "-s", "emulator-5554", "pull", remote_apk, local_apk])
                
                print(f"\n----------------------------------------")
                print(f"Analyzing {p} ({os.path.getsize(local_apk)} bytes)")
                print(f"----------------------------------------")
                
                with zipfile.ZipFile(local_apk, 'r') as z:
                    for n in z.namelist():
                        if n.endswith('.dex') or n.endswith('.json') or n.endswith('.js') or n.endswith('.py') or n.startswith('assets/'):
                            try:
                                data = z.read(n)
                            except:
                                continue
                            
                            # M3U8 / Streams
                            streams = re.findall(rb'https?://[^\s\"\'<>]+\.(?:m3u8|m3u|mpd)[^\s\"\'<>]*', data)
                            if streams:
                                print(f"  [Streams in {n}]:")
                                for s in set(streams[:6]):
                                    print("    *", s.decode('utf-8', errors='ignore'))
                            
                            # Domain endpoints
                            urls = re.findall(rb'https?://[a-zA-Z0-9_\-\.]+\.[a-zA-Z]{2,6}(?::[0-9]+)?/[a-zA-Z0-9_\-/\?=%&+#\.]*', data)
                            good = set()
                            for u in urls:
                                us = u.decode('utf-8', errors='ignore')
                                if any(k in us.lower() for k in ['api', 'tv', 'stream', 'live', 'movie', 'serie', 'channel', 'vod', 'youcine', 'tevi', 'exodus', 'anime']):
                                    if not any(ign in us.lower() for ign in ['google', 'android', 'firebase', 'crashlytics', 'facebook', 'schema.org']):
                                        good.add(us)
                            if good:
                                print(f"  [APIs in {n}]:")
                                for g in sorted(list(good))[:8]:
                                    print("    *", g)
    except Exception as e:
        print(f"Error {p}: {e}")
