import urllib.request

url = 'http://vegetatvv2.duckdns.org/bootstrap.php'
headers = {
    'X-Vegeta-Version-Code': '25',
    'X-Vegeta-Servers-Direct': '1',
    'User-Agent': 'Mozilla/5.0 (compatible; VegetaTVCache/2.0)',
    'Cache-Control': 'no-cache'
}

req = urllib.request.Request(url, headers=headers)
try:
    resp = urllib.request.urlopen(req, timeout=10)
    data = resp.read().decode('utf-8')
    print("Vegeta success! Length:", len(data))
    lines = [l.strip() for l in data.splitlines() if l.strip()]
    print(f"Total lines: {len(lines)}")
    for l in lines[:10]:
        print(" ->", l[:90])
    with open("vegeta_servers.txt", "w", encoding="utf-8") as f:
        f.write(data)
except Exception as e:
    print("Error:", e)
