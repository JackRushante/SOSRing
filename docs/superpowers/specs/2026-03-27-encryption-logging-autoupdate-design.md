# SOS Ring v1.2 — Encryption, Logging, Auto-Update

**Date:** 2026-03-27
**Scope:** 3 features for internal flavor (encryption applies to both flavors' protocol, but location is internal-only)

---

## 1. E2E Encryption (loc_response)

### Problem
Location coordinates travel in plaintext JSON through ntfy. Anyone who guesses/knows a VIP phone number can compute the topic hash and eavesdrop on coordinates.

### Solution
Encrypt the lat/lon/acc payload in `loc_response` messages using AES-256-GCM. Key derived deterministically from both phone numbers + a hardcoded app secret.

### Design

**New class: `CryptoHelper`** (in `src/main/`)

- `APP_SECRET`: hardcoded random string constant (~32 chars)
- Key derivation: `HKDF-SHA256(sort(normalizedNumA, normalizedNumB) + APP_SECRET)` → 256-bit key
  - `sort()` ensures both parties derive the same key regardless of who sends/receives
  - Uses `javax.crypto` / `SecretKeySpec` — no external dependencies
- `encrypt(plaintext: String, myNumber: String, theirNumber: String) → String`
  - Generate random 12-byte IV
  - AES-GCM encrypt
  - Return Base64(IV + ciphertext + GCM tag)
- `decrypt(ciphertext: String, myNumber: String, theirNumber: String) → String`
  - Base64 decode
  - Extract IV (first 12 bytes), ciphertext+tag (rest)
  - AES-GCM decrypt
  - Return plaintext

**Protocol change:**

Before (loc_response):
```json
{"type": "loc_response", "from": "sosring-abc123", "lat": 45.123, "lon": 9.456, "acc": 12.0, "ts": 1711500000}
```

After (loc_response):
```json
{"type": "loc_response", "from": "sosring-abc123", "enc": "base64-iv-ciphertext-tag", "ts": 1711500000}
```

The `enc` field contains the encrypted JSON: `{"lat": 45.123, "lon": 9.456, "acc": 12.0}`

**Files modified:**
- `NtfyClient.sendLocationResponse()` — encrypt before sending
- `NtfyService.handleLocationResponse()` — decrypt before reading
- New: `CryptoHelper.kt`

**Key derivation detail (HKDF simplified):**
Since we only need one key per pair and Java doesn't have HKDF built-in, use:
`SHA-256(sort(numA, numB) + APP_SECRET)` as the 256-bit AES key directly.
This is sufficient for the threat model (family use, obscurity of topic + app secret).

---

## 2. Location Request Logging

### Problem
Users have no visibility into who requested their location and when.

### Solution
Log every location request (incoming and outgoing) locally, retain 30 days, display in-app.

### Design

**Log entry structure:**
```json
{
  "name": "Lorenzo",
  "number": "+39393207780",
  "timestamp": 1711500000,
  "type": "incoming"
}
```
- `type`: `"incoming"` (someone requested my location) or `"outgoing"` (I requested theirs)

**Storage:** SharedPreferences, key `location_logs`, JSON array. Consistent with existing patterns (contacts, quiet rules).

**PrefsManager additions:**
- `addLocationLog(name: String, number: String, type: String)` — append entry, prune >30 days
- `getLocationLogs(): List<LocationLogEntry>` — return sorted by timestamp desc
- `pruneOldLogs()` — remove entries older than 30 days (called on every add)

**Data class:** `LocationLogEntry(name: String, number: String, timestamp: Long, type: String)`

**Logging points:**
- `NtfyService.handleLocationRequest()` — log incoming request (with sender name from contacts)
- `NtfyService.requestLocation()` — log outgoing request (with target contact name)

**UI:**
- New button "Cronologia" in the Location card
- Opens a MaterialAlertDialog with a scrollable list of log entries
- Each entry: icon (incoming/outgoing arrow), contact name, date/time
- Empty state: "Nessuna richiesta recente"

---

## 3. Auto-Update (internal flavor only)

### Problem
No mechanism to distribute updates to family members. Currently requires manual APK sideload.

### Solution
Check for updates against a version.json hosted on the existing Cloudflare tunnel. Notify user and offer one-tap install.

### Server-side setup

On unraid, serve static files via ntfy container or adjacent path:
```
YOUR_NTFY_SERVER/update/version.json
YOUR_NTFY_SERVER/update/SOSRing-internal-release.apk
```

`version.json`:
```json
{
  "versionCode": 2,
  "versionName": "1.2",
  "apkUrl": "https://YOUR_NTFY_SERVER/update/SOSRing-internal-release.apk"
}
```

### App-side design

**New class: `UpdateChecker`**

- `checkForUpdate(context: Context, onUpdateAvailable: (versionName: String, apkUrl: String) -> Unit)`
  - GET `version.json` via OkHttp
  - Compare `versionCode` with `BuildConfig.VERSION_CODE`
  - If remote > local → invoke callback
- `downloadAndInstall(context: Context, apkUrl: String)`
  - Download APK to `context.cacheDir` via OkHttp
  - Trigger install via `ACTION_VIEW` intent with FileProvider URI
  - Notification with progress (optional: can be simple blocking download with spinner notification)

**FileProvider configuration:**
- `res/xml/file_paths.xml`: expose `cache-path`
- `AndroidManifest.xml`: declare FileProvider with authority `${applicationId}.fileprovider`
- Permission: `REQUEST_INSTALL_PACKAGES` in manifest

**Trigger points:**
1. `MainActivity.onResume()` — check on app open (guarded by `BuildConfig.LOCATION_ENABLED` or new `AUTO_UPDATE` flag)
2. `CallMonitorService` — every 12 hours via `Handler.postDelayed()` loop

**Notification:**
- Channel: reuse `sosring_location` or create `sosring_updates`
- Content: "Aggiornamento disponibile: v1.2"
- Tap action: download and install
- Notification ID: dedicated constant (4)

**Build config:**
- New `BuildConfig` field `AUTO_UPDATE` (true for internal, false for fdroid) — or reuse `LOCATION_ENABLED` since they currently map 1:1
- Update URL base: `BuildConfig.UPDATE_URL` = `"https://YOUR_NTFY_SERVER/update/"` (only in internal flavor)

**Files modified/created:**
- New: `UpdateChecker.kt`
- New: `res/xml/file_paths.xml`
- Modified: `AndroidManifest.xml` (FileProvider, REQUEST_INSTALL_PACKAGES)
- Modified: `build.gradle.kts` (buildConfigField for UPDATE_URL)
- Modified: `CallMonitorService` (12h update check loop)
- Modified: `MainActivity` (check on resume)

---

## Scope exclusions

- No delta/patch updates — full APK replacement
- No forced updates or minimum version enforcement
- No server-side authentication on update endpoint
- No encryption on APK download (HTTPS via Cloudflare is sufficient)
- Logging UI is read-only (no export, no clear button)

## Dependencies

- No new external dependencies. AES-GCM and HKDF use `javax.crypto` (Android built-in). OkHttp already present for ntfy.

## Version bump

- `versionCode` → 2, `versionName` → "1.2" in build.gradle.kts
