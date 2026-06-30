# SOS Ring

**Force the ringtone for VIP contacts even in Silent / Vibrate / Do Not Disturb mode.**

SOS Ring is a FOSS Android app that overrides Silent, Vibrate, and Do Not Disturb when you receive a call from a whitelisted VIP contact. After the call ends, your phone is restored to its exact previous state.

Available on F-Droid.

## Why?

Android 14+ removed per-contact DND exceptions, and there is no built-in way to let specific contacts bypass silent mode. Google restricts `READ_CALL_LOG` on the Play Store, so this kind of app cannot be published there.

SOS Ring solves this with a foreground service that monitors incoming calls and, when a VIP contact calls, plays the ringtone via the ALARM audio stream (the one stream that bypasses DND on every device).

## Features

- Pick VIP contacts from the phonebook or enter them manually
- Configurable override volume (25-100%)
- Works in **Silent**, **Vibrate**, AND **Do Not Disturb** mode
- Full state restore after the call (ringer mode, all volumes including alarm, DND)
- Temporary mute timer (1-12 hours) without turning the service off
- Survives reboots (auto-start)
- Minimal battery usage (event-driven, no polling)
- Dark mode (follows system), English and Italian
- No ads, no analytics, no tracking
- **Optional peer-to-peer location sharing and live tracking** (see below)

## Location sharing (optional, peer-to-peer)

Entirely optional and off by default. It uses **UnifiedPush** as the transport, so there is **no SOS Ring server**: you pick a UnifiedPush distributor app (for example [ntfy](https://f-droid.org/packages/io.heckel.ntfy/)), and it relays only encrypted data.

- **End-to-end encrypted** with Web Push encryption (RFC 8291); the distributor never sees your location
- Each message is **signed** with a per-device key in the Android Keystore so the recipient can verify the sender
- Peers **pair explicitly via QR code**; sharing is off by default, per contact
- **Live tracking** streams a position roughly every 10 seconds for a chosen duration and shows the path on an **OpenStreetMap** map inside the app; the path is saved locally on the requester's device only

## Permissions

| Permission | Why |
|---|---|
| `READ_PHONE_STATE` | Detect incoming calls |
| `READ_CALL_LOG` | Get the caller's number |
| `READ_CONTACTS` | Pick VIP contacts from the phonebook |
| `ACCESS_NOTIFICATION_POLICY` | Override Do Not Disturb |
| `MODIFY_AUDIO_SETTINGS` | Change ringer mode and volume |
| `FOREGROUND_SERVICE` (+ `LOCATION`, `SPECIAL_USE`) | Keep the monitoring service alive; foreground type during live sharing |
| `VIBRATE` | Force vibration during VIP calls |
| `POST_NOTIFICATIONS` | Persistent service notification (Android 13+) |
| `INTERNET` | Optional peer-to-peer location sharing |
| `ACCESS_FINE_LOCATION` / `ACCESS_BACKGROUND_LOCATION` | Optional location sharing with VIP contacts |

## Building

```bash
./gradlew assembleFdroidRelease
```

The APK is produced at `app/build/outputs/apk/fdroid/release/app-fdroid-release.apk`.

## License

GPL-3.0-or-later. See [LICENSE](LICENSE).
