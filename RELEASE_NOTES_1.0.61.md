# Ondo 1.0.61 (961)

This update explains how to restore an integration after upgrading from a build
that bundled shared service keys. It includes all fixes from 1.0.60.

- Missing API keys now show the setup steps before making a network request.
- HTTP 401/403 errors explain whether to check a service key or gateway setup.
  Downloads provides an Open Integrations button alongside source errors.
- Integrations explains that music-server login needs a compatible gateway.
  The bundled DeckNAS service URLs currently require their own API keys.
- Pasted API keys tolerate surrounding whitespace. Gateway authentication sends
  only the three login-proof headers and never falls back to a saved service key.
- If secure key storage fails, saving reports the error and keeps the previous
  settings instead of silently replacing the key with an empty value.

## Setup after upgrading

In Integrations, select the affected service, leave **Sign in with my music-server
login (gateway)** off for the bundled DeckNAS URLs, enter the API key obtained
from the service administrator, then choose **Save** and **Test connection**.
Keys are stored encrypted on each device and are not included in the APK.
YouTube and Squid use the fallback service's key; each has its own saved setting.

## Verification

- Android host tests: 79 tests in 29 suites passed, including missing keys,
  service-specific headers, pasted whitespace, optional AudioMuse authentication,
  incomplete gateway login and credential-safe rejection messages.
- Release lint: zero errors, two existing warnings. Signed release assembly passed.
- APK: `com.nickshortnct.ondo`, version `1.0.61` / `961`, ARM64 and ARMv7.
  Signing certificate matches the installed 1.0.59 and published 1.0.60 builds.
  No plaintext or base64 matches for the three configured integration keys.
- API 35 emulator: upgrade retained the fixture library.
  A blank YouTube key showed setup instructions; an incorrect key showed the new
  HTTP 401 instructions. Saving the correct key produced encrypted preferences
  with no plaintext key, and the live service returned Connection verified.
  The final APK verified the live connection again after reopening the app.
  Selecting gateway authentication showed the compatible-gateway explanation.
  The original fake service configuration was restored after testing.
- Live read-only probes: YouTube status, tasks and Squid tasks returned HTTP 200
  with the server key. Missing credentials and unsupported gateway headers were
  rejected with HTTP 401. SoulSync status, downloads and wishlist returned HTTP 200.
- No production acquisition request was submitted by these checks.

APK SHA-256: `3273d305b104c5ef4f6966b29d679a4ec834607bd7d95a60dc365955fae263a9`.

## Publication

Published as [v1.0.61+961](https://github.com/nickshortnct-create/ondo-apk/releases/tag/v1.0.61%2B961).
The unauthenticated updater feed selects this release, and a fresh public APK
download matches the signed build's SHA-256. The sole release asset is `Ondo.apk`.
The DeckNAS dashboard APK link was also updated atomically to the same build.
