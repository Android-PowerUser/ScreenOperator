# Trial/donation UI overrides (remote-updatable dialog text)

This controls what the trial and donation dialogs **say** - titles, body copy, button labels.
It deliberately does **not** control trial length, when a dialog is shown, or anything in
`TrialManager.kt`/Play Billing entitlement checks. That stays native-only on purpose: see the
"Why billing logic isn't on this list" note at the end.

`trial-ui-overrides.json` (this file, fetched by the WebView relative to `index.html`, same
pattern as `command-patterns.json`) - missing/invalid/empty means the original built-in
English text, unchanged.

## Format

Every field is optional; omitted fields keep their current value (the built-in default, or
whatever a previous override already set):

```json
{
  "firstLaunchDialogTitle": "Trial Information",
  "firstLaunchDialogBody": "You can try Screen Operator for 7 days before you have to subscribe to support the development of more features.",
  "firstLaunchDialogButton": "OK",

  "trialExpiredDialogTitle": "Trial period expired",
  "trialExpiredDialogBody": "Please support the development of the app so that you can continue using it \ud83c\udf89",
  "trialExpiredDialogSubscribeButton": "Subscribe",

  "paymentMethodDialogTitle": "Choose Payment Method",
  "paymentMethodPayPalButtonLabel": "PayPal (2,90 €/Month)",
  "paymentMethodGooglePlayButtonLabel": "Google Play (2,90 €/Month)",
  "paymentMethodCancelButtonLabel": "Cancel",

  "infoDialogTitle": "Information",
  "expiredStateInfoMessage": null
}
```

- `expiredStateInfoMessage` is a separate copy of the "trial expired" text used internally by
  `TrialStateUiModelResolver` (kept in sync with `trialExpiredDialogBody` automatically - leave
  it unset/`null` unless you specifically want these two to say different things).
- The 7-days / 2,90 € numbers in the default English text are just copy - changing them here
  changes what users *read*, not how long the trial actually runs or what Play Billing
  actually charges (that's still controlled by `TrialManager.TRIAL_DURATION_MS` and your Play
  Console subscription price respectively). Keep the wording consistent with the real values if
  you change one without the other.

## How it gets applied

1. `window.onAndroidReady()` in `index.html` fetches `trial-ui-overrides.json` and passes it to
   `Android.setTrialUiOverrides(json)`.
2. `TrialUiConfig` installs the parsed policy; the bridge persists the raw JSON via
   `TrialUiOverridesPreferences` so it's restored on the next app start.
3. `FirstLaunchInfoDialog`, `TrialExpiredDialog`, `PaymentMethodDialog`, and the generic
   `InfoDialog`'s title, plus `TrialStateUiModelResolver`'s expired-state message, all read
   from `TrialUiConfig.current()` instead of a hardcoded string.

## Why billing/entitlement logic isn't on this list

`TrialManager.kt` decides whether the trial is active/expired and whether a purchase is valid,
checked against real, Play-Billing-verified purchase state. That stays native-only:

- The override JSON is public and unauthenticated (anyone can read it, and - since this repo
  is open source - anyone can read exactly how the bridge method works). Wiring entitlement
  state to it would mean "am I a paying customer" is a publicly-documented, unverified client
  flag instead of a real purchase record.
- Apps that let paid features be unlocked through a mechanism other than Google Play's billing
  system risk violating Play's payments policy, independent of who controls the flag.

Text and copy carry none of that risk - changing what a dialog says doesn't change whether the
app is usable, so it's fully on the table.
