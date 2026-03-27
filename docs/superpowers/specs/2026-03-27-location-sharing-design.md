# Location Sharing — Design Spec

## Summary

Add peer-to-peer location sharing to SOS Ring via ntfy push notifications. Users can request the GPS position of VIP contacts who also have the app installed. Responses are automatic — no user interaction needed on the receiving end. Internal/family feature only, not for public F-Droid release.

## Architecture

Communication happens through a self-hosted ntfy server (unraid container) exposed via Cloudflare Tunnel at a non-guessable subdomain (e.g. `YOUR_NTFY_SERVER`). Each device subscribes to its own unique topic (SHA-256 hash of the user's phone number). Requests go to the recipient's topic, responses return to the sender's topic.

```
Phone A                    ntfy server                     Phone B
  |                   YOUR_NTFY_SERVER                |
  |-- subscribe topic-hash-A -->                              |
  |                              <-- subscribe topic-hash-B --|
  |                                                           |
  |-- POST topic-hash-B ------->                              |
  |   {loc_request, from:hash-A}  -- push notification ------>|
  |                                                    [GPS start]
  |                              <-- POST topic-hash-A -------|
  |                              |   {loc_pending}            |
  |<-- push notification --------|                            |
  |  "Ottenendo posizione..."                          [fix OK]
  |                              <-- POST topic-hash-A -------|
  |<-- push notification --------|   {loc_response, lat, lon} |
  |  "Moglie (+-12m)" [Maps]                           [GPS stop]
```

## Discovery Protocol

Automatic detection of which VIP contacts also have the app:

1. When the app opens, send `discovery` ping to each VIP contact's topic
2. Receiving app checks if sender's number is in its own VIP list
3. If yes, responds with `discovery_ack`
4. Sender marks that contact as `locationEnabled = true` — GPS icon appears
5. If no response within 10 seconds — no icon
6. Discovery runs every time the app is opened to refresh status

This is consensual: both parties must have each other as VIP contacts.

## Message Protocol

All messages are JSON in the ntfy message body:

```json
// Discovery ping
{"type": "discovery", "from": "a1b2c3...hash"}

// Discovery ack
{"type": "discovery_ack", "from": "d4e5f6...hash"}

// Location request
{"type": "loc_request", "from": "a1b2c3...hash", "ts": 1711450000}

// Location pending (GPS warming up)
{"type": "loc_pending", "from": "d4e5f6...hash", "ts": 1711450002}

// Location response
{"type": "loc_response", "from": "d4e5f6...hash", "lat": 40.123, "lon": 18.456, "acc": 12.5, "ts": 1711450005}
```

Fields:
- `from`: SHA-256 hash of sender's phone number (used to route responses)
- `ts`: Unix timestamp (to discard stale messages)
- `lat`, `lon`: GPS coordinates
- `acc`: GPS accuracy in meters

No personal data in messages — only hashes and coordinates.

## GPS Fix Strategy

To avoid sending imprecise first-fix positions:

1. On receiving `loc_request`, immediately send `loc_pending` back
2. Start GPS updates with `PRIORITY_HIGH_ACCURACY`
3. Each update: check if `accuracy < 30m` — if yes, send `loc_response` and stop GPS
4. Timeout after 15 seconds — send best fix obtained so far
5. GPS turns off immediately after sending response

Battery impact: max 15 seconds of GPS per request. Negligible.

## Data Model

### Modified VipContact

```kotlin
data class VipContact(
    val name: String,
    val number: String,
    val locationEnabled: Boolean = false
)
```

`locationEnabled` is transient — recalculated via discovery on each app open. Not persisted (avoids stale state).

### New preferences

| Key | Type | Description |
|-----|------|-------------|
| `ntfy_server_url` | String | `https://YOUR_NTFY_SERVER` |
| `own_phone_number` | String | User's own number with international prefix (e.g. `+39393207780`) |
| `own_topic_hash` | String | SHA-256 of normalized own number, computed once on save |

The user enters their own number once in a settings section. The app normalizes it (remove spaces/dashes, ensure `+` prefix) and computes the hash.

## Topic Hash Generation

```
normalizeNumber("+39 333 123 4567") → "+393331234567"
SHA-256("+393331234567") → "sosring-a1b2c3d4e5f6..."
```

Prefix `sosring-` to avoid topic collisions on the ntfy server. The hash is deterministic: same number on any phone produces the same topic.

## Components

| Component | Type | Responsibility |
|-----------|------|---------------|
| `NtfyService` | New class | Subscribe to own topic via SSE. Receive and dispatch messages (discovery, loc_request, loc_response). Send automatic responses. |
| `LocationHelper` | New class | Wrapper for FusedLocationProviderClient. Single on-demand high-accuracy fix with 30m threshold and 15s timeout. |
| `NtfyClient` | New class | HTTP POST to ntfy server. Methods: sendDiscovery(), sendLocationRequest(), sendLocationResponse(), sendLocationPending(). |
| `PrefsManager` | Modified | + locationEnabled on VipContact, + own number/hash, + ntfy URL |
| `MainActivity` | Modified | + "Your number" settings field, + GPS icon on locationEnabled contacts, + tap handler for location request, + discovery on resume |
| `CallMonitorService` | Modified | Start/stop NtfyService alongside phone monitoring |

## UI Changes

### VIP Contact item — GPS icon

Add a GPS/location icon button between the edit and delete buttons. Only visible when `locationEnabled = true`.

```
┌─────────────────────────────────────────┐
│  Moglie                                 │
│  +39333456789         [📍] [✏️] [🗑️]  │
└─────────────────────────────────────────┘
```

Tap on GPS icon → sends location request → notification flow begins.

### Settings section — Your number

New card above or below Quiet Hours:

```
┌─────────────────────────────────────────┐
│  Location Sharing                       │
│  Your number: [+39393207780]     [Save] │
│  Server: YOUR_NTFY_SERVER      │
└─────────────────────────────────────────┘
```

Number field with international prefix. Server URL shown but not editable (hardcoded for now). Once number is saved, the service starts subscribing to ntfy.

## Notification Flow (requester side)

Three states, same notification ID (updates in place):

1. **Immediate** (local): "Location request sent to Moglie"
2. **On `loc_pending`**: "Getting position of Moglie..."
3. **On `loc_response`**: "Position of Moglie (+-12m)" — tap opens Google Maps at `geo:40.123,18.456`
4. **Timeout 30s**: "No response from Moglie"

## Permissions

New permissions required:
- `android.permission.INTERNET` — for ntfy communication
- `android.permission.ACCESS_FINE_LOCATION` — precise GPS
- `android.permission.ACCESS_BACKGROUND_LOCATION` — respond to requests when app is closed
- `com.google.android.gms.permission.ACCESS_FINE_LOCATION` — for FusedLocationProviderClient

New dependency:
- `com.google.android.gms:play-services-location` — for FusedLocationProviderClient

Note: This dependency means the location feature is NOT FOSS-compatible. This is acceptable because this feature is internal-only and excluded from the F-Droid build flavor.

## Build Flavors

```kotlin
productFlavors {
    create("fdroid") {
        // No location sharing, no Google Play Services
        // This is the public F-Droid build
    }
    create("internal") {
        // Location sharing enabled
        // Includes Google Play Services location
    }
}
```

Location-related code lives in `src/internal/` source set. The `fdroid` flavor compiles without it.

## ntfy Server Setup (out of scope for app implementation)

Documented here for reference:
- Docker container on unraid: `binwiederhier/ntfy`
- Cloudflare Tunnel to `YOUR_NTFY_SERVER`
- No authentication required (topics are SHA-256 hashes, obscure by design)

## Localization

New strings needed (en + it):
- Location sharing card title/subtitle
- "Your number" label and placeholder
- GPS icon content description
- Notification texts for all 4 states
- Discovery status messages

## Out of Scope

- End-to-end encryption of coordinates (topics are already obscure hashes)
- Location history or tracking
- Real-time continuous location sharing
- Map view inside the app
- Sharing location with non-VIP contacts
- Editable ntfy server URL in UI
- ntfy server setup/deployment (separate task)
