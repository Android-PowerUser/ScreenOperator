#!/usr/bin/env python3
import json, os, sys, urllib.request, urllib.error

TOKEN = os.environ.get("DENO_DEPLOY_TOKEN", "")
if not TOKEN:
    print("ERROR: DENO_DEPLOY_TOKEN not set!", flush=True)
    sys.exit(1)

APP_NAME = "screenoperator-kilo-proxy"
SOURCE_FILE = "cloudflare-worker/kilo-proxy/kilo_proxy.ts"
API = "https://api.deno.com/v1"

print(f"Token prefix: {TOKEN[:8]}... (len={len(TOKEN)})", flush=True)

def api(method, path, body=None):
    data = json.dumps(body).encode() if body else None
    req = urllib.request.Request(
        f"{API}{path}", data=data, method=method,
        headers={"Authorization": f"Bearer {TOKEN}", "Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req) as r:
            raw = r.read()
            decoded = raw.decode('utf-8', errors='replace')
            print(f"  [{method} {path}] HTTP {r.status}: {decoded[:600]}", flush=True)
            return json.loads(raw) if raw else {}, r.status
    except urllib.error.HTTPError as e:
        raw = e.read()
        decoded = raw.decode('utf-8', errors='replace')
        print(f"  [{method} {path}] HTTP ERROR {e.code}: {decoded[:600]}", flush=True)
        return json.loads(raw) if raw else {}, e.code
    except Exception as e:
        print(f"  [{method} {path}] Exception: {type(e).__name__}: {e}", flush=True)
        sys.exit(1)

# 1. Get user/org info
print("=== Step 1: GET /user ===", flush=True)
user, status = api("GET", "/user")
if status != 200:
    print(f"FATAL: /user returned {status}", flush=True)
    sys.exit(1)
orgs = user.get("organizations", [])
org = orgs[0]["name"] if orgs else user.get("name", "")
print(f"User name: {user.get('name')}, Org used: {org}", flush=True)

# 2. Get existing project or create
print(f"\n=== Step 2: GET /projects/{APP_NAME} ===", flush=True)
project_id = None
get_result, get_status = api("GET", f"/projects/{APP_NAME}")
if get_status == 200:
    project_id = get_result.get("id") or APP_NAME
    print(f"Project exists, id={project_id}", flush=True)
else:
    print(f"\n=== Step 2b: POST /organizations/{org}/projects ===", flush=True)
    create_result, create_status = api("POST", f"/organizations/{org}/projects", {"name": APP_NAME})
    if create_status in (200, 201):
        project_id = create_result.get("id") or APP_NAME
        print(f"Project created, id={project_id}", flush=True)
    elif create_status == 409:
        # Already exists - try fetching by name again
        print(f"\n=== Step 2c: GET /projects/{APP_NAME} (after 409) ===", flush=True)
        get_result2, get_status2 = api("GET", f"/projects/{APP_NAME}")
        if get_status2 == 200:
            project_id = get_result2.get("id") or APP_NAME
        else:
            project_id = APP_NAME
        print(f"Project id resolved: {project_id}", flush=True)
    else:
        print(f"FATAL: Cannot create project, status={create_status}", flush=True)
        sys.exit(1)

# 3. Deploy
print(f"\n=== Step 3: Deploy to project {project_id} ===", flush=True)
try:
    code = open(SOURCE_FILE).read()
    print(f"Source file size: {len(code)} bytes", flush=True)
except Exception as e:
    print(f"FATAL: Cannot read {SOURCE_FILE}: {e}", flush=True)
    sys.exit(1)

assets = {"main.ts": {"kind": "file", "content": code, "encoding": "utf-8"}}
result, status = api("POST", f"/projects/{project_id}/deployments",
    {"entryPointUrl": "main.ts", "assets": assets, "envVars": {}})

if status in (200, 201):
    domains = result.get("domains", [])
    if domains:
        print(f"\nSUCCESS - Live at: https://{domains[0]}", flush=True)
    else:
        dep_id = result.get("id", "unknown")
        dep_url = result.get("url", "")
        print(f"\nSUCCESS - Deployment ID: {dep_id} URL: {dep_url}", flush=True)
else:
    print(f"\nFATAL: Deploy failed with status {status}", flush=True)
    sys.exit(1)
