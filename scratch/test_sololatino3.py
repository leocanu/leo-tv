import urllib.request
import re

url = 'https://sololatino.net/serie/abismo-de-pasion/temporada-1/episodio-1'
req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'})
with urllib.request.urlopen(req, timeout=10) as resp:
    html = resp.read().decode('utf-8', errors='ignore')
    for m in re.finditer(r'.{0,100}player\.pelisserieshoy\.com.{0,100}', html):
        print('Context:', m.group(0))
