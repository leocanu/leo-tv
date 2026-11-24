import urllib.request
import re

url = 'https://sololatino.net/serie/abismo-de-pasion/temporada-1/episodio-1'
req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'})
try:
    with urllib.request.urlopen(req, timeout=10) as resp:
        print('Final URL:', resp.geturl())
        html = resp.read().decode('utf-8', errors='ignore')
        print('HTML len:', len(html))
        iframes = re.findall(r'<iframe[^>]+src=["\']([^"\']+)["\']', html)
        print('Iframes:', iframes)
except Exception as e:
    print('Error:', e)
