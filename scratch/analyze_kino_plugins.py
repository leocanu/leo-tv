import json
import zipfile

apk_path = 'c:/workspace/app-video-propia-android-v1/scratch/kino.apk'
with zipfile.ZipFile(apk_path, 'r') as z:
    seed_data = json.loads(z.read('assets/plugin-catalog-seed.json').decode('utf-8'))
    for p in seed_data['plugins']:
        print(f"ID: {p.get('id')}")
        for k, v in p.items():
            if k not in ['icon', 'banner']:
                print(f"   {k}: {str(v)[:120]}")
        print()

    # Also inspect plugin/stremio-adapter.js, cloudstream-adapter.js, and builtin plugin.js
    print("=== INSPECTING BUILTIN plugin.js ===")
    plugin_js = z.read('assets/builtin/kino-plugins/plugin.js').decode('utf-8', errors='ignore')
    print(f"Length of plugin.js: {len(plugin_js)}")
    lines = [line.strip() for line in plugin_js.split('\n') if 'http' in line or 'api' in line.lower() or 'url' in line.lower() or 'channel' in line.lower() or 'pluto' in line.lower() or 'stream' in line.lower()]
    print(f"Matching lines in builtin plugin.js: {len(lines)}")
    for l in lines[:40]:
        print("  JS:", l[:140])
