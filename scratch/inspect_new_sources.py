import urllib.request
import re

def inspect_m3u(url, name):
    print(f"=== {name} ({url}) ===")
    req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
    with urllib.request.urlopen(req, timeout=15) as resp:
        content = resp.read().decode('utf-8', errors='ignore')
    
    lines = content.splitlines()
    print(f"Total lines: {len(lines)}")
    channels = 0
    groups = {}
    for line in lines:
        if line.startswith('#EXTINF:'):
            channels += 1
            m = re.search(r'group-title="([^"]*)"', line)
            if m:
                g = m.group(1)
                groups[g] = groups.get(g, 0) + 1
    print(f"Total channels: {channels}")
    top_groups = sorted(groups.items(), key=lambda x: x[1], reverse=True)[:15]
    print(f"Top groups: {top_groups}")

if __name__ == '__main__':
    inspect_m3u('https://raw.githubusercontent.com/Free-TV/IPTV/master/playlist.m3u8', 'Free-TV IPTV')
    print()
    inspect_m3u('https://www.tdtchannels.com/lists/tv.m3u8', 'TDTChannels')
