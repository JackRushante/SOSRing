# Quiet Hours — Design Spec

## Summary

Add "Quiet Hours" rules to SOS Ring so the user can define time windows when the app does NOT override audio, even for VIP calls. The app remains always-on by default; quiet rules are exceptions.

## Behavior

- SOS Ring is always active unless a quiet rule matches the current day+time
- Multiple rules can coexist (e.g., "Mon-Fri 9-18" + "Sat 22-06")
- If any rule matches, the override is suppressed — VIP calls ring normally (system behavior)
- Rules are weekly-recurring, no one-off or date-specific rules
- Cross-midnight rules are supported (e.g., 22:00-06:00 means "tonight to tomorrow morning")

## Data Model

### QuietRule

```kotlin
data class QuietRule(
    val days: Set<Int>,    // Calendar.MONDAY..SUNDAY (values 2..1)
    val startHour: Int,    // 0-23
    val startMinute: Int,  // 0-59
    val endHour: Int,      // 0-23
    val endMinute: Int     // 0-59
)
```

- Stored in SharedPreferences as JSON array under key `"quiet_rules"`
- Same serialization pattern as VipContact (JSONArray of JSONObjects)
- Days stored as integer array matching `java.util.Calendar` constants

### JSON format

```json
[
  {
    "days": [2, 3, 4, 5, 6],
    "startHour": 9, "startMinute": 0,
    "endHour": 18, "endMinute": 0
  }
]
```

## Logic: isInQuietPeriod()

Added to `PrefsManager` (pure function, testable):

```
fun isInQuietPeriod(): Boolean
  1. Get current day (Calendar.DAY_OF_WEEK) and time (HH:mm)
  2. For each QuietRule:
     a. Same-day rule (end > start):
        - Match if current day is in rule.days AND time >= start AND time < end
     b. Cross-midnight rule (end <= start):
        - Match if current day is in rule.days AND time >= start (evening part)
        - OR if PREVIOUS day is in rule.days AND time < end (morning part)
  3. Return true if any rule matches
```

"Previous day" wraps: Monday's previous day is Sunday.

## Service Integration

In `CallMonitorService`, the phone state receiver changes from:

```kotlin
if (isVipNumber(number)) overrideAudio()
```

to:

```kotlin
if (isVipNumber(number) && !prefs.isInQuietPeriod()) overrideAudio()
```

Single line change. No other service modifications needed.

## UI Design

### Position in MainActivity

Between Volume Control Card and Permissions Card:

```
Service Toggle Card
Volume Control Card
--- Quiet Hours Card (NEW) ---
Permissions Card
VIP Contacts
```

### Quiet Hours Card

Material CardView matching existing style. Contains:
- Title: "Quiet Hours" / "Orari silenziosi"
- Subtitle: "SOS Ring pauses during these times" / "SOS Ring si mette in pausa in questi orari"
- Dynamic list of rules (LinearLayout, not RecyclerView — few items expected)
- "+ Add quiet rule" / "+ Aggiungi regola" button at bottom
- Empty state: only the add button visible
- Max 10 rules — add button disabled at limit, toast: "Maximum rules reached" / "Numero massimo di regole raggiunto"

### Rule Item (item_quiet_rule.xml)

```
┌─────────────────────────────────────┐
│  Mon-Fri  09:00 - 18:00       [X]  │
└─────────────────────────────────────┘
```

- Days abbreviated (Mon-Fri / Lun-Ven or individual: Mon, Wed, Fri / Lun, Mer, Ven)
- Time range with 24h format
- Cross-midnight shows "(+1)" hint after end time
- Delete button (ImageButton with delete icon)
- No edit — delete and recreate (YAGNI)

### Day abbreviation logic

- Consecutive runs compressed: Mon, Tue, Wed, Thu, Fri -> "Mon-Fri" / "Lun-Ven"
- Non-consecutive listed: Mon, Wed, Fri -> "Mon, Wed, Fri" / "Lun, Mer, Ven"
- All 7 days: "Every day" / "Ogni giorno"

### Add Rule Dialog (dialog_quiet_rule.xml)

```
┌─────────────────────────────────────┐
│  New Quiet Rule                     │
│                                     │
│  Days:                              │
│  [M] [T] [W] [T] [F] [S] [S]      │
│                                     │
│  From:  [09:00]    To: [18:00]     │
│         (hint: next day)            │
│                                     │
│       [Cancel]  [Save]              │
└─────────────────────────────────────┘
```

- 7 Material Chips (FilterChip), toggle selection
- Day labels localized: M T W T F S S / L M M G V S D
- From/To: TextViews that launch MaterialTimePicker on click
- Default: no days selected, From 09:00, To 18:00
- Validation: at least 1 day selected, start != end
- Cross-midnight hint appears when end <= start

## Files Modified

| File | Change |
|------|--------|
| `PrefsManager.kt` | + QuietRule data class, + getQuietRules/saveQuietRules, + isInQuietPeriod() |
| `CallMonitorService.kt` | + 1 line: check isInQuietPeriod() before override |
| `MainActivity.kt` | + Quiet Hours UI section, add/delete rule logic |
| `activity_main.xml` | + Quiet Hours card between volume and permissions |
| `item_quiet_rule.xml` | NEW — rule item layout |
| `dialog_quiet_rule.xml` | NEW — add rule dialog layout |
| `res/values/strings.xml` | + Quiet Hours strings (English) |
| `res/values-it/strings.xml` | + Quiet Hours strings (Italian) |

## No New Permissions

No additional permissions required. The feature only adds conditional logic to the existing override flow.

## No New Dependencies

Uses only existing Material Components (Chip, MaterialTimePicker, CardView).

## Out of Scope

- Per-rule enable/disable toggle
- Edit existing rules (delete + recreate instead)
- One-off date-specific rules
- Per-contact quiet hours
- Notification when a VIP call is suppressed by quiet hours
