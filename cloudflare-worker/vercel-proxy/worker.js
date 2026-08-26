/**
 * Cloudflare Worker: CORS proxy for ai-gateway.vercel.sh
 * Deploy at: https://screenoperator-vercel-proxy.<your-subdomain>.workers.dev
 *
 * Routes:
 *   POST /v1/chat/completions  -> forwards to ai-gateway.vercel.sh
 *   OPTIONS *                  -> CORS preflight
 *
 * Why this proxy exists:
 *   Android WebViews loaded from local assets send Origin: null on cross-origin
 *   requests. Vercel AI Gateway rejects null origins for security reasons, which
 *   causes the CORS preflight to fail and the WebView sees "Failed to fetch".
 *   This proxy sits in between, strips the null Origin, and forwards the real
 *   Authorization header (the user's Vercel API key) to the gateway unchanged.
 */

const TARGET_BASE = 'https://ai-gateway.vercel.sh';

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

    // Build upstream headers: forward Authorization, drop Origin (null would be rejected by Vercel)
    const upstreamHeaders = new Headers();
    for (const [k, v] of request.headers.entries()) {
      const lower = k.toLowerCase();
      if (lower === 'origin' || lower === 'host' || lower === 'cf-connecting-ip' ||
          lower === 'cf-ipcountry' || lower === 'cf-ray' || lower === 'cf-visitor') continue;
      upstreamHeaders.set(k, v);
    }

    const upstreamRequest = new Request(targetUrl, {
      method: 'POST',
      headers: upstreamHeaders,
      body: request.body,
      duplex: 'half',
    });

    let upstreamResponse;
    try {
      upstreamResponse = await fetch(upstreamRequest);
    } catch (e) {
      return new Response(
        JSON.stringify({ error: { message: 'Proxy could not reach ai-gateway.vercel.sh: ' + e.message, type: 'proxy_error' } }),
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
