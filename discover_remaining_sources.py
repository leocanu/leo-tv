"""Completa referencias de los candidatos aun no consultados, respetando el aviso de SoloLatino."""
import pathlib,re,json,urllib.request,urllib.parse,concurrent.futures
ROOT=pathlib.Path(__file__).resolve().parent
TMP=pathlib.Path(r"C:\Users\Pc\AppData\Local\Temp\appvideo-catalog-discovery");TMP.mkdir(exist_ok=True)
inventory=json.loads(pathlib.Path(r"C:\workspace\app-tv-propia-fuentes-v1\fuentes.json").read_text(encoding="utf-8-sig"))
refs={}
for entry in inventory["sources"]:
 if entry.get("status")=="candidate_requires_cpp_port":
  url=entry["reference_code_url"].replace("https://github.com/","https://raw.githubusercontent.com/").replace("/blob/","/")
  if url not in refs:
   try:
    with urllib.request.urlopen(url,timeout=15) as r:code=r.read(1000000).decode("utf-8","replace")
    refs[url]=code;(TMP/pathlib.Path(urllib.parse.urlparse(url).path).name).write_text(code,encoding="utf-8")
   except Exception as e:refs[url]="";print("REFERENCE_ERROR",pathlib.Path(urllib.parse.urlparse(url).path).name,type(e).__name__)
def probe(entry):
 result={"id":entry["id"],"name":entry["name"],"playback_verified":False}
 if entry["id"]=="sololatino":result["status"]="excluded_site_warning";return result
 host=None
 match=re.search(r"desde\s+([a-zA-Z0-9.-]+\.[a-zA-Z]{2,})",entry.get("description_declared",""))
 if match:host=match.group(1)
 else:
  key=re.sub(r"[^a-z0-9]","",entry["id"].lower())
  source=refs.get(entry.get("reference_code_url","").replace("https://github.com/","https://raw.githubusercontent.com/").replace("/blob/","/"),"")
  for url in re.findall(r'https?://[^"\s<>]+',source):
   candidate=urllib.parse.urlparse(url).hostname or ""
   normalized=re.sub(r"[^a-z0-9]","",candidate.lower())
   if key in normalized or (key=="veranime" and "verani" in normalized):
    host=candidate;break
 if not host:result["status"]="host_not_identified";return result
 result["host"]=host
 try:
  request=urllib.request.Request("https://"+host+"/",headers={"User-Agent":"Mozilla/5.0"})
  with urllib.request.urlopen(request,timeout=12) as r:
   html=r.read(1000000).decode("utf-8","replace");result["http_status"]=r.status;result["final_host"]=urllib.parse.urlparse(r.url).hostname
  (TMP/(entry["id"]+".html")).write_text(html,encoding="utf-8")
  result.update(status="public_home_fetched",rest_videoapi="/api/rest/v1" in html,wp_api="/wp-api/v1" in html,wordpress="wp-content" in html or "wp-json" in html)
 except Exception as e:result.update(status=type(e).__name__,error=str(e)[:100])
 return result
remaining=[entry for entry in inventory["sources"] if not entry.get("live_test_allowed")]
with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:results=list(pool.map(probe,remaining))
(ROOT/"REMAINING_SOURCE_DISCOVERY.json").write_text(json.dumps(results,indent=2,ensure_ascii=False),encoding="utf-8")
for result in results:print(json.dumps(result,ensure_ascii=True),flush=True)
