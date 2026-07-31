#!/usr/bin/env python3
"""Deploys kilo_proxy.ts to Deno Deploy via REST API."""
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
            return json.loads(r.read()), r.status
    except urllib.error.HTTPError as e:
        return json.loads(e.read()), e.code

# 1. Get org name
user, _ = api("GET", "/user")
orgs = user.get("organizations", [])
org = orgs[0]["name"] if orgs else user.get("name", "")
print(f"Org: {org}")

# 2. Create app (409 = already exists, both are fine)
result, status = api("POST", f"/organizations/{org}/projects", {"name": APP_NAME})
print(f"Create app: HTTP {status} - {result.get('name', result.get('error', result))}")

# 3. Read source
code = open(SOURCE_FILE).read()

# 4. Deploy
assets = {"main.ts": {"kind": "file", "content": code, "encoding": "utf-8"}}
deploy_payload = {"entryPointUrl": "main.ts", "assets": assets, "envVars": {}}
result, status = api("POST", f"/projects/{APP_NAME}/deployments", deploy_payload)
print(f"Deploy: HTTP {status}")
if status in (200, 201):
    domains = result.get("domains", [])
    print(f"Live at: https://{domains[0]}" if domains else "Deployed (no domain in response)")
else:
    print(f"Error: {json.dumps(result, indent=2)}")
    sys.exit(1)
