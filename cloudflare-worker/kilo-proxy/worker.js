/**
 * Cloudflare Worker: CORS proxy for api.kilo.ai
 * Deploy at: https://kilo-proxy.<your-subdomain>.workers.dev
 *
 * Routes:
 *   POST /api/gateway/chat/completions  → forwards to api.kilo.ai
 *   OPTIONS *                           → CORS preflight
 */

const TARGET_BASE = 'https://api.kilo.ai';

const CORS_HEADERS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
  'Access-Control-Allow-Headers': 'Content-Type, Authorization',
  'Access-Control-Max-Age': '86400',
};

export default {
  async fetch(request) {
    // Handle CORS preflight
    if (request.method === 'OPTIONS') {
      return new Response(null, { status: 204, headers: CORS_HEADERS });
    }

    // Only allow POST
    if (request.method !== 'POST') {
      return new Response('Method not allowed', { status: 405, headers: CORS_HEADERS });
    }

    const url = new URL(request.url);
    const targetUrl = TARGET_BASE + url.pathname + url.search;

    // Forward the request to kilo.ai
    const upstreamRequest = new Request(targetUrl, {
      method: 'POST',
      headers: request.headers,
      body: request.body,
      // duplex needed for streaming bodies
      duplex: 'half',
    });

    let upstreamResponse;
    try {
      upstreamResponse = await fetch(upstreamRequest);
    } catch (e) {
      return new Response(
        JSON.stringify({ error: { message: 'Proxy could not reach api.kilo.ai: ' + e.message, type: 'proxy_error' } }),
        { status: 502, headers: { ...CORS_HEADERS, 'Content-Type': 'application/json' } }
      );
    }

    // Stream the upstream response back, adding CORS headers
    const responseHeaders = new Headers(upstreamResponse.headers);
    for (const [k, v] of Object.entries(CORS_HEADERS)) {
      responseHeaders.set(k, v);
    }

    return new Response(upstreamResponse.body, {
      status: upstreamResponse.status,
      statusText: upstreamResponse.statusText,
      headers: responseHeaders,
    });
  },
};
