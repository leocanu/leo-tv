with open('xuper_plugin.js', 'r', encoding='utf-8') as f:
    text = f.read()

idx = text.find('src/sources/cablevisionhd.js')
if idx != -1:
    print(text[idx:idx+2500])
else:
    print("Not found")
