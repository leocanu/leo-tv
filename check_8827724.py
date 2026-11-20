import urllib.request

req = urllib.request.Request('http://go.aftvnews.com/8827724', headers={'User-Agent': 'Mozilla/5.0'})
html = urllib.request.urlopen(req, timeout=10).read().decode('utf-8', errors='ignore')
print("AFTV 8827724 HTML:")
print(html[:1500])

