/**
 * Deno Deploy: CORS proxy for Firebase Anonymous Auth
 * Forwards POST /accounts:signInAnonymously to identitytoolkit.googleapis.com
 * and adds Access-Control-Allow-Origin: * so Android WebViews can reach it.
 */

const TARGET_BASE = "https://identitytoolkit.googleapis.com";

const CORS_HEADERS: Record<string, string> = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type",
  "Access-Control-Max-Age": "86400",
};

Deno.serve(async (request: Request): Promise<Response> => {
  if (request.method === "OPTIONS") {
    return new Response(null, { status: 200, headers: CORS_HEADERS });
  }

  if (request.method !== "POST") {
    return new Response("Method not allowed", { status: 405, headers: CORS_HEADERS });
  }

  const url = new URL(request.url);
  const targetUrl = TARGET_BASE + url.pathname + url.search;

  let body: string;
  try {
    body = await request.text();
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e);
    return new Response(
      JSON.stringify({ error: { message: "Failed to read request body: " + msg } }),
      { status: 400, headers: { ...CORS_HEADERS, "Content-Type": "application/json" } }
    );
  }

  let upstreamResponse: Response;
  try {
    upstreamResponse = await fetch(targetUrl, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body,
    });
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e);
    return new Response(
      JSON.stringify({ error: { message: "Proxy could not reach identitytoolkit: " + msg } }),
      { status: 502, headers: { ...CORS_HEADERS, "Content-Type": "application/json" } }
    );
  }

  const responseHeaders = new Headers({ "Content-Type": "application/json" });
  for (const [k, v] of Object.entries(CORS_HEADERS)) {
    responseHeaders.set(k, v);
  }

  return new Response(upstreamResponse.body, {
    status: upstreamResponse.status,
    headers: responseHeaders,
  });
});
