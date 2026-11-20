"""Prueba local de pantalla completa con ADB."""
import pathlib,subprocess,time,re,json,sys,xml.etree.ElementTree as ET
BASE=pathlib.Path(__file__).resolve().parent
OUT=BASE/("qa-fullscreen-"+time.strftime("%Y%m%d-%H%M%S"))
OUT.mkdir()
ADB=r"C:\Users\Pc\AppData\Local\Android\Sdk\platform-tools\adb.exe"
checks=[]
def adb(*args):
    p=subprocess.run([ADB,"-s","emulator-5554",*args],capture_output=True,timeout=40)
    if p.returncode: raise RuntimeError(p.stderr.decode("utf-8","replace"))
    return p.stdout
def key(k):
    adb("shell","input","keyevent",str(k)); time.sleep(.7)
def ui(label):
    remote="/sdcard/fullscreen-"+str(time.time_ns())+".xml"
    adb("shell","uiautomator","dump",remote)
    raw=adb("shell","cat",remote)
    adb("shell","rm",remote)
    (OUT/(label+".xml")).write_bytes(raw)
    return ET.fromstring(raw)
def tap(tree,text):
    node=next(n for n in tree.iter("node") if text in n.get("text","") and n.get("clickable")=="true")
    a,b,c,d=map(int,re.findall(r"\d+",node.get("bounds")))
    adb("shell","input","tap",str((a+c)//2),str((b+d)//2)); time.sleep(1)
def shot(label):
    adb("shell","screencap","-p","/sdcard/fullscreen-qa.png")
    adb("pull","/sdcard/fullscreen-qa.png",str(OUT/(label+".png")))
def state():
    raw=adb("shell","dumpsys","media_session").decode()
    part=raw.split("package=ar.appvideo.player",1)[1]
    m=re.search(r"state=PlaybackState \{state=(\w+)\(\d+\), position=(\d+)",part)
    return m.group(1),int(m.group(2))
def check(name,ok,detail=""):
    checks.append(dict(test=name,passed=bool(ok),detail=detail)); print(json.dumps(checks[-1]),flush=True)
def frame(tree):
    node=next(n for n in tree.iter("node") if n.get("resource-id","").endswith("/exo_content_frame"))
    return list(map(int,re.findall(r"\d+",node.get("bounds"))))
try:
    adb("shell","am","force-stop","ar.appvideo.player")
    adb("shell","am","start","-n","ar.appvideo.player/.MainActivity")
    time.sleep(1)
    tap(ui("home"),"BLENDER OPEN MOVIES")
    for i in range(8):
        tree=ui("catalog-"+str(i))
        if any("Elephants Dream" in n.get("text","") for n in tree.iter("node")): break
        time.sleep(2)
    tap(tree,"Elephants Dream")
    time.sleep(6)
    key(127)
    shot("fullscreen")
    adb("shell","input","tap","500","300")  # Ocultar controles animados antes de consultar accesibilidad.
    tree=ui("fullscreen")
    bounds=frame(tree)
    check("Video ocupa pantalla 1920x1080",bounds==[0,0,1920,1080],str(bounds))
    before=state()
    key(4)
    tree=ui("embedded")
    check("Atras sale de pantalla completa",frame(tree)[3]-frame(tree)[1]==620,str(frame(tree)))
    check("Salida conserva posicion y pausa",state()==before,str((before,state())))
    key(82)
    check("Menu vuelve a pantalla completa",frame(ui("menu-fullscreen"))==[0,0,1920,1080])
    key(82)
    tap(ui("button-embedded"),"Pantalla completa")
    check("Boton entra en pantalla completa",frame(ui("button-fullscreen"))==[0,0,1920,1080])
    key(4); key(4)
    tree=ui("channel-back")
    check("Segundo Atras vuelve al canal",any("Elephants Dream" in n.get("text","") for n in tree.iter("node")))
    key(4)
    tap(ui("demo-home"),"DEMO")
    tap(ui("demo-channel"),"Reproducir muestra")
    time.sleep(2)
    shot("demo-fullscreen")
    tree=ui("demo-fullscreen")
    video=next(n for n in tree.iter("node") if n.get("class")=="android.widget.VideoView")
    check("Demo ocupa pantalla completa",video.get("bounds")=="[0,0][1920,1080]",video.get("bounds"))
    key(4); key(4); key(4)
except Exception as e:
    check("Ejecucion",False,str(e))
finally:
    (OUT/"results.json").write_text(json.dumps(checks,indent=2,ensure_ascii=False),encoding="utf-8")
    print("QA_DIR="+str(OUT),flush=True)
sys.exit(1 if any(not c["passed"] for c in checks) else 0)
