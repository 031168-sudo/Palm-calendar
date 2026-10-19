"""После загрузки: ждём обработки сборки и раздаём её внешним группам TestFlight (и просим Apple проверить, если нужно).
Запуск: ASC_VERSION=0.2 ASC_BUILD=27 python3 asc_distribute.py. Ошибки пишем как ::warning, чтобы не ронять сборку."""
import os, sys, time, json, jwt, requests

kid = os.environ["KEY_ID"]; iss = os.environ["ISSUER_ID"]
ver = os.environ["ASC_VERSION"]; build = os.environ["ASC_BUILD"]
key = open(os.path.expanduser(f"~/private_keys/AuthKey_{kid}.p8")).read()
base = "https://api.appstoreconnect.apple.com"
log = []

def token():
    return jwt.encode({"iss": iss, "iat": int(time.time()), "exp": int(time.time()) + 1100, "aud": "appstoreconnect-v1"}, key, algorithm="ES256", headers={"kid": kid})

def api(method, path, body=None, ok=(200, 201, 204)):
    r = requests.request(method, base + path, headers={"Authorization": "Bearer " + token(), "Content-Type": "application/json"},
                         data=json.dumps(body) if body is not None else None)
    if r.status_code not in ok:
        log.append(f"{method} {path} -> {r.status_code} {r.text[:300]}")
        return None
    return r.json() if r.text else {}

def finish(msg):
    print(("::warning title=Ошибки раздача тестировщикам::" + msg + (" | " + " | ".join(log) if log else "")).replace("\n", " "))
    sys.exit(0)

app = api("GET", "/v1/apps?filter[bundleId]=ru.palmdate.app")["data"][0]["id"]

# 1. Ждём, пока Apple обработает сборку (до ~40 минут)
bid = None
for i in range(80):
    r = api("GET", f"/v1/builds?filter[app]={app}&filter[version]={build}&filter[preReleaseVersion.version]={ver}&limit=5")
    if r and r["data"]:
        b = r["data"][0]
        if b["attributes"]["processingState"] == "VALID":
            bid = b["id"]; break
        if b["attributes"]["processingState"] in ("FAILED", "INVALID"):
            finish(f"сборка {ver} ({build}) не обработана: {b['attributes']['processingState']}")
    time.sleep(30)
if not bid:
    finish(f"сборка {ver} ({build}) не обработалась за 40 минут — раздайте вручную")

# 2. Уведомлять тестировщиков автоматически
d = api("GET", f"/v1/builds/{bid}/buildBetaDetail")
if d and d.get("data"):
    api("PATCH", f"/v1/buildBetaDetails/{d['data']['id']}", {"data": {"type": "buildBetaDetails", "id": d["data"]["id"], "attributes": {"autoNotifyEnabled": True}}})

# 3. Во все внешние группы
groups = api("GET", f"/v1/apps/{app}/betaGroups?limit=50")
added = []
for g in (groups or {}).get("data", []):
    if g["attributes"].get("isInternalGroup"):
        continue
    if api("POST", f"/v1/betaGroups/{g['id']}/relationships/builds", {"data": [{"type": "builds", "id": bid}]}) is not None:
        added.append(g["attributes"]["name"])

# 4. Проверка Apple (если сборку ещё не одобрили автоматически)
sub = api("POST", "/v1/betaAppReviewSubmissions", {"data": {"type": "betaAppReviewSubmissions", "relationships": {"build": {"data": {"type": "builds", "id": bid}}}}}, ok=(200, 201))
state = (sub or {}).get("data", {}).get("attributes", {}).get("betaReviewState", "—")
finish(f"сборка {ver} ({build}) добавлена в группы: {', '.join(added) or 'нет'}; проверка Apple: {state}")
