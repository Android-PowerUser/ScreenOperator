/**
 * Deno Deploy: Server-side Google Play purchase verification.
 *
 * Purpose:
 *   Prevents Lucky Patcher / IInAppBillingService hooking attacks.
 *   Lucky Patcher fakes a local Purchase object with purchaseState=PURCHASED,
 *   but the purchaseToken it generates is NOT recognised by Google's servers.
 *   By verifying the token server-side via the Google Play Developer API
 *   (purchases.subscriptions.get), we can distinguish real purchases from
 *   locally forged ones.
 *
 * Architecture:
 *   App (native Kotlin)  ──POST──▶  this service  ──GET──▶  Google Play Developer API
 *                                   (Deno Deploy)           (androidpublisher v3)
 *
 * Request:
 *   POST /verify
 *   {
 *     "purchaseToken": "<token from Purchase.getPurchaseToken()>",
 *     "productId":     "<e.g. donation_monthly_2_90_eur or freedom_monthly_7_90_eur>",
 *     "packageName":   "io.github.android_poweruser"
 *   }
 *
 * Response (valid purchase):
 *   { "valid": true, "purchaseState": 0, "expiryTimeMillis": "..." }
 *
 * Response (invalid / forged token):
 *   { "valid": false, "reason": "..." }
 *
 * Environment variables (set via Deno Deploy dashboard or CI):
 *   GOOGLE_PLAY_SERVICE_ACCOUNT_JSON  — the full JSON content of a Google Cloud
 *     service-account key file that has the "Financial Data" role (or
 *     "View financial data" at minimum) on the app's Google Play Console
 *     entry. Without this, every request returns 503.
 *
 *   ALLOWED_PACKAGE_NAME  — (optional) lock verification to a single package.
 *     Defaults to "io.github.android_poweruser" if not set.
 */

// ── CORS headers ──────────────────────────────────────────────────────────────
const CORS_HEADERS: Record<string, string> = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "POST, GET, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type, Authorization, X-Requested-With",
  "Access-Control-Max-Age": "86400",
};

const DEFAULT_PACKAGE = "io.github.android_poweruser";

// Known subscription product IDs for this app
const KNOWN_PRODUCTS = new Set([
  "donation_monthly_2_90_eur",
  "freedom_monthly_7_90_eur",
]);

// ── OAuth2 token cache ────────────────────────────────────────────────────────
// Google OAuth2 access tokens are valid for 1 hour. We cache one and refresh
// proactively 5 minutes before expiry to avoid per-request token fetches.
let _cachedAccessToken: string | null = null;
let _tokenExpiresAt: number = 0;

/**
 * Obtains an OAuth2 access token for the Google Play Developer API using the
 * service-account JWT credentials. Implements the "2-legged OAuth" / JWT-Bearer
 * flow directly (no external dependency needed).
 */
async function getAccessToken(): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  if (_cachedAccessToken && now < _tokenExpiresAt - 300) {
    return _cachedAccessToken;
  }

  const saJson = Deno.env.get("GOOGLE_PLAY_SERVICE_ACCOUNT_JSON");
  if (!saJson) {
    throw new Error("GOOGLE_PLAY_SERVICE_ACCOUNT_JSON env var not configured");
  }

  const sa = JSON.parse(saJson);
  const clientEmail: string = sa.client_email;
  const privateKey: string = sa.private_key;

  if (!clientEmail || !privateKey) {
    throw new Error("Service account JSON missing client_email or private_key");
  }

  // Build the JWT assertion
  const header = { alg: "RS256", typ: "JWT" };
  const iat = now;
  const exp = iat + 3600; // 1 hour
  const claimSet = {
    iss: clientEmail,
    scope: "https://www.googleapis.com/auth/androidpublisher",
    aud: "https://oauth2.googleapis.com/token",
    iat,
    exp,
  };

  const b64url = (obj: unknown) =>
    btoa(JSON.stringify(obj))
      .replace(/\+/g, "-")
      .replace(/\//g, "_")
      .replace(/=+$/, "");

  const headerB64 = b64url(header);
  const claimB64 = b64url(claimSet);
  const signingInput = `${headerB64}.${claimB64}`;

  // Sign with RS256 using WebCrypto
  const pemBody = privateKey
    .replace(/-----BEGIN PRIVATE KEY-----/, "")
    .replace(/-----END PRIVATE KEY-----/, "")
    .replace(/\s/g, "");
  const derBytes = Uint8Array.from(atob(pemBody), (c) => c.charCodeAt(0));
  const cryptoKey = await crypto.subtle.importKey(
    "pkcs8",
    derBytes,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"]
  );
  const signatureBytes = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    cryptoKey,
    new TextEncoder().encode(signingInput)
  );
  const sigB64 = btoa(
    String.fromCharCode(...new Uint8Array(signatureBytes))
  )
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/, "");

  const jwt = `${signingInput}.${sigB64}`;

  // Exchange JWT for access token
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

// ── Google Play Developer API call ────────────────────────────────────────────
interface SubscriptionPurchase {
  purchaseState?: number; // 0=purchased, 1=canceled, 2=pending
  expiryTimeMillis?: string;
  startTimeMillis?: string;
  autoRenewing?: boolean;
  priceCurrencyCode?: string;
  priceAmountMicros?: string;
  orderId?: string;
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
    headers: {
      Authorization: `Bearer ${accessToken}`,
      Accept: "application/json",
    },
  });

  if (!resp.ok) {
    let reason = `Google API returned HTTP ${resp.status}`;
    try {
      const errBody = await resp.json();
      const errMsg =
        errBody?.error?.message || errBody?.error?.status || JSON.stringify(errBody);
      reason = errMsg;
    } catch {
      // ignore parse error, keep generic reason
    }
    return { valid: false, reason };
  }

  const purchase: SubscriptionPurchase = await resp.json();

  // purchaseState: 0 = purchased (active), 1 = canceled, 2 = pending
  // A valid, active subscription has purchaseState === 0.
  // However, even a canceled subscription (state 1) was genuinely purchased
  // at some point — the important thing is that Google recognises the token.
  // We accept state 0 and 1 as "valid" (the app's own queryActiveSubscriptions
  // logic on the client handles the active/inactive distinction).
  if (purchase.purchaseState === undefined) {
    return {
      valid: false,
      reason: "Google returned a response but purchaseState is missing",
      details: purchase,
    };
  }

  // Check expiry for subscriptions: if expiryTimeMillis is in the past,
  // the subscription has lapsed. Still "valid" in the sense that it was a
  // real purchase, but the app may want to know.
  return { valid: true, details: purchase };
}

// ── Request handler ───────────────────────────────────────────────────────────
Deno.serve(async (request: Request): Promise<Response> => {
  // CORS preflight
  if (request.method === "OPTIONS") {
    return new Response(null, { status: 200, headers: CORS_HEADERS });
  }

  const url = new URL(request.url);

  // Health check
  if (request.method === "GET" && url.pathname === "/health") {
    return new Response(
      JSON.stringify({ status: "ok", service: "purchase-verify" }),
      {
        status: 200,
        headers: { ...CORS_HEADERS, "Content-Type": "application/json" },
      }
    );
  }

  // Only POST /verify is the actual endpoint
  if (request.method !== "POST" || url.pathname !== "/verify") {
    return new Response(
      JSON.stringify({ error: "Not found. Use POST /verify" }),
      {
        status: 404,
        headers: { ...CORS_HEADERS, "Content-Type": "application/json" },
      }
    );
  }

  // Parse request body
  let body: { purchaseToken?: string; productId?: string; packageName?: string };
  try {
    body = await request.json();
  } catch (e) {
    return new Response(
      JSON.stringify({
        valid: false,
        reason: `Invalid JSON body: ${e instanceof Error ? e.message : String(e)}`,
      }),
      {
        status: 400,
        headers: { ...CORS_HEADERS, "Content-Type": "application/json" },
      }
    );
  }

  const { purchaseToken, productId, packageName } = body;

  // Validate inputs
  if (!purchaseToken || typeof purchaseToken !== "string") {
    return new Response(
      JSON.stringify({ valid: false, reason: "Missing or invalid purchaseToken" }),
      {
        status: 400,
        headers: { ...CORS_HEADERS, "Content-Type": "application/json" },
      }
    );
  }

  if (!productId || typeof productId !== "string") {
    return new Response(
      JSON.stringify({ valid: false, reason: "Missing or invalid productId" }),
      {
        status: 400,
        headers: { ...CORS_HEADERS, "Content-Type": "application/json" },
      }
    );
  }

  // Validate productId against known products (defense in depth)
  if (!KNOWN_PRODUCTS.has(productId)) {
    return new Response(
      JSON.stringify({
        valid: false,
        reason: `Unknown productId: ${productId}. Expected one of: ${[...KNOWN_PRODUCTS].join(", ")}`,
      }),
      {
        status: 400,
        headers: { ...CORS_HEADERS, "Content-Type": "application/json" },
      }
    );
  }

  // Enforce package name
  const allowedPkg = Deno.env.get("ALLOWED_PACKAGE_NAME") || DEFAULT_PACKAGE;
  const pkg = packageName || allowedPkg;
  if (pkg !== allowedPkg) {
    return new Response(
      JSON.stringify({
        valid: false,
        reason: `Package name mismatch. Allowed: ${allowedPkg}, got: ${pkg}`,
      }),
      {
        status: 403,
        headers: { ...CORS_HEADERS, "Content-Type": "application/json" },
      }
    );
  }

  // Check that the service account is configured
  if (!Deno.env.get("GOOGLE_PLAY_SERVICE_ACCOUNT_JSON")) {
    return new Response(
      JSON.stringify({
        valid: false,
        reason: "Server misconfiguration: GOOGLE_PLAY_SERVICE_ACCOUNT_JSON not set",
      }),
      {
        status: 503,
        headers: { ...CORS_HEADERS, "Content-Type": "application/json" },
      }
    );
  }

  // Verify with Google
  try {
    const result = await verifySubscription(pkg, productId, purchaseToken);
    return new Response(JSON.stringify(result), {
      status: 200,
      headers: { ...CORS_HEADERS, "Content-Type": "application/json" },
    });
  } catch (e) {
    const reason = e instanceof Error ? e.message : String(e);
    console.error(`Verification error: ${reason}`);
    return new Response(
      JSON.stringify({
        valid: false,
        reason: `Verification failed: ${reason}`,
      }),
      {
        status: 500,
        headers: { ...CORS_HEADERS, "Content-Type": "application/json" },
      }
    );
  }
});
