"""Prueba Android real de catalogo, busqueda, peliculas y episodios."""
import pathlib,time,json,re,subprocess,xml.etree.ElementTree as ET,sys
PROVIDER=sys.argv[1] if len(sys.argv)>1 else "CineHax"
ROOT=pathlib.Path(__file__).resolve().parent
OUT=ROOT/("qa-catalog-"+PROVIDER.lower()+"-"+time.strftime("%Y%m%d-%H%M%S"));OUT.mkdir()
ADB=r"C:\Users\Pc\AppData\Local\Android\Sdk\platform-tools\adb.exe"
checks=[]
def adb(*args):
 for attempt in range(2):
  p=subprocess.run([ADB,"-s","emulator-5554",*args],capture_output=True,timeout=45)
  if p.returncode==0:return p.stdout
  error=p.stderr.decode("utf-8","replace")
  if attempt==0 and any(x in error.lower() for x in ["daemon","device offline","cannot connect"]):
   subprocess.run([ADB,"start-server"],capture_output=True,timeout=20);time.sleep(2)
   print("ADB_RECONNECT",flush=True);continue
  raise RuntimeError(error)
def key(code):
 adb("shell","input","keyevent",str(code));time.sleep(.5)
def shot(label):
 adb("shell","screencap","-p","/sdcard/catalog-qa.png");adb("pull","/sdcard/catalog-qa.png",str(OUT/(label+".png")))
def ui(label):
 for attempt in range(3):
  remote="/sdcard/catalog-"+str(time.time_ns())+".xml"
  adb("shell","uiautomator","dump",remote)
  p=subprocess.run([ADB,"-s","emulator-5554","shell","cat",remote],capture_output=True,timeout=10)
  if p.returncode==0:
   adb("shell","rm",remote);(OUT/(label+".xml")).write_bytes(p.stdout)
   tree=ET.fromstring(p.stdout)
   if not any(n.get("package")=="ar.appvideo.player" for n in tree.iter("node")):
    raise RuntimeError("La app no esta visible; el emulador muestra otra pantalla")
   return tree
  time.sleep(1)
 raise RuntimeError("No se obtuvo una captura nueva de interfaz: "+label)
def texts(tree):return [n.get("text","") for n in tree.iter("node") if n.get("text")]
def check(label,passed,detail=""):
 checks.append(dict(test=label,passed=bool(passed),detail=detail));print(json.dumps(checks[-1]),flush=True)
def swipe(up=True):
 adb("shell","input","swipe","1500","930" if up else "350","1500","350" if up else "930","650");time.sleep(.4)
def tap_node(node):
 a,b,c,d=map(int,re.findall(r"\d+",node.get("bounds")))
 adb("shell","input","tap",str((a+c)//2),str((b+d)//2));time.sleep(.7)
def find_tap(fragment,exact=False,limit=14):
 for i in range(limit):
  tree=ui("find-"+str(time.time_ns()))
  for n in tree.iter("node"):
   if n.get("clickable")!="true":continue
   text=n.get("text","")
   matches=text.splitlines()[:1]==[fragment] if exact else fragment in text
   if matches:
    bounds=list(map(int,re.findall(r"\d+",n.get("bounds"))))
    if bounds[3]-bounds[1]>30:tap_node(n);return tree
  swipe()
 raise RuntimeError("No se encuentra boton: "+fragment)
def find_title(title,year):
 pattern=re.compile(re.escape(title)+r"(?: \("+str(year)+r"\))?(?: \u00b7 Serie)?",re.I)
 for i in range(14):
  tree=ui("title-"+str(time.time_ns()))
  for node in tree.iter("node"):
   if node.get("class")=="android.widget.Button" and node.get("clickable")=="true" and pattern.fullmatch(node.get("text","").strip()):
    a,b,c,d=map(int,re.findall(r"\d+",node.get("bounds")))
    if d-b>30:tap_node(node);return
  swipe()
 raise RuntimeError("No se encuentra ficha: "+title)
def top():
 for i in range(10):
  tree=ui("top-"+str(i)+"-"+str(time.time_ns()))
  if any(n.get("class")=="android.widget.EditText" for n in tree.iter("node")):return tree
  swipe(False)
 raise RuntimeError("No se encuentra el buscador")
def wait_text(fragment,seconds=35):
 end=time.time()+seconds
 while time.time()<end:
  tree=ui("wait-"+str(time.time_ns()))
  if any(fragment in text for text in texts(tree)):return tree
  time.sleep(1)
 raise RuntimeError("No aparecio: "+fragment)
def open_provider():
 adb("shell","am","force-stop","ar.appvideo.player");adb("shell","am","start","-W","-n","ar.appvideo.player/.MainActivity");time.sleep(1)
 find_tap(PROVIDER,exact=True)
 return wait_text("titulos ")
def search(query):
 tree=top()
 node=next(n for n in tree.iter("node") if n.get("class")=="android.widget.EditText")
 tap_node(node);key(123)
 adb("shell","input","keyevent","--longpress","67")
 # Seleccionar todo y reemplazar el texto evita depender del teclado del emulador.
 adb("shell","input","keycombination","113","29")
 adb("shell","input","text",query.replace(" ","%s"))
 key(4)
 find_tap("Buscar",exact=True,limit=2)
 return wait_text('Resultados de "'+query+'"')
def session():
 d=adb("shell","dumpsys","media_session").decode("utf-8","replace")
 if "package=ar.appvideo.player" not in d:return None
 p=d.split("package=ar.appvideo.player",1)[1]
 m=re.search(r"state=PlaybackState \{state=(\w+)\(\d+\), position=(\d+)",p)
 return (m.group(1),int(m.group(2))) if m else None
def playback(label):
 end=time.time()+65
 while time.time()<end and not session():time.sleep(1)
 if not session():raise RuntimeError("No se obtuvo video: "+label)
 time.sleep(8);key(127);first=session();shot(label)
 check(label+" reproduce",first and first[1]>1000,str(first))
 key(126);time.sleep(3);key(127);second=session()
 check(label+" reanuda",first and second and second[1]>first[1]+1000,str((first,second)))
try:
 tree=open_provider()
 check("Catalogo online con portadas",any(n.get("class")=="android.widget.ImageView" for n in tree.iter("node")))
 shot("catalog-first-page")
 tree=search("Matrix")
 check("Busqueda Matrix muestra resultados",True)
 find_title("Matrix",1999)
 find_tap("Reproducir Matrix")
 playback("matrix-from-search")
 key(4);key(4);key(4)
 tree=search("Breaking Bad")
 check("Busqueda de serie",True)
 find_title("Breaking Bad",2008)
 find_tap("Temporada 1")
 tree=wait_text("T1 E1")
 check("Lista de episodios",any("T1 E2" in text for text in texts(tree)))
 find_tap("T1 E1")
 playback("breaking-bad-s1e1")
 tree=open_provider()
 find_tap("Cargar mas")
 tree=top()
 tree=wait_text("Pagina 2")
 check("Paginacion siguiente",True)
 shot("catalog-page-two")
except Exception as error:
 check("Ejecucion",False,str(error))
 try:shot("failure")
 except Exception:pass
finally:
 (OUT/"results.json").write_text(json.dumps(checks,indent=2,ensure_ascii=False),encoding="utf-8")
 print("QA_DIR="+str(OUT),flush=True)
sys.exit(1 if any(not x["passed"] for x in checks) else 0)
