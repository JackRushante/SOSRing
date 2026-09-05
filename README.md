# SOS Ring

**Make calls and messages from VIP contacts audible even in Silent / Vibrate / Do Not Disturb mode.**

SOS Ring is a FOSS Android app that overrides Silent, Vibrate, and Do Not Disturb when you receive a call or supported direct message from a whitelisted VIP contact. After the alert ends, your phone is restored to its exact previous state.

Available on F-Droid.

## Why?

Android 14+ removed per-contact DND exceptions, and there is no built-in way to let specific contacts bypass silent mode. Google restricts `READ_CALL_LOG` on the Play Store, so this kind of app cannot be published there.

SOS Ring solves this with a foreground service that monitors incoming calls and, when a VIP contact calls, plays the ringtone via the ALARM audio stream (the one stream that bypasses DND on every device).

## Features

- Pick VIP contacts from the phonebook or enter them manually; select any or all numbers when a contact has more than one
- Audible alerts for direct WhatsApp, Google Messages (SMS/RCS), and Telegram conversations from paired VIP contacts
- Separate controls for calls (25-100%) and messages (on/off, 5-100%, Android default sound or the sound assigned to the VIP contact)
- Choose to sound from the first call or only from the second call within 3, 5, or 10 minutes, with exceptions for individual VIP numbers
- Optional volume doubling on subsequent calls when the configured call volume is below 50%, up to 100%
- Works in **Silent**, **Vibrate**, AND **Do Not Disturb** mode
- Full state restore after each alert (ringer mode, all volumes including alarm, DND)
- Temporary mute timer (1-12 hours) and Quiet Hours apply to both calls and messages
- Survives reboots (auto-start)
- Minimal battery usage (event-driven, no polling)
- Dark mode (follows system), English and Italian
- No ads, no analytics, no tracking
- **Optional peer-to-peer location sharing and live tracking** (see below)

## Repeated VIP calls

Open **Settings → When to sound for VIP calls**, or tap the call-mode summary on Home. The default remains **From the first call**; the alternative starts overriding the phone's sound settings from the **second call from the same VIP number** within 3, 5 (default), or 10 minutes. The first call keeps the phone's normal sound settings. Each VIP's menu can inherit the general rule or override it.

Only separate incoming cellular calls count, not WhatsApp/Telegram calls or messages. The window starts with the first call and does not slide. An unanswered or rejected call counts; answering resets that number's sequence. Pausing monitoring, Quiet Hours, disabling monitoring, or changing call settings resets pending sequences. Overlapping calls/call waiting do not create additional attempts.

**Double the volume on subsequent calls** is optional and off by default. It applies only when the configured base call volume is below 50%. For example, a 25% base becomes **25% → 50% → 100%**, starting with the first call allowed to sound (the second actual call in second-call mode). This changes volume between calls, not gradually during one call. The original alarm volume is restored after each call. At a base of 50% or higher, the existing volume behavior is unchanged.

Messages retain their separate settings and never advance the call counter. Their sound override is suppressed while a cellular call is ringing or ongoing.

Pending call sequences are stored locally as hashed VIP-number identifiers, monotonic start times and capped counts. They are only valid for the selected window, are not included in configuration exports, and do not survive reboot. If the app restarts during a call whose outcome is unknown, that sequence is discarded conservatively.

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
| Notification access | Identify paired direct conversations from WhatsApp, Google Messages, and Telegram; message text is processed transiently and never stored |
| `ACCESS_NOTIFICATION_POLICY` | Override Do Not Disturb |
| `MODIFY_AUDIO_SETTINGS` | Change ringer mode and volume |
| `FOREGROUND_SERVICE` (+ `LOCATION`, `SPECIAL_USE`) | Keep the monitoring service alive; foreground type during live sharing |
| `VIBRATE` | Force vibration during VIP calls |
| `POST_NOTIFICATIONS` | Persistent service notification (Android 13+) |
| `INTERNET` | Optional peer-to-peer location sharing |
| `ACCESS_FINE_LOCATION` / `ACCESS_BACKGROUND_LOCATION` | Optional location sharing with VIP contacts |

VIP message alerts are optional. Conversation identifiers are stored only as hashes, together with technical deduplication fingerprints. SOS Ring does not store notification message text, contact names, or phone numbers extracted from notifications.

## Building

```bash
./gradlew assembleFdroidRelease
```

The APK is produced at `app/build/outputs/apk/fdroid/release/app-fdroid-release.apk`.

## License

GPL-3.0-or-later. See [LICENSE](LICENSE).
