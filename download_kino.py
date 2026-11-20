import urllib.request
import os

with open("direct_dl.txt", "r") as f:
    dl_url = f.read().strip()

print("Downloading from:", dl_url[:80], "...")
headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'
}

req = urllib.request.Request(dl_url, headers=headers)
with urllib.request.urlopen(req, timeout=60) as resp:
    data = resp.read()
    print("Downloaded bytes:", len(data))
    with open("kino.apk", "wb") as f:
        f.write(data)
print("Saved to kino.apk, size:", os.path.getsize("kino.apk"))
