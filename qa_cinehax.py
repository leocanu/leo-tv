"""Prueba funcional de la integracion propia CineHax en Android TV."""
import pathlib,subprocess,time,json,re,xml.etree.ElementTree as ET,sys
ROOT=pathlib.Path(__file__).resolve().parent
OUT=ROOT/("qa-cinehax-"+time.strftime("%Y%m%d-%H%M%S"));OUT.mkdir()
ADB=r"C:\Users\Pc\AppData\Local\Android\Sdk\platform-tools\adb.exe"
results=[]
def adb(*args):
 p=subprocess.run([ADB,"-s","emulator-5554",*args],capture_output=True,timeout=40)
 if p.returncode:raise RuntimeError(p.stderr.decode("utf-8","replace"))
 return p.stdout
def key(k):
 adb("shell","input","keyevent",str(k));time.sleep(.5)
def ui(label):
 remote="/sdcard/cinehax-"+str(time.time_ns())+".xml"
 adb("shell","uiautomator","dump",remote)
 raw=adb("shell","cat",remote);adb("shell","rm",remote)
 (OUT/(label+".xml")).write_bytes(raw)
 return ET.fromstring(raw)
def tap(tree,fragment):
 n=next(n for n in tree.iter("node") if (n.get("text","").splitlines()[0] == "CineHax" if fragment == "CineHax" and n.get("text") else fragment in n.get("text","")) and n.get("clickable")=="true")
 a,b,c,d=map(int,re.findall(r"\d+",n.get("bounds")))
 adb("shell","input","tap",str((a+c)//2),str((b+d)//2));time.sleep(1)
def session():
 raw=adb("shell","dumpsys","media_session").decode("utf-8","replace")
 if "package=ar.appvideo.player" not in raw:return None
 part=raw.split("package=ar.appvideo.player",1)[1]
 m=re.search(r"state=PlaybackState \{state=(\w+)\(\d+\), position=(\d+)",part)
 return (m.group(1),int(m.group(2))) if m else None
def check(label,passed,detail=""):
 results.append(dict(test=label,passed=bool(passed),detail=detail))
 print(json.dumps(results[-1]),flush=True)
def shot(label):
 adb("shell","screencap","-p","/sdcard/cinehax-qa.png")
 adb("pull","/sdcard/cinehax-qa.png",str(OUT/(label+".png")))
try:
 adb("shell","am","force-stop","ar.appvideo.player")
 adb("shell","am","start","-n","ar.appvideo.player/.MainActivity")
 time.sleep(1)
 for i in range(16):
  tree=ui("home-"+str(i))
  if any(n.get("text","").splitlines()[:1] == ["CineHax"] for n in tree.iter("node")):break
  key(20)
 tap(tree,"CineHax")
 tree=ui("cinehax-channel")
 check("CineHax ofrece Matrix",any("Matrix (1999)" in n.get("text","") for n in tree.iter("node")))
 if "--open-only" in sys.argv:
  key(20)
  print("READY: CineHax abierto, Matrix seleccionado",flush=True)
  sys.exit(0)
 tap(tree,"Matrix (1999)")
 for i in range(35):
  if session():break
  time.sleep(2)
 if not session():
  tree=ui("resolver-error")
  raise RuntimeError(" | ".join(n.get("text","") for n in tree.iter("node") if n.get("text")))
 check("Enlace resuelto dentro de nuestra app",True)
 time.sleep(9)
 key(127)
 first=session()
 check("Matrix avanzo y se pauso",first and first[0]=="PAUSED" and first[1]>1000,str(first))
 shot("matrix-paused")
 key(126);time.sleep(4);key(127)
 second=session()
 check("Matrix reanuda y sigue avanzando",second and first and second[1]>first[1]+1000,str((first,second)))
 shot("matrix-second-frame")
except Exception as e:
 check("Ejecucion",False,str(e))
finally:
 (OUT/"results.json").write_text(json.dumps(results,indent=2,ensure_ascii=False),encoding="utf-8")
 print("QA_DIR="+str(OUT),flush=True)
sys.exit(1 if any(not r["passed"] for r in results) else 0)
