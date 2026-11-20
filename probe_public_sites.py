import json, re, pathlib, urllib.request, urllib.error, concurrent.futures, datetime
root=pathlib.Path(r"C:\workspace\app-video-propia-android-v1")
inventory=json.loads(pathlib.Path(r"C:\workspace\app-tv-propia-fuentes-v1\fuentes.json").read_text(encoding="utf-8-sig"))["sources"]
report_path=root/"app/src/main/assets/provider_audit.json"
report=json.loads(report_path.read_text(encoding="utf-8"))
previous={v["id"]:v for v in report["providers"]}
def check(s):
    result={"id":s["id"],"site_test":"skipped_no_permission","site_http_status":None}
    if not s.get("live_test_allowed",False):return result
    description=s.get("description_declared") or ""
    match=re.search(r"\b(?:[a-zA-Z0-9-]+\.)+[a-zA-Z]{2,}\b",description)
    if not match:
        result["site_test"]="no_hostname_in_inventory";return result
    host=match.group(0)
    if host in {"github.com"}:
        result["site_test"]="no_valid_hostname";return result
    result["host"]=host
    req=urllib.request.Request("https://"+host+"/",method="HEAD",headers={"User-Agent":"VideoPropia-Check/1.0"})
    try:
        with urllib.request.urlopen(req,timeout=9) as response:
            result["site_http_status"]=response.status
            result["site_test"]="http_accessible" if response.status<400 else "http_error"
    except urllib.error.HTTPError as ex:
        result["site_http_status"]=ex.code
        result["site_test"]="http_restricted" if ex.code in (401,403,429) else "http_error"
    except Exception as ex:
        result["site_test"]="network_or_tls_error"
        result["error_type"]=type(ex).__name__
    return result
with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool: site_checks=list(pool.map(check,inventory))
for record in site_checks:
    previous[record["id"]].update(record)
report["providers"]=[previous[s["id"]] for s in inventory]
report["site_checked_at_utc"]=datetime.datetime.now(datetime.timezone.utc).isoformat()
report["site_test_note"]="Single HEAD to the declared homepage only when allowed. No catalog, embeds, authentication, or media playback tested."
counts={}
for x in site_checks: counts[x["site_test"]]=counts.get(x["site_test"],0)+1
report["site_counts"]=counts
data=json.dumps(report,ensure_ascii=False,indent=2)
report_path.write_text(data,encoding="utf-8")
(root/"PROVIDER_AUDIT.json").write_text(data,encoding="utf-8")
print("SITE_COUNTS:",counts)
for r in site_checks:print(r["id"],r["site_test"],r.get("site_http_status"))
