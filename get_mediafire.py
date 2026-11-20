import urllib.request
import re

mf_url = 'https://www.mediafire.com/file/fxqduuee67mh03n/kino.apk/file'
headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    'Accept-Language': 'es-ES,es;q=0.9,en;q=0.8'
}

req = urllib.request.Request(mf_url, headers=headers)
try:
    html = urllib.request.urlopen(req, timeout=15).read().decode('utf-8', errors='ignore')
    print("MediaFire HTML length:", len(html))
    
    # Mediafire direct download link pattern
    # <a class="input popsok" ... href="https://download...mediafire.com/..."
    m = re.search(r'href="(https?://download[^"]+)"', html)
    if not m:
        m = re.search(r'aria-label="Download file"\s+href="([^"]+)"', html)
    if not m:
        m = re.search(r'id="downloadButton"[^>]*href="([^"]+)"', html)
    
    if m:
        dl_url = m.group(1)
        print("Direct download URL found:", dl_url)
        with open("direct_dl.txt", "w") as f:
            f.write(dl_url)
    else:
        print("Could not find direct download link via regex. Searching for any download link:")
        for link in re.findall(r'https?://[^"\'<>\s]+', html):
            if 'download' in link and 'mediafire' in link:
                print("Candidate:", link)
except Exception as e:
    print("Error:", e)
