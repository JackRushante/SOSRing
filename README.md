# SOS Ring

**Force ringtone for VIP contacts even in Silent/Vibrate/DND mode.**

SOS Ring is an Android app that overrides Silent, Vibrate, and Do Not Disturb mode when you receive a call from a whitelisted VIP contact. After the call ends, your phone is restored to its exact previous state.

## Why?

Android 14+ removed per-contact DND exceptions. There is no built-in way to let specific contacts bypass silent mode. Google restricts `READ_CALL_LOG` on the Play Store, so this type of app can't be published there.

SOS Ring solves this with a simple approach: a foreground service monitors incoming calls, and when a VIP contact calls, it plays the ringtone via the ALARM audio stream (the only stream that bypasses all DND restrictions on all devices).

## Features

- Pick VIP contacts from phonebook or enter manually
- Configurable override volume (50-100%)
- Works in **Silent**, **Vibrate**, AND **Do Not Disturb** mode
- Full state restore after call (ringer mode, all volumes including alarm, DND)
- Survives phone reboots (auto-start)
- Minimal battery usage (event-driven, no polling)
- Dark mode support (follows system)
- Dual language: English and Italian
- No ads, no tracking, no internet required

## What's new in v2.0

### Bug fixes

- **Fix**: No more double ringtone when phone is already in normal mode
- **Fix**: Volume slider now correctly controls MediaPlayer volume (range extended to 25-100%)

### New features

- **Temporary mute timer (1-12 hours)**: Disables ringtone override while keeping GPS active — useful when you need silence without turning off the service entirely
- **Choose override sound type**: Select between a ringtone or a notification sound for the override playback
- **Per-contact ringtone toggle**: Disable the override for specific VIP contacts while keeping GPS tracking active for them
- **Navigation drawer**: Home, Settings, Location Log, and Privacy & Licenses sections are now accessible via a side drawer
- **Privacy & Licenses page**: In-app page with data handling information and GPL-3.0 license details

## Permissions

| Permission | Why |
|---|---|
| `READ_PHONE_STATE` | Detect incoming calls |
| `READ_CALL_LOG` | Get caller's phone number |
| `READ_CONTACTS` | Pick VIP contacts from phonebook |
| `ACCESS_NOTIFICATION_POLICY` | Override Do Not Disturb |
| `MODIFY_AUDIO_SETTINGS` | Change ringer mode and volume |
| `FOREGROUND_SERVICE` | Keep monitoring service alive |
| `VIBRATE` | Force vibration during VIP calls |
| `POST_NOTIFICATIONS` | Persistent service notification (Android 13+) |

## How it works

1. `CallMonitorService` (ForegroundService) registers a `BroadcastReceiver` for `PHONE_STATE`
2. On `RINGING`: checks if caller is VIP → saves audio state → overrides DND → sets volumes → plays ringtone via `MediaPlayer` on `USAGE_ALARM` stream → starts vibration
3. On `OFFHOOK` (answered): stops ringtone/vibration
4. On `IDLE` (call ended): restores ringer mode → restores all volumes → restores DND (with 200ms delay to handle OEM quirks)

## Building

```bash
./gradlew assembleRelease
```

APK will be at `app/build/outputs/apk/release/app-release.apk`

## Tested on

- OnePlus (Android 14) - Silent, Vibrate, DND: all working
- Xiaomi, Samsung, Realme - DND bypass confirmed

## License

GPL-3.0-or-later. See [LICENSE](LICENSE).
