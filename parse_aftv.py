import re

with open('aftv_page.html', 'r', encoding='utf-8') as f:
    text = f.read()

for m in re.finditer(r'<title>(.*?)</title>', text, re.I):
    print('Title:', m.group(1))

# Find meta refresh
for m in re.finditer(r'http-equiv=["\']refresh["\'][^>]*content=["\'][^;]+;\s*url=([^"\']+)["\']', text, re.I):
    print('Meta refresh:', m.group(1))

# Find javascript redirect
for m in re.finditer(r'window\.location\s*=\s*["\']([^"\']+)["\']', text):
    print('Window location:', m.group(1))

for m in re.finditer(r'https?://[^\s"\'<>]+', text):
    u = m.group(0)
    if not any(x in u for x in ['aftvnews', 'w3.org', 'google', 'cloudflare', 'jquery', 'schema.org']):
        print('External link:', u)
