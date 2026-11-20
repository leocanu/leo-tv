import urllib.request
import re

url = 'http://go.aftvnews.com/4397343'
req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36'})
try:
    resp = urllib.request.urlopen(req, timeout=15)
    final_url = resp.geturl()
    print("Final URL:", final_url)
    html = resp.read().decode('utf-8', errors='ignore')
    print("HTML length:", len(html))
    with open('aftv_page.html', 'w', encoding='utf-8') as f:
        f.write(html)
except Exception as e:
    print("Error:", e)
