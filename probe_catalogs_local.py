"""Consulta acotada de paginas publicas; no utiliza modelos de IA."""
import pathlib,urllib.request,re,json,concurrent.futures,time
ROOT=pathlib.Path(__file__).resolve().parent
audit=json.loads((ROOT/"PROVIDER_AUDIT.json").read_text(encoding="utf-8-sig"))
OUT=pathlib.Path(r"C:\Users\Pc\AppData\Local\Temp\appvideo-catalog-discovery");OUT.mkdir(exist_ok=True)
def probe(item):
 result={"id":item["id"],"name":item["name"],"host":item.get("host"),"playback_verified":False}
 if not item.get("live_test_allowed") or not item.get("host"):
  result["status"]="skipped_previous_restriction";return result
 if item["id"]=="cinehax":
  result["status"]="rest_catalog_already_verified";return result
 try:
  req=urllib.request.Request("https://"+item["host"]+"/",headers={"User-Agent":"Mozilla/5.0"})
  with urllib.request.urlopen(req,timeout=12) as r:
   h=r.read(1000000).decode("utf-8","replace");result["http_status"]=r.status;result["final_host"]=urllib.parse.urlparse(r.url).hostname
  (OUT/(item["id"]+".html")).write_text(h,encoding="utf-8")
  result["rest_videoapi"]="/api/rest/v1" in h
  result["wp_api"]="/wp-api/v1" in h
  result["wordpress"]="wp-content" in h or "wp-json" in h
  result["status"]="public_home_fetched"
 except Exception as e:result["status"]=type(e).__name__;result["error"]=str(e)[:120]
 return result
with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
 results=list(pool.map(probe,audit["providers"]))
report={"checked_at_host_time":time.strftime("%Y-%m-%d %H:%M:%S"),"note":"HTTP/catalog discovery is not playback verification","providers":results}
(ROOT/"LOCAL_CATALOG_DISCOVERY.json").write_text(json.dumps(report,indent=2,ensure_ascii=False),encoding="utf-8")
for item in results:print(json.dumps(item,ensure_ascii=True),flush=True)
