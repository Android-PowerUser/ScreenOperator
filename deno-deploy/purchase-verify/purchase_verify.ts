/**
 * Deno Deploy: Server-side Google Play purchase verification.
 *
 * Reads the service account credentials from either:
 *  1. GOOGLE_PLAY_SERVICE_ACCOUNT_JSON env var (if set)
 *  2. A bundled service-account.json file (written by CI during deploy)
 */

const CORS_HEADERS: Record<string, string> = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "POST, GET, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type, Authorization, X-Requested-With",
  "Access-Control-Max-Age": "86400",
};

const DEFAULT_PACKAGE = "io.github.android_poweruser";

const KNOWN_PRODUCTS = new Set([
  "donation_monthly_2_90_eur",
  "freedom_monthly_7_90_eur",
]);

// ── Service account credentials (loaded once at startup) ──────────────────────
let _clientEmail: string | null = null;
let _privateKey: string | null = null;

async function loadServiceAccount(): Promise<{ clientEmail: string; privateKey: string }> {
  if (_clientEmail && _privateKey) return { clientEmail: _clientEmail, privateKey: _privateKey };

  let saJson: string | null = null;

  // Strategy 1: env var
  saJson = Deno.env.get("GOOGLE_PLAY_SERVICE_ACCOUNT_JSON") || null;

  // Strategy 2: bundled file (written by CI)
  if (!saJson) {
    try {
      const url = new URL("./service-account.json", import.meta.url);
      saJson = await Deno.readTextFile(url);
    } catch {
      // not found
    }
  }

  if (!saJson) {
    throw new Error("No service account credentials found (neither env var nor service-account.json)");
  }

  const sa = JSON.parse(saJson);
  if (!sa.client_email || !sa.private_key) {
    throw new Error("Service account JSON missing client_email or private_key");
  }

  _clientEmail = sa.client_email;
  _privateKey = sa.private_key;
  console.log(`Service account loaded: ${_clientEmail}`);
  return { clientEmail: _clientEmail!, privateKey: _privateKey! };
}

// ── OAuth2 token cache ────────────────────────────────────────────────────────
let _cachedAccessToken: string | null = null;
let _tokenExpiresAt: number = 0;

async function getAccessToken(): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  if (_cachedAccessToken && now < _tokenExpiresAt - 300) {
    return _cachedAccessToken;
  }

  const { clientEmail, privateKey } = await loadServiceAccount();

  const header = { alg: "RS256", typ: "JWT" };
  const iat = now;
  const exp = iat + 3600;
  const claimSet = {
    iss: clientEmail,
    scope: "https://www.googleapis.com/auth/androidpublisher",
    aud: "https://oauth2.googleapis.com/token",
    iat,
    exp,
  };

  const b64url = (obj: unknown) =>
    btoa(JSON.stringify(obj)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");

  const signingInput = `${b64url(header)}.${b64url(claimSet)}`;

  const pemBody = privateKey.replace(/-----BEGIN PRIVATE KEY-----/, "").replace(/-----END PRIVATE KEY-----/, "").replace(/\s/g, "");
  const derBytes = Uint8Array.from(atob(pemBody), (c) => c.charCodeAt(0));
  const cryptoKey = await crypto.subtle.importKey("pkcs8", derBytes, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"]);
  const signatureBytes = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", cryptoKey, new TextEncoder().encode(signingInput));
  const sigB64 = btoa(String.fromCharCode(...new Uint8Array(signatureBytes))).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");

  const jwt = `${signingInput}.${sigB64}`;

  const tokenResp = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: `grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion=${jwt}`,
  });

  if (!tokenResp.ok) {
    const errText = await tokenResp.text();
    throw new Error(`Token exchange failed (${tokenResp.status}): ${errText}`);
  }

  const tokenData = await tokenResp.json();
  _cachedAccessToken = tokenData.access_token;
  _tokenExpiresAt = now + (tokenData.expires_in || 3600);
  return _cachedAccessToken!;
}

// ── Google Play Developer API ─────────────────────────────────────────────────
interface SubscriptionPurchase {
  purchaseState?: number;
  expiryTimeMillis?: string;
  startTimeMillis?: string;
  autoRenewing?: boolean;
  [key: string]: unknown;
}

async function verifySubscription(
  packageName: string,
  productId: string,
  purchaseToken: string
): Promise<{ valid: boolean; reason?: string; details?: SubscriptionPurchase }> {
  const accessToken = await getAccessToken();

  const url =
    `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/` +
    `${encodeURIComponent(packageName)}/purchases/subscriptions/` +
    `${encodeURIComponent(productId)}/tokens/${encodeURIComponent(purchaseToken)}`;

  const resp = await fetch(url, {
    method: "GET",
    headers: { Authorization: `Bearer ${accessToken}`, Accept: "application/json" },
  });

  if (!resp.ok) {
    let reason = `Google API returned HTTP ${resp.status}`;
    let errorStatus = "";
    try {
      const errBody = await resp.json();
      errorStatus = errBody?.error?.status || "";
      reason = `[HTTP ${resp.status}] [${errorStatus}] ${errBody?.error?.message || JSON.stringify(errBody)}`;
    } catch { /* keep generic */ }
    // HTTP 404 with "purchaseTokenNotFound" means the token doesn't exist at Google
    // → this is a forged/invalid token
    if (resp.status === 404 || errorStatus === "NOT_FOUND") {
      return { valid: false, reason: "Invalid/forged purchase token (Google does not recognise it)" };
    }
    return { valid: false, reason };
  }

  const purchase: SubscriptionPurchase = await resp.json();

  // The v3 subscriptions API does NOT return a `purchaseState` field.
  // Instead, it returns: paymentState, expiryTimeMillis, startTimeMillis,
  // autoRenewing, orderId, acknowledgementState, etc.
  // A successful HTTP 200 with an orderId is sufficient proof that Google
  // recognises this purchase token as genuine.
  if (purchase.orderId || purchase.startTimeMillis || purchase.paymentState !== undefined) {
    return { valid: true, details: purchase };
  }

  // If we got a 200 but no recognizable subscription fields, something is off.
  return { valid: false, reason: "Google returned 200 but response has no recognisable subscription fields", details: purchase };
}

// ── Check if service account is available ─────────────────────────────────────
async function hasServiceAccount(): Promise<boolean> {
  try {
    await loadServiceAccount();
    return true;
  } catch {
    return false;
  }
}

// ── Request handler ───────────────────────────────────────────────────────────
Deno.serve(async (request: Request): Promise<Response> => {
  if (request.method === "OPTIONS") {
    return new Response(null, { status: 200, headers: CORS_HEADERS });
  }

  const url = new URL(request.url);

  if (request.method === "GET" && url.pathname === "/health") {
    const hasSa = await hasServiceAccount();
    return new Response(
      JSON.stringify({ status: "ok", service: "purchase-verify", serviceAccountConfigured: hasSa }),
      { status: 200, headers: { ...CORS_HEADERS, "Content-Type": "application/json" } }
    );
  }

  if (request.method !== "POST" || url.pathname !== "/verify") {
    return new Response(JSON.stringify({ error: "Not found. Use POST /verify" }), {
      status: 404,
      headers: { ...CORS_HEADERS, "Content-Type": "application/json" },
    });
  }

  let body: { purchaseToken?: string; productId?: string; packageName?: string };
  try {
    body = await request.json();
  } catch (e) {
    return new Response(JSON.stringify({ valid: false, reason: `Invalid JSON: ${e instanceof Error ? e.message : String(e)}` }), {
      status: 400, headers: { ...CORS_HEADERS, "Content-Type": "application/json" },
    });
  }

  const { purchaseToken, productId, packageName } = body;

  if (!purchaseToken || typeof purchaseToken !== "string") {
    return new Response(JSON.stringify({ valid: false, reason: "Missing or invalid purchaseToken" }), {
      status: 400, headers: { ...CORS_HEADERS, "Content-Type": "application/json" },
    });
  }
  if (!productId || typeof productId !== "string") {
    return new Response(JSON.stringify({ valid: false, reason: "Missing or invalid productId" }), {
      status: 400, headers: { ...CORS_HEADERS, "Content-Type": "application/json" },
    });
  }
  if (!KNOWN_PRODUCTS.has(productId)) {
    return new Response(JSON.stringify({ valid: false, reason: `Unknown productId: ${productId}` }), {
      status: 400, headers: { ...CORS_HEADERS, "Content-Type": "application/json" },
    });
  }

  const allowedPkg = Deno.env.get("ALLOWED_PACKAGE_NAME") || DEFAULT_PACKAGE;
  const pkg = packageName || allowedPkg;
  if (pkg !== allowedPkg) {
    return new Response(JSON.stringify({ valid: false, reason: `Package name mismatch` }), {
      status: 403, headers: { ...CORS_HEADERS, "Content-Type": "application/json" },
    });
  }

  if (!(await hasServiceAccount())) {
    return new Response(JSON.stringify({ valid: false, reason: "Server misconfiguration: service account not configured" }), {
      status: 503, headers: { ...CORS_HEADERS, "Content-Type": "application/json" },
    });
  }

  try {
    const result = await verifySubscription(pkg, productId, purchaseToken);
    return new Response(JSON.stringify(result), {
      status: 200, headers: { ...CORS_HEADERS, "Content-Type": "application/json" },
    });
  } catch (e) {
    const reason = e instanceof Error ? e.message : String(e);
    console.error(`Verification error: ${reason}`);
    return new Response(JSON.stringify({ valid: false, reason: `Verification failed: ${reason}` }), {
      status: 500, headers: { ...CORS_HEADERS, "Content-Type": "application/json" },
    });
  }
});
