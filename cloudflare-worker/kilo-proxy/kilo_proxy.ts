/**
 * Deno Deploy: CORS proxy for api.kilo.ai
 * Forwards POST /api/gateway/chat/completions to api.kilo.ai
 * and adds Access-Control-Allow-Origin: * so Android WebViews can reach it.
 */

const TARGET_BASE = "https://api.kilo.ai";

const CORS_HEADERS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type, Authorization",
  "Access-Control-Max-Age": "86400",
};

Deno.serve(async (request: Request): Promise<Response> => {
  // CORS preflight
  if (request.method === "OPTIONS") {
    return new Response(null, { status: 204, headers: CORS_HEADERS });
  }

  if (request.method !== "POST") {
    return new Response("Method not allowed", { status: 405, headers: CORS_HEADERS });
  }

  const url = new URL(request.url);
  const targetUrl = TARGET_BASE + url.pathname + url.search;

  // Forward headers (pass Authorization through if present)
  const forwardHeaders = new Headers();
  forwardHeaders.set("Content-Type", "application/json");
  const auth = request.headers.get("Authorization");
  if (auth) forwardHeaders.set("Authorization", auth);

  let upstreamResponse: Response;
  try {
    upstreamResponse = await fetch(targetUrl, {
      method: "POST",
      headers: forwardHeaders,
      body: request.body,
    });
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e);
    return new Response(
      JSON.stringify({ error: { message: "Proxy could not reach api.kilo.ai: " + msg, type: "proxy_error" } }),
      { status: 502, headers: { ...CORS_HEADERS, "Content-Type": "application/json" } }
    );
  }

  // Pass upstream headers through, override CORS ones
  const responseHeaders = new Headers(upstreamResponse.headers);
  for (const [k, v] of Object.entries(CORS_HEADERS)) {
    responseHeaders.set(k, v);
  }

  return new Response(upstreamResponse.body, {
    status: upstreamResponse.status,
    statusText: upstreamResponse.statusText,
    headers: responseHeaders,
  });
});
