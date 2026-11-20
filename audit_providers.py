import json, concurrent.futures, urllib.request, urllib.error, pathlib, hashlib, datetime, re
root=pathlib.Path(r"C:\workspace\app-video-propia-android-v1")
source=pathlib.Path(r"C:\workspace\app-tv-propia-fuentes-v1\fuentes.json")
entries=json.loads(source.read_text(encoding="utf-8-sig"))["sources"]
def check(item):
    link=item.get("reference_code_url") or ""
    raw=link.replace("https://github.com/","https://raw.githubusercontent.com/").replace("/blob/","/")
    out={"id":item["id"],"name":item["name"],"reference":link,
         "live_test_allowed":bool(item.get("live_test_allowed",False)),
         "previous_status":item.get("status"),"reference_status":"unknown",
         "implemented":False,"playback_verified_in_own_app":False}
    if not raw.startswith("https://raw.githubusercontent.com/"):
        out["reference_status"]="no_raw_link";return out
    try:
        req=urllib.request.Request(raw,headers={"User-Agent":"VideoPropia-Audit/1.0"})
        with urllib.request.urlopen(req,timeout=12) as r:
            body=r.read(1024*512)
            out["http_status"]=r.status
            out["reference_status"]="accessible" if r.status==200 else "http_error"
            out["reference_sha256"]=hashlib.sha256(body).hexdigest()
            out["bytes_checked"]=len(body)
            out["reference_language"]="cpp" if raw.endswith(".cpp") else "javascript" if raw.endswith(".js") else "unknown"
    except urllib.error.HTTPError as e:
        out["http_status"]=e.code; out["reference_status"]="http_error"
    except Exception as e:
        out["reference_status"]="network_error";out["error_type"]=type(e).__name__
    return out
with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
    results=list(pool.map(check,entries))
stats={}
for r in results:
    s=r["reference_status"]; stats[s]=stats.get(s,0)+1
report={"schema_version":1,"checked_at_utc":datetime.datetime.now(datetime.timezone.utc).isoformat(),
        "kind":"static_reference_audit_only","note":"No provider playback or copyrighted media was fetched. HTTP access to source code does not imply permission or integration.",
        "total_candidates":len(results),"counts":stats,"providers":results}
out=root/"app"/"src"/"main"/"assets"/"provider_audit.json"
out.parent.mkdir(parents=True,exist_ok=True)
out.write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding="utf-8")
(root/"PROVIDER_AUDIT.json").write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding="utf-8")
print("TOTAL",len(results),"COUNTS",stats)
for r in results:
    print(r["id"],r["reference_status"],r.get("http_status",""),r["reference_language"] if "reference_language" in r else "")
print("SAVED",out)
