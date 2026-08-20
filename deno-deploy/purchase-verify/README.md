# Purchase Verification Service

Server-side Google Play purchase verification for Screen Operator.

## Architecture

```
App (Kotlin)                Deno Deploy                  Google Play
handlePurchase() ──POST──▶ /verify ──────GET──────────▶ Developer API
purchaseToken              (JWT auth)                   purchases.subscriptions.get
                                                         │
                           ◀── {valid:true/false} ◀─────┘
```

## Why

Lucky Patcher and similar tools hook `IInAppBillingService` / `BillingClient`
at the Zygisk level and return forged `Purchase` objects with
`purchaseState=PURCHASED`. The `purchaseToken` in these forged objects is
**not recognised by Google's servers**. By verifying the token server-side
via the Play Developer API, we can distinguish real purchases from locally
forged ones.

## Setup

### 1. Google Cloud Console Setup

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Select your project (the one linked to your Google Play Console)
3. **APIs & Services → Library** → search for **"Google Play Android Developer API"** → **Enable**
4. Navigate to **IAM & Admin → Service Accounts**
5. Click **Create Service Account**
   - Name: `play-purchase-verifier`
   - Role: **Basic → Viewer** (the real permissions are granted in Play Console, not here)
6. After creation, click the service account → **Keys** → **Add Key → Create new key → JSON**
7. Download the JSON file — this is your service account key
8. Copy the service account email address (looks like `play-purchase-verifier@your-project.iam.gserviceaccount.com`)

### 2. Google Play Console — Grant Permissions

The "View financial data" permission does NOT exist in Google Cloud IAM — it is granted
exclusively through the Google Play Console's user management:

1. Go to [Google Play Console](https://play.google.com/console/)
2. Navigate to **Users and permissions** (left sidebar)
3. Click **Invite new users**
4. Paste the service account's email address (from step 1.8)
5. Under **App permissions** → select your app → **Apply**
6. Under **Account permissions**, tick these three checkboxes:
   - ✅ View app information and download bulk reports (read-only)
   - ✅ View financial data, orders, and cancellation survey responses
   - ✅ Manage orders and subscriptions
7. Click **Invite user**

> ⚠️ It can take up to 24 hours for Google to propagate new service-account
> permissions to the Play Developer API. If you get 403 errors initially,
> wait a few hours and retry.

### 3. Configure Deno Deploy Secrets

In your Deno Deploy dashboard (or via CI secret):

- **Secret name:** `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON`
- **Secret value:** The full JSON content of the service account key file (copy-paste the entire JSON)

The GitHub Actions workflow (`.github/workflows/deploy-purchase-verify.yml`)
will automatically set this as an environment variable on the Deno Deploy
project if the `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` GitHub secret is configured.

### 4. Deploy

```bash
# Via GitHub Actions (recommended):
# Push changes to main or manually trigger the workflow

# Via Deno CLI:
cd deno-deploy/purchase-verify
deno deploy --org=android-poweruser --app=screenoperator-purchase-verify --prod .
```

## API

### `POST /verify`

**Request:**
```json
{
  "purchaseToken": "abcdef...",
  "productId": "donation_monthly_2_90_eur",
  "packageName": "io.github.android_poweruser"
}
```

**Response (valid):**
```json
{
  "valid": true,
  "details": {
    "purchaseState": 0,
    "expiryTimeMillis": "1735689600000",
    "autoRenewing": true
  }
}
```

**Response (invalid/forged):**
```json
{
  "valid": false,
  "reason": "The purchase token is invalid or expired."
}
```

### `GET /health`

Returns `{"status": "ok", "service": "purchase-verify"}` — used by CI to
verify the deployment is alive.

## Client Integration

The native `MainActivity.handlePurchase()` now calls
`PurchaseVerifier.verifyPurchase()` before calling
`TrialManager.markAsPurchased()`. If verification fails, the purchase is
silently rejected (with a Toast to the user) and no local purchase flag is
set.

## Security Notes

- The service account JSON is stored as a Deno Deploy environment variable
  (encrypted at rest, never exposed to the client).
- The `ALLOWED_PACKAGE_NAME` env var locks verification to your app's
  package name (default: `io.github.android_poweruser`).
- Only known product IDs (`donation_monthly_2_90_eur`, `freedom_monthly_7_90_eur`)
  are accepted.
- CORS is set to `*` since the app calls this from a WebView.
