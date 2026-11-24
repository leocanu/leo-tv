import urllib.request, json, ssl, re

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

# TMDB search
# Let's check which TMDB keys we have in the codebase
import glob
print('Searching TMDB keys in codebase:')
for fn in glob.glob('app/src/main/java/ar/appvideo/player/*.java'):
    c = open(fn, encoding='utf-8', errors='ignore').read()
    keys = re.findall(r'api_key=([a-f0-9]{32})', c)
    if keys:
        print(f'{fn}: {set(keys)}')
