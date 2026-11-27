import re
import zipfile

apk_path = 'c:/workspace/app-video-propia-android-v1/scratch/kino.apk'
with zipfile.ZipFile(apk_path, 'r') as z:
    plugin_js = z.read('assets/builtin/kino-plugins/plugin.js').decode('utf-8', errors='ignore')

# Search for plugin objects or repo references
print("=== EXTRACTING ALL PLUGINS IN BUILTIN plugin.js ===")

repos = re.findall(r'address:\s*["\']([^"\']+)["\']', plugin_js)
github_repos = re.findall(r'github\.com/([^/]+/[^/]+)/', plugin_js)
all_repos = list(set(repos + github_repos))
print(f"Found {len(all_repos)} GitHub plugin repositories:")
for r in sorted(all_repos):
    print(f"  * https://github.com/{r}")

# Search for stremio endpoints
stremio_urls = re.findall(r'stremio:\s*["\']([^"\']+)["\']', plugin_js)
print(f"\nFound {len(stremio_urls)} Stremio endpoints:")
for s in sorted(set(stremio_urls)):
    print(f"  * {s}")

# Search for IDs and Names
items = re.findall(r'id:\s*["\']([^"\']+)["\'],\s*name:\s*["\']([^"\']+)["\']', plugin_js)
print(f"\nFound {len(items)} named plugins:")
for pid, pname in items:
    print(f"  - [{pid}] {pname}")
