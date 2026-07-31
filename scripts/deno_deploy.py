#!/usr/bin/env python3
import json, os, sys, urllib.request, urllib.error

TOKEN = os.environ["DENO_DEPLOY_TOKEN"]
APP_NAME = "screenoperator-kilo-proxy"
SOURCE_FILE = "cloudflare-worker/kilo-proxy/kilo_proxy.ts"
API = "https://api.deno.com/v1"

def api(method, path, body=None):
    data = json.dumps(body).encode() if body else None
    req = urllib.request.Request(
        f"{API}{path}", data=data, method=method,
        headers={"Authorization": f"Bearer {TOKEN}", "Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req) as r:
            raw = r.read()
            print(f"  [{method} {path}] HTTP {r.status}: {raw[:300].decode()}", flush=True)
            return json.loads(raw), r.status
    except urllib.error.HTTPError as e:
        raw = e.read()
        print(f"  [{method} {path}] HTTP {e.code}: {raw[:500].decode()}", flush=True)
        return json.loads(raw) if raw else {}, e.code
    except Exception as e:
        print(f"  [{method} {path}] Exception: {e}", flush=True)
        sys.exit(1)

print(f"Token prefix: {TOKEN[:8]}...", flush=True)

# 1. Get user/org
print("Step 1: get user", flush=True)
user, status = api("GET", "/user")
if status != 200:
    sys.exit(1)
orgs = user.get("organizations", [])
org = orgs[0]["name"] if orgs else user.get("name", "")
print(f"Org: {org}", flush=True)

# 2. Create app
print("Step 2: create app", flush=True)
result, status = api("POST", f"/organizations/{org}/projects", {"name": APP_NAME})
if status not in (200, 201, 409):
    sys.exit(1)

# 3. Deploy
print("Step 3: deploy", flush=True)
code = open(SOURCE_FILE).read()
assets = {"main.ts": {"kind": "file", "content": code, "encoding": "utf-8"}}
result, status = api("POST", f"/projects/{APP_NAME}/deployments",
    {"entryPointUrl": "main.ts", "assets": assets, "envVars": {}})
if status in (200, 201):
    domains = result.get("domains", [])
    print(f"SUCCESS - Live at: https://{domains[0]}" if domains else "SUCCESS (no domain in response)", flush=True)
else:
    print("DEPLOY FAILED", flush=True)
    sys.exit(1)
