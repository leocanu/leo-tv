import urllib.request
import os

with open("dl_1069032.txt", "r") as f:
    dl_url = f.read().strip()

print("Downloading 1069032 from Mediafire...")
headers = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'}
req = urllib.request.Request(dl_url, headers=headers)
with urllib.request.urlopen(req, timeout=60) as resp:
    data = resp.read()
    with open("kino_1069032.apk", "wb") as f:
        f.write(data)

print("Downloaded kino_1069032.apk, size:", os.path.getsize("kino_1069032.apk"))
