import json
import urllib.request
import re

def analyze():
    print("=== Analyzing Free-TV IPTV playlist ===")
    req1 = urllib.request.Request("https://raw.githubusercontent.com/Free-TV/IPTV/master/playlist.m3u8", headers={'User-Agent': 'Mozilla/5.0'})
    with urllib.request.urlopen(req1, timeout=15) as resp:
        text1 = resp.read().decode('utf-8', errors='ignore')
    
    print("=== Analyzing TDTChannels playlist ===")
    req2 = urllib.request.Request("https://www.tdtchannels.com/lists/tv.m3u8", headers={'User-Agent': 'Mozilla/5.0'})
    with urllib.request.urlopen(req2, timeout=15) as resp:
        text2 = resp.read().decode('utf-8', errors='ignore')

    def parse_m3u(text):
        channels = []
        cur = {}
        for line in text.splitlines():
            line = line.strip()
            if line.startswith('#EXTINF:'):
                cur = {}
                m_logo = re.search(r'tvg-logo="([^"]*)"', line)
                m_id = re.search(r'tvg-id="([^"]*)"', line)
                m_group = re.search(r'group-title="([^"]*)"', line)
                comma = line.rfind(',')
                title = line[comma+1:].strip() if comma >= 0 else "Canal"
                cur['title'] = title
                cur['logo'] = m_logo.group(1) if m_logo else ""
                cur['id'] = m_id.group(1) if m_id else ""
                cur['group'] = m_group.group(1) if m_group else "General"
            elif line.startswith('http://') or line.startswith('https://'):
                if cur:
                    cur['url'] = line
                    channels.append(cur)
                    cur = {}
        return channels

    ch1 = parse_m3u(text1)
    ch2 = parse_m3u(text2)
    print(f"Free-TV: {len(ch1)} streams")
    print(f"TDTChannels: {len(ch2)} streams")

    # Inspect groups
    g1 = {}
    for c in ch1:
        g = c['group']
        g1[g] = g1.get(g, 0) + 1
    print("Free-TV groups sample:", sorted(g1.items(), key=lambda x: x[1], reverse=True)[:10])

    g2 = {}
    for c in ch2:
        g = c['group']
        g2[g] = g2.get(g, 0) + 1
    print("TDTChannels groups sample:", sorted(g2.items(), key=lambda x: x[1], reverse=True)[:10])

if __name__ == '__main__':
    analyze()
