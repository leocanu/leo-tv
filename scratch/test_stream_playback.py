import urllib.request
import re
import socket

def test_samples():
    socket.setdefaulttimeout(4)
    # Test 10 streams from Free-TV
    req1 = urllib.request.Request("https://raw.githubusercontent.com/Free-TV/IPTV/master/playlist.m3u8", headers={'User-Agent': 'Mozilla/5.0'})
    with urllib.request.urlopen(req1, timeout=10) as resp:
        text1 = resp.read().decode('utf-8', errors='ignore')
    
    urls1 = [line.strip() for line in text1.splitlines() if line.startswith('http://') or line.startswith('https://')][:10]
    print(f"Testing 10 Free-TV streams...")
    for u in urls1:
        try:
            req = urllib.request.Request(u, headers={'User-Agent': 'Mozilla/5.0'})
            with urllib.request.urlopen(req, timeout=4) as r:
                print(f"OK ({r.status}): {u[:70]}")
        except Exception as e:
            print(f"FAIL: {u[:70]} -> {e}")

    req2 = urllib.request.Request("https://www.tdtchannels.com/lists/tv.m3u8", headers={'User-Agent': 'Mozilla/5.0'})
    with urllib.request.urlopen(req2, timeout=10) as resp:
        text2 = resp.read().decode('utf-8', errors='ignore')
    
    urls2 = [line.strip() for line in text2.splitlines() if line.startswith('http://') or line.startswith('https://')][:10]
    print(f"\nTesting 10 TDTChannels streams...")
    for u in urls2:
        try:
            req = urllib.request.Request(u, headers={'User-Agent': 'Mozilla/5.0'})
            with urllib.request.urlopen(req, timeout=4) as r:
                print(f"OK ({r.status}): {u[:70]}")
        except Exception as e:
            print(f"FAIL: {u[:70]} -> {e}")

if __name__ == '__main__':
    test_samples()
