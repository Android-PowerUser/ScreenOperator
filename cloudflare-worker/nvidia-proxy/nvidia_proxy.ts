/**
 * Deno Deploy: CORS proxy for integrate.api.nvidia.com
 * Forwards POST /v1/chat/completions to NVIDIA NIM API
 * and adds Access-Control-Allow-Origin: * so Android WebViews can reach it.
 */

const TARGET_BASE = "https://integrate.api.nvidia.com";

const CORS_HEADERS: Record<string, string> = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "POST, GET, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type, Authorization, X-Requested-With",
  "Access-Control-Expose-Headers": "Content-Type, X-Request-Id",
  "Access-Control-Max-Age": "86400",
};

Deno.serve(async (request: Request): Promise<Response> => {
  // CORS preflight – respond with 200 (not 204, some WebViews reject 204)
  if (request.method === "OPTIONS") {
    return new Response(null, { status: 200, headers: CORS_HEADERS });
  }

  if (request.method !== "POST") {
    return new Response("Method not allowed", { status: 405, headers: CORS_HEADERS });
  }

  const url = new URL(request.url);
  const targetUrl = TARGET_BASE + url.pathname + url.search;

  // Forward headers (pass Authorization through)
  const forwardHeaders = new Headers();
  forwardHeaders.set("Content-Type", "application/json");
  forwardHeaders.set("Accept", "text/event-stream, application/json");
  const auth = request.headers.get("Authorization");
  if (auth) forwardHeaders.set("Authorization", auth);

  let body: string;
  try {
    body = await request.text();
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e);
    return new Response(
      JSON.stringify({ error: { message: "Failed to read request body: " + msg, type: "proxy_error" } }),
      { status: 400, headers: { ...CORS_HEADERS, "Content-Type": "application/json" } }
    );
  }

  let upstreamResponse: Response;
  try {
    upstreamResponse = await fetch(targetUrl, {
      method: "POST",
      headers: forwardHeaders,
      body,
    });
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e);
    return new Response(
      JSON.stringify({ error: { message: "Proxy could not reach integrate.api.nvidia.com: " + msg, type: "proxy_error" } }),
      { status: 502, headers: { ...CORS_HEADERS, "Content-Type": "application/json" } }
    );
  }

  // Build response headers: copy safe upstream headers, then overlay CORS
  const responseHeaders = new Headers();
  for (const [k, v] of upstreamResponse.headers.entries()) {
    const kl = k.toLowerCase();
    if (kl === "content-type" || kl === "x-request-id" || kl === "transfer-encoding") {
      responseHeaders.set(k, v);
    }
  }
  for (const [k, v] of Object.entries(CORS_HEADERS)) {
    responseHeaders.set(k, v);
  }

  return new Response(upstreamResponse.body, {
    status: upstreamResponse.status,
    headers: responseHeaders,
  });
});
