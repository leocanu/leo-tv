import re

with open(r'c:\workspace\app-video-propia-android-v1\scratch\orbit_logcat.txt', 'r', encoding='utf-8', errors='ignore') as f:
    text = f.read()

urls = set(re.findall(r'https?://[^\s"\'<>]+', text))
noise = ['google', 'android', 'schema.org', 'gstatic', 'crashlytics']
clean_urls = [u for u in urls if not any(n in u for n in noise)]
print('Found clean URLs in logcat:')
for u in sorted(clean_urls):
    print('  ', u)

print('\nSearching for key terms (player_api, username, get.php, m3u, etc.):')
for line in text.splitlines():
    if any(k in line.lower() for k in ['player_api', 'username', 'password', 'get.php', '.m3u', 'xtream', 'dns', 'playlist', 'server_info', 'http']):
        if not any(n in line.lower() for n in noise):
            print('MATCH:', line[:180])
