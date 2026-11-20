import subprocess,time,xml.etree.ElementTree as ET,uuid,pathlib,re,json,sys
ADB=r"C:\Users\Pc\AppData\Local\Android\Sdk\platform-tools\adb.exe"
ROOT=pathlib.Path(r"C:\workspace\app-video-propia-android-v1")
OUT=ROOT/("qa-tioanime-"+time.strftime("%Y%m%d-%H%M%S"));OUT.mkdir()
def adb(*args):
 p=subprocess.run([ADB,"-s","emulator-5554",*args],capture_output=True,timeout=40)
 if p.returncode:raise RuntimeError(p.stderr.decode("utf-8","replace"))
 return p.stdout
def ui():
 remote="/sdcard/naruto"+uuid.uuid4().hex+".xml"
 adb("shell","uiautomator","dump",remote)
 data=adb("shell","cat",remote)
 adb("shell","rm",remote)
 return ET.fromstring(data)
def texts(root):return [n.get("text","") for n in root.iter("node") if n.get("text")]
def tap(n):
 a,b,c,d=map(int,re.findall(r"\d+",n.get("bounds")))
 adb("shell","input","tap",str((a+c)//2),str((b+d)//2));time.sleep(.8)
def find(fragment,max_swipes=20,exact=False):
 for i in range(max_swipes):
  root=ui()
  for n in root.iter("node"):
   s=n.get("text","")
   if n.get("clickable")=="true" and ((s==fragment) if exact else (fragment.lower() in s.lower())):
    b=list(map(int,re.findall(r"\d+",n.get("bounds"))))
    if b[3]>b[1] and b[1]<1050 and b[3]>0:tap(n);return True
  adb("shell","input","swipe","1550","880","1550","300","420");time.sleep(.2)
 return False
def waitfor(fragment,seconds=30):
 end=time.monotonic()+seconds
 while time.monotonic()<end:
  r=ui();t=texts(r)
  if any(fragment in x for x in t):return t
  time.sleep(1)
 return texts(ui())
def log(phase):
 r=texts(ui());print(json.dumps({"phase":phase,"texts":r[:18]},ensure_ascii=True),flush=True)
 return r
result={}
try:
 adb("shell","am","force-stop","ar.appvideo.player")
 adb("shell","am","start","-n","ar.appvideo.player/.MainActivity")
 time.sleep(1)
 result["provider"]=find("TioAnime",24,False)
 if not result["provider"]:raise Exception("No encontré tarjeta TioAnime")
 waitfor("Catalogo",35)
 log("catalog")
 n=next((x for x in ui().iter("node") if x.get("class")=="android.widget.EditText"),None)
 if n is None:raise Exception("Sin campo buscar")
 tap(n);adb("shell","input","text","Naruto");adb("shell","input","keyevent","4")
 result["search_button"]=find("Buscar",3,True)
 waitfor('Resultados de "Naruto"',30)
 log("search")
 result["naruto_card"]=find("Naruto Shippuden",12)
 if not result["naruto_card"]:raise Exception("Sin tarjeta Naruto Shippuden")
 log("detail_pre")
 result["episodes_heading"]=waitfor("Episodios",35)
 log("detail_post")
 result["open_episodes"]=find("Episodios",3,True)
 time.sleep(3)
 result["episode_button"]=find("Episodio 1",6)
 time.sleep(12)
 log("episode_attempt")
except Exception as ex:
 result["error"]=repr(ex)
print("RESULT",json.dumps({k:v for k,v in result.items() if k!="episodes_heading"},ensure_ascii=True),flush=True)
(OUT/"result.json").write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding="utf-8")
print("QA_DIR",str(OUT),flush=True)
