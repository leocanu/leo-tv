import subprocess,time,xml.etree.ElementTree as ET,uuid,json,pathlib,re
ADB=r"C:\Users\Pc\AppData\Local\Android\Sdk\platform-tools\adb.exe"
R=pathlib.Path(r"C:\workspace\app-video-propia-android-v1")
out=R/("qa-anime-"+time.strftime("%Y%m%d-%H%M%S"))
out.mkdir(exist_ok=True)
def adb(*args):
 p=subprocess.run([ADB,"-s","emulator-5554",*args],capture_output=True,timeout=40)
 if p.returncode:raise RuntimeError(p.stderr.decode(errors="replace"))
 return p.stdout
def dump():
 remote="/sdcard/anime-"+uuid.uuid4().hex+".xml"
 adb("shell","uiautomator","dump",remote)
 data=adb("shell","cat",remote)
 root=ET.fromstring(data)
 return root
def txt(root):return [x.get("text","") for x in root.iter("node") if x.get("text","")]
def tap(node):
 m=list(map(int,re.findall(r"\d+",node.get("bounds"))))
 adb("shell","input","tap",str((m[0]+m[2])//2),str((m[1]+m[3])//2))
 time.sleep(.8)
def find_click(fragment,maximum=35):
 for k in range(maximum):
  root=dump()
  for n in root.iter("node"):
   if n.get("clickable")=="true" and fragment.lower() in n.get("text","").lower():
    tap(n);return True
  adb("shell","input","swipe","1500","920","1500","260","430")
  time.sleep(.3)
 return False
report=[]
for provider in ("TioAnime","LatAnime"):
 entry={"provider":provider}
 try:
  adb("shell","am","force-stop","ar.appvideo.player")
  adb("shell","am","start","-n","ar.appvideo.player/.MainActivity")
  time.sleep(1)
  entry["tile_found"]=find_click(provider,maximum=28)
  if not entry["tile_found"]:raise RuntimeError("tile not found")
  time.sleep(8)
  root=dump()
  entry["initial_texts"]=txt(root)[:14]
  entry["has_search"]=any(n.get("class")=="android.widget.EditText" for n in root.iter("node"))
  if entry["has_search"]:
   n=next(x for x in root.iter("node") if x.get("class")=="android.widget.EditText")
   tap(n)
   adb("shell","input","text","Naruto")
   adb("shell","input","keyevent","4")
   entry["search_clicked"]=find_click("Buscar",maximum=3)
   time.sleep(9)
   entry["search_texts"]=txt(dump())[:18]
  entry["outcome"]="catalog_checked"
 except Exception as e: entry["outcome"]="error";entry["error"]=str(e)
 report.append(entry)
 print(json.dumps(entry,ensure_ascii=False),flush=True)
(out/"results.json").write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding="utf-8")
print("QA_DIR",out)
