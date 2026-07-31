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
            print(f"  [{method} {path}] HTTP {r.status}: {raw[:400].decode()}", flush=True)
            return json.loads(raw) if raw else {}, r.status
    except urllib.error.HTTPError as e:
        raw = e.read()
        print(f"  [{method} {path}] HTTP {e.code}: {raw[:500].decode()}", flush=True)
        return json.loads(raw) if raw else {}, e.code
    except Exception as e:
        print(f"  [{method} {path}] Exception: {e}", flush=True)
        sys.exit(1)

print(f"Token prefix: {TOKEN[:8]}...", flush=True)

# 1. Get user/org info
print("Step 1: get user", flush=True)
user, status = api("GET", "/user")
if status != 200:
    print(f"Failed to get user: {status}", flush=True)
    sys.exit(1)
orgs = user.get("organizations", [])
org = orgs[0]["name"] if orgs else user.get("name", "")
print(f"Using org: {org}", flush=True)

# 2. Get or create project - capture project ID
print("Step 2: get or create project", flush=True)
project_id = None

# Try to get existing project first
get_result, get_status = api("GET", f"/projects/{APP_NAME}")
if get_status == 200:
    project_id = get_result.get("id") or APP_NAME
    print(f"Project exists, id={project_id}", flush=True)
else:
    # Create it
    create_result, create_status = api("POST", f"/organizations/{org}/projects", {"name": APP_NAME})
    if create_status in (200, 201):
        project_id = create_result.get("id") or APP_NAME
        print(f"Project created, id={project_id}", flush=True)
    elif create_status == 409:
        # Already exists, fetch it
        get_result2, get_status2 = api("GET", f"/projects/{APP_NAME}")
        if get_status2 == 200:
            project_id = get_result2.get("id") or APP_NAME
            print(f"Project found after 409, id={project_id}", flush=True)
        else:
            project_id = APP_NAME
            print(f"Falling back to name as id: {project_id}", flush=True)
    else:
        print(f"Failed to create/find project: {create_status}", flush=True)
        sys.exit(1)

# 3. Deploy
print("Step 3: deploy", flush=True)
code = open(SOURCE_FILE).read()
assets = {"main.ts": {"kind": "file", "content": code, "encoding": "utf-8"}}
result, status = api("POST", f"/projects/{project_id}/deployments",
    {"entryPointUrl": "main.ts", "assets": assets, "envVars": {}})
if status in (200, 201):
    domains = result.get("domains", [])
    if domains:
        print(f"SUCCESS - Live at: https://{domains[0]}", flush=True)
    else:
        dep_id = result.get("id", "unknown")
        print(f"SUCCESS - Deployment ID: {dep_id}", flush=True)
else:
    print(f"DEPLOY FAILED with status {status}", flush=True)
    sys.exit(1)
