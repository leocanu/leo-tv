import urllib.request

url = "https://files.catbox.moe/a9x8yj.apk"
req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
with urllib.request.urlopen(req, timeout=15) as resp:
    data = resp.read(1024)
    print("Downloaded bytes:", len(data))
    print("PK zip header check:", data[:2] == b"PK")
