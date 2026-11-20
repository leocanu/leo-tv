import urllib.request
import re

# 1. Check Mediafire for 1069032
mf_url = 'https://www.mediafire.com/file/rgdtkzasunqbg99/kino.apk/file?dkey=hp9bj0e2srm&r=1465'
headers = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'}

print("Checking 1069032 Mediafire...")
try:
    req = urllib.request.Request(mf_url, headers=headers)
    html = urllib.request.urlopen(req, timeout=15).read().decode('utf-8', errors='ignore')
    m = re.search(r'href="(https?://download[^"]+)"', html)
    if m:
        print("1069032 Direct DL:", m.group(1))
        with open("dl_1069032.txt", "w") as f:
            f.write(m.group(1))
    # File details from Mediafire
    fn = re.search(r'class="filename">([^<]+)', html)
    if fn:
        print("Filename:", fn.group(1).strip())
    fs = re.search(r'File size: <span>([^<]+)</span>', html)
    if fs:
        print("File size:", fs.group(1).strip())
except Exception as e:
    print("Error 1069032:", e)

# 2. Check 8827724 (link-target)
lt_url = 'https://link-target.net/8724813/rQzossF9Fe6V'
print("\nChecking 8827724 link-target...")
try:
    req = urllib.request.Request(lt_url, headers=headers)
    resp = urllib.request.urlopen(req, timeout=15)
    print("Final URL:", resp.geturl())
    html2 = resp.read().decode('utf-8', errors='ignore')
    print("Length:", len(html2))
    with open("link_target_8827724.html", "w", encoding="utf-8") as f:
        f.write(html2)
except Exception as e:
    print("Error 8827724:", e)
