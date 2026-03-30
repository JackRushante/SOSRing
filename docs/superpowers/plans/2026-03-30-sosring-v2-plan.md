# SOS Ring v2.0 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add 5 features + 1 bugfix to SOS Ring: fix double ringtone, fix volume slider range, add mute timer, sound type choice, per-VIP ringtone toggle, and navigation drawer restructure.

**Architecture:** Refactor monolithic `MainActivity` into a `DrawerLayout` host with 4 Fragment destinations. New preferences fields in `PrefsManager`. Service-level changes in `CallMonitorService` for ringer mode check, mute timer, and per-contact ringtone control. New `MuteTimerReceiver` for alarm-based reactivation.

**Tech Stack:** Kotlin, Android SDK 34, Material Design 3, SharedPreferences (JSON), AlarmManager, ViewBinding

---

## File Structure

### New files
- `app/src/main/java/com/lorenzomarci/sosring/HomeFragment.kt` — Home screen (service toggle, permissions, VIP list, mute timer)
- `app/src/main/java/com/lorenzomarci/sosring/SettingsFragment.kt` — Settings (volume, sound type, quiet hours)
- `app/src/main/java/com/lorenzomarci/sosring/LocationLogFragment.kt` — Location request log
- `app/src/main/java/com/lorenzomarci/sosring/PrivacyFragment.kt` — Privacy policy and licenses
- `app/src/main/java/com/lorenzomarci/sosring/MuteTimerReceiver.kt` — BroadcastReceiver for mute timer expiry
- `app/src/main/res/layout/fragment_home.xml` — Home fragment layout
- `app/src/main/res/layout/fragment_settings.xml` — Settings fragment layout
- `app/src/main/res/layout/fragment_location_log.xml` — Location log fragment layout
- `app/src/main/res/layout/fragment_privacy.xml` — Privacy fragment layout
- `app/src/main/res/menu/nav_menu.xml` — Navigation drawer menu

### Modified files
- `app/src/main/java/com/lorenzomarci/sosring/MainActivity.kt` — Strip to drawer host
- `app/src/main/java/com/lorenzomarci/sosring/CallMonitorService.kt` — Ringer mode check, mute timer check, per-contact ringtone, volume fix, sound type
- `app/src/main/java/com/lorenzomarci/sosring/PrefsManager.kt` — New fields: muteUntilTimestamp, overrideSoundType, ringtoneEnabled in VipContact, MIN_VOLUME 25
- `app/src/main/java/com/lorenzomarci/sosring/VipNumbersAdapter.kt` — Ringtone toggle icon, icon size reduction
- `app/src/main/java/com/lorenzomarci/sosring/BootReceiver.kt` — Re-register mute alarm
- `app/src/main/res/layout/activity_main.xml` — DrawerLayout wrapper
- `app/src/main/res/layout/item_vip_number.xml` — Add bell icon, resize all icons 20%
- `app/src/main/res/values/strings.xml` — New English strings
- `app/src/main/res/values-it/strings.xml` — New Italian strings
- `app/src/main/AndroidManifest.xml` — Register MuteTimerReceiver, SCHEDULE_EXACT_ALARM permission
- `app/build.gradle.kts` — Version bump to 2.0

---

## Task 1: Fix double ringtone (Feature 1) + Volume slider fix (Feature 7)

**Files:**
- Modify: `app/src/main/java/com/lorenzomarci/sosring/CallMonitorService.kt:165-237`
- Modify: `app/src/main/java/com/lorenzomarci/sosring/PrefsManager.kt:36`
- Modify: `app/src/main/res/layout/activity_main.xml:115`

- [ ] **Step 1: Fix double ringtone — add guard clause in overrideAudio()**

In `CallMonitorService.kt`, add ringer mode check at the start of `overrideAudio()`:

```kotlin
// CallMonitorService.kt — replace overrideAudio() lines 165-221
@Suppress("DEPRECATION")
private fun overrideAudio() {
    if (isOverriding) return

    // Feature 1: Skip override if phone is already in normal ringer mode
    if (audioManager.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
        Log.i(TAG, "Phone already in NORMAL mode, skipping override.")
        return
    }

    try {
        // Save ALL current audio state
        savedRingerMode = audioManager.ringerMode
        savedRingVolume = audioManager.getStreamVolume(AudioManager.STREAM_RING)
        savedNotifVolume = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
        savedAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
        savedDndFilter = notificationManager.currentInterruptionFilter
        savedVibrateRing = audioManager.getVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER)
        savedVibrateNotif = audioManager.getVibrateSetting(AudioManager.VIBRATE_TYPE_NOTIFICATION)

        Log.d(TAG, "Saved state: ringer=$savedRingerMode, ringVol=$savedRingVolume, " +
                "notifVol=$savedNotifVolume, alarmVol=$savedAlarmVolume, dnd=$savedDndFilter")

        // 1. Override DND FIRST
        if (notificationManager.isNotificationPolicyAccessGranted) {
            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            Log.d(TAG, "DND overridden to FILTER_ALL")
        } else {
            Log.w(TAG, "DND permission NOT granted!")
        }

        // 2. Ringer mode to NORMAL
        audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
        Log.d(TAG, "Ringer set to NORMAL")

        // 3. Calculate target volume from user preference (25-100%)
        val volumePercent = prefs.volumePercent
        fun targetVolume(stream: Int): Int {
            val max = audioManager.getStreamMaxVolume(stream)
            return (max * volumePercent / 100).coerceAtLeast(1)
        }

        // 4. Set ring, notification, alarm volumes to configured level
        val ringTarget = targetVolume(AudioManager.STREAM_RING)
        val notifTarget = targetVolume(AudioManager.STREAM_NOTIFICATION)
        val alarmTarget = targetVolume(AudioManager.STREAM_ALARM)
        audioManager.setStreamVolume(AudioManager.STREAM_RING, ringTarget, 0)
        audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, notifTarget, 0)
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, alarmTarget, 0)
        Log.d(TAG, "Volumes set to $volumePercent%: ring=$ringTarget, notif=$notifTarget, alarm=$alarmTarget")

    } catch (e: Exception) {
        Log.e(TAG, "Error during audio override: ${e.message}", e)
    }

    // 6. Play ringtone via ALARM stream (bypasses ALL DND restrictions)
    startRingtone()

    // 7. Start vibration
    startVibration()

    isOverriding = true
    notificationManager.notify(OVERRIDE_NOTIFICATION_ID, buildOverrideNotification())
}
```

- [ ] **Step 2: Fix volume on MediaPlayer — apply volumePercent to MediaPlayer.setVolume()**

In `CallMonitorService.kt`, update `startRingtone()`:

```kotlin
// CallMonitorService.kt — replace startRingtone() lines 223-243
private fun startRingtone() {
    try {
        val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        Log.d(TAG, "Ringtone URI: $ringtoneUri")
        val vol = prefs.volumePercent / 100f
        mediaPlayer = MediaPlayer().apply {
            setDataSource(this@CallMonitorService, ringtoneUri)
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            isLooping = true
            setVolume(vol, vol)
            prepare()
            start()
        }
        Log.i(TAG, "Ringtone PLAYING on ALARM stream at ${prefs.volumePercent}% volume.")
    } catch (e: Exception) {
        Log.e(TAG, "Failed to play ringtone: ${e.message}", e)
    }
}
```

- [ ] **Step 3: Extend volume slider range to 25-100%**

In `PrefsManager.kt`, change:
```kotlin
// PrefsManager.kt line 36
const val MIN_VOLUME_PERCENT = 25
```

In `activity_main.xml`, change the slider `valueFrom`:
```xml
<!-- activity_main.xml line 115 -->
android:valueFrom="25"
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/lorenzomarci/sosring/CallMonitorService.kt \
       app/src/main/java/com/lorenzomarci/sosring/PrefsManager.kt \
       app/src/main/res/layout/activity_main.xml
git commit -m "fix: skip override when ringer already normal, fix MediaPlayer volume, extend slider to 25-100%"
```

---

## Task 2: PrefsManager — new fields for mute timer, sound type, ringtone per contact

**Files:**
- Modify: `app/src/main/java/com/lorenzomarci/sosring/PrefsManager.kt`

- [ ] **Step 1: Add ringtoneEnabled to VipContact data class**

```kotlin
// PrefsManager.kt line 10 — replace VipContact
data class VipContact(
    val name: String,
    val number: String,
    val locationEnabled: Boolean = false,
    val ringtoneEnabled: Boolean = true
)
```

- [ ] **Step 2: Update getContacts() to deserialize ringtoneEnabled**

```kotlin
// PrefsManager.kt — in getContacts(), replace the VipContact constructor (lines 68-72)
VipContact(
    name = obj.getString("name"),
    number = obj.getString("number"),
    locationEnabled = obj.optBoolean("locationEnabled", false),
    ringtoneEnabled = obj.optBoolean("ringtoneEnabled", true)
)
```

- [ ] **Step 3: Update saveContacts() to serialize ringtoneEnabled**

```kotlin
// PrefsManager.kt — in saveContacts(), replace the JSONObject block (lines 82-86)
arr.put(JSONObject().apply {
    put("name", c.name)
    put("number", c.number)
    put("locationEnabled", c.locationEnabled)
    put("ringtoneEnabled", c.ringtoneEnabled)
})
```

- [ ] **Step 4: Add mute timer and sound type fields**

Add these after the `volumePercent` property (after line 60):

```kotlin
// PrefsManager.kt — new fields after volumePercent

private const val KEY_MUTE_UNTIL = "mute_until_timestamp"
private const val KEY_OVERRIDE_SOUND_TYPE = "override_sound_type"

// Add these constants to companion object:
const val SOUND_TYPE_RINGTONE = 0
const val SOUND_TYPE_NOTIFICATION = 1
```

Add these properties after `volumePercent`:

```kotlin
var muteUntilTimestamp: Long
    get() = prefs.getLong(KEY_MUTE_UNTIL, 0L)
    set(value) = prefs.edit().putLong(KEY_MUTE_UNTIL, value).apply()

val isMuted: Boolean
    get() {
        val until = muteUntilTimestamp
        if (until == 0L) return false
        if (System.currentTimeMillis() >= until) {
            muteUntilTimestamp = 0L
            return false
        }
        return true
    }

var overrideSoundType: Int
    get() = prefs.getInt(KEY_OVERRIDE_SOUND_TYPE, SOUND_TYPE_RINGTONE)
    set(value) = prefs.edit().putInt(KEY_OVERRIDE_SOUND_TYPE, value).apply()
```

- [ ] **Step 5: Add helper to update ringtoneEnabled for a contact**

```kotlin
// PrefsManager.kt — add after updateContactLocationEnabled()
fun updateContactRingtoneEnabled(number: String, enabled: Boolean) {
    val updated = getContacts().map { c ->
        if (normalizeNumber(c.number) == normalizeNumber(number)) {
            c.copy(ringtoneEnabled = enabled)
        } else c
    }
    saveContacts(updated)
}
```

- [ ] **Step 6: Add method to find VipContact by incoming number**

```kotlin
// PrefsManager.kt — add after getVipNumbers()
fun findVipContact(incoming: String): VipContact? {
    val normalized = normalizeNumber(incoming)
    return getContacts().find { contact ->
        android.telephony.PhoneNumberUtils.compare(normalizeNumber(contact.number), normalized)
    }
}
```

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/lorenzomarci/sosring/PrefsManager.kt
git commit -m "feat: add mute timer, sound type, and ringtoneEnabled fields to PrefsManager"
```

---

## Task 3: CallMonitorService — mute timer, per-contact ringtone, sound type

**Files:**
- Modify: `app/src/main/java/com/lorenzomarci/sosring/CallMonitorService.kt`

- [ ] **Step 1: Replace isVipNumber with findVipContact, add mute/ringtone checks**

Replace `isVipNumber` and update the phone receiver:

```kotlin
// CallMonitorService.kt — replace isVipNumber() (lines 157-162)
private fun findVipContact(incoming: String): VipContact? {
    return prefs.findVipContact(incoming)
}
```

Update the RINGING handler in `phoneReceiver` (lines 80-84):

```kotlin
TelephonyManager.EXTRA_STATE_RINGING -> {
    if (number != null) {
        val vipContact = findVipContact(number)
        if (vipContact != null && !prefs.isInQuietPeriod()) {
            if (vipContact.ringtoneEnabled && !prefs.isMuted) {
                Log.i(TAG, "VIP call detected! Overriding audio.")
                overrideAudio()
            } else {
                Log.i(TAG, "VIP call detected but ringtone disabled or muted. Skipping override.")
            }
        }
    }
}
```

- [ ] **Step 2: Use configurable sound type in startRingtone()**

Update `startRingtone()` to use `prefs.overrideSoundType`:

```kotlin
// CallMonitorService.kt — replace startRingtone()
private fun startRingtone() {
    try {
        val soundType = if (prefs.overrideSoundType == PrefsManager.SOUND_TYPE_NOTIFICATION) {
            RingtoneManager.TYPE_NOTIFICATION
        } else {
            RingtoneManager.TYPE_RINGTONE
        }
        val ringtoneUri = RingtoneManager.getDefaultUri(soundType)
        Log.d(TAG, "Sound URI ($soundType): $ringtoneUri")
        val vol = prefs.volumePercent / 100f
        mediaPlayer = MediaPlayer().apply {
            setDataSource(this@CallMonitorService, ringtoneUri)
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            isLooping = true
            setVolume(vol, vol)
            prepare()
            start()
        }
        Log.i(TAG, "Sound PLAYING on ALARM stream at ${prefs.volumePercent}% volume.")
    } catch (e: Exception) {
        Log.e(TAG, "Failed to play sound: ${e.message}", e)
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/lorenzomarci/sosring/CallMonitorService.kt
git commit -m "feat: add mute timer check, per-contact ringtone toggle, configurable sound type"
```

---

## Task 4: MuteTimerReceiver + BootReceiver update

**Files:**
- Create: `app/src/main/java/com/lorenzomarci/sosring/MuteTimerReceiver.kt`
- Modify: `app/src/main/java/com/lorenzomarci/sosring/BootReceiver.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Create MuteTimerReceiver**

```kotlin
// MuteTimerReceiver.kt
package com.lorenzomarci.sosring

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class MuteTimerReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_UNMUTE = "com.lorenzomarci.sosring.ACTION_UNMUTE"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_UNMUTE) {
            val prefs = PrefsManager(context)
            prefs.muteUntilTimestamp = 0L
            Log.i("SOSRing", "Mute timer expired. Ringtone override re-enabled.")
        }
    }
}
```

- [ ] **Step 2: Update BootReceiver to re-register mute alarm**

```kotlin
// BootReceiver.kt — replace entire file
package com.lorenzomarci.sosring

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = PrefsManager(context)
            if (prefs.isServiceEnabled) {
                CallMonitorService.start(context)
            }
            // Re-register mute timer alarm if still active
            val muteUntil = prefs.muteUntilTimestamp
            if (muteUntil > System.currentTimeMillis()) {
                scheduleMuteAlarm(context, muteUntil)
                Log.i("SOSRing", "Boot: re-registered mute alarm for $muteUntil")
            }
        }
    }

    companion object {
        fun scheduleMuteAlarm(context: Context, triggerAtMillis: Long) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, MuteTimerReceiver::class.java).apply {
                action = MuteTimerReceiver.ACTION_UNMUTE
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent
                )
            }
        }

        fun cancelMuteAlarm(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, MuteTimerReceiver::class.java).apply {
                action = MuteTimerReceiver.ACTION_UNMUTE
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }
    }
}
```

- [ ] **Step 3: Register MuteTimerReceiver and add SCHEDULE_EXACT_ALARM in AndroidManifest.xml**

Add permission after existing permissions:
```xml
<!-- AndroidManifest.xml — add after line 20 (REQUEST_INSTALL_PACKAGES) -->
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
```

Add receiver after BootReceiver (after line 55):
```xml
<receiver
    android:name=".MuteTimerReceiver"
    android:exported="false">
    <intent-filter>
        <action android:name="com.lorenzomarci.sosring.ACTION_UNMUTE" />
    </intent-filter>
</receiver>
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/lorenzomarci/sosring/MuteTimerReceiver.kt \
       app/src/main/java/com/lorenzomarci/sosring/BootReceiver.kt \
       app/src/main/AndroidManifest.xml
git commit -m "feat: add MuteTimerReceiver and alarm scheduling for mute timer"
```

---

## Task 5: VipNumbersAdapter — ringtone toggle icon + icon size reduction

**Files:**
- Modify: `app/src/main/java/com/lorenzomarci/sosring/VipNumbersAdapter.kt`
- Modify: `app/src/main/res/layout/item_vip_number.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-it/strings.xml`

- [ ] **Step 1: Update item_vip_number.xml — add bell icon, reduce all icons to 32dp (from 40dp = 20% smaller)**

```xml
<?xml version="1.0" encoding="utf-8"?>
<com.google.android.material.card.MaterialCardView
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginBottom="8dp"
    app:cardElevation="1dp"
    app:cardCornerRadius="8dp">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="center_vertical"
        android:padding="12dp">

        <LinearLayout
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:orientation="vertical">

            <TextView
                android:id="@+id/tvName"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:textSize="16sp"
                android:textStyle="bold" />

            <TextView
                android:id="@+id/tvNumber"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:textSize="14sp"
                android:textColor="?android:textColorSecondary" />
        </LinearLayout>

        <ImageButton
            android:id="@+id/btnRingtone"
            android:layout_width="32dp"
            android:layout_height="32dp"
            android:background="?selectableItemBackgroundBorderless"
            android:src="@android:drawable/ic_lock_silent_mode_off"
            android:contentDescription="@string/ringtone_toggle_desc"
            android:layout_marginEnd="4dp"
            android:scaleType="centerInside" />

        <ImageButton
            android:id="@+id/btnLocation"
            android:layout_width="32dp"
            android:layout_height="32dp"
            android:background="?selectableItemBackgroundBorderless"
            android:src="@android:drawable/ic_menu_mylocation"
            android:contentDescription="@string/location_icon_desc"
            android:visibility="gone"
            android:layout_marginEnd="4dp"
            android:scaleType="centerInside" />

        <ImageButton
            android:id="@+id/btnEdit"
            android:layout_width="32dp"
            android:layout_height="32dp"
            android:background="?selectableItemBackgroundBorderless"
            android:src="@android:drawable/ic_menu_edit"
            android:contentDescription="@string/edit_icon_desc"
            android:layout_marginEnd="4dp"
            android:scaleType="centerInside" />

        <ImageButton
            android:id="@+id/btnDelete"
            android:layout_width="32dp"
            android:layout_height="32dp"
            android:background="?selectableItemBackgroundBorderless"
            android:src="@android:drawable/ic_menu_delete"
            android:contentDescription="@string/delete_icon_desc"
            android:scaleType="centerInside" />
    </LinearLayout>
</com.google.android.material.card.MaterialCardView>
```

- [ ] **Step 2: Update VipNumbersAdapter to handle ringtone toggle**

```kotlin
// VipNumbersAdapter.kt — replace entire file
package com.lorenzomarci.sosring

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lorenzomarci.sosring.databinding.ItemVipNumberBinding

class VipNumbersAdapter(
    private val onEdit: (Int, VipContact) -> Unit,
    private val onDelete: (Int) -> Unit,
    private val onLocation: ((VipContact) -> Unit)? = null,
    private val onRingtoneToggle: ((Int, VipContact) -> Unit)? = null
) : ListAdapter<VipContact, VipNumbersAdapter.ViewHolder>(DiffCallback) {

    object DiffCallback : DiffUtil.ItemCallback<VipContact>() {
        override fun areItemsTheSame(old: VipContact, new: VipContact) =
            old.number == new.number
        override fun areContentsTheSame(old: VipContact, new: VipContact) =
            old == new
    }

    inner class ViewHolder(private val binding: ItemVipNumberBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(contact: VipContact, position: Int) {
            binding.tvName.text = contact.name
            binding.tvNumber.text = contact.number
            binding.btnEdit.setOnClickListener { onEdit(position, contact) }
            binding.btnDelete.setOnClickListener { onDelete(position) }

            // Ringtone toggle
            updateRingtoneIcon(contact.ringtoneEnabled)
            binding.btnRingtone.setOnClickListener {
                onRingtoneToggle?.invoke(position, contact)
            }

            // Location button
            if (BuildConfig.LOCATION_ENABLED && contact.locationEnabled && onLocation != null) {
                binding.btnLocation.visibility = View.VISIBLE
                binding.btnLocation.setOnClickListener { onLocation.invoke(contact) }
            } else {
                binding.btnLocation.visibility = View.GONE
            }
        }

        private fun updateRingtoneIcon(enabled: Boolean) {
            binding.btnRingtone.setImageResource(
                if (enabled) android.R.drawable.ic_lock_silent_mode_off
                else android.R.drawable.ic_lock_silent_mode
            )
            binding.btnRingtone.alpha = if (enabled) 1.0f else 0.4f
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemVipNumberBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }
}
```

- [ ] **Step 3: Add new strings for icon descriptions**

In `values/strings.xml`, add:
```xml
<!-- Icon descriptions -->
<string name="ringtone_toggle_desc">Toggle ringtone for this contact</string>
<string name="edit_icon_desc">Edit contact</string>
<string name="delete_icon_desc">Delete contact</string>
```

In `values-it/strings.xml`, add:
```xml
<!-- Icon descriptions -->
<string name="ringtone_toggle_desc">Attiva/disattiva suoneria per questo contatto</string>
<string name="edit_icon_desc">Modifica contatto</string>
<string name="delete_icon_desc">Elimina contatto</string>
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/lorenzomarci/sosring/VipNumbersAdapter.kt \
       app/src/main/res/layout/item_vip_number.xml \
       app/src/main/res/values/strings.xml \
       app/src/main/res/values-it/strings.xml
git commit -m "feat: add ringtone toggle icon per VIP contact, reduce all icons 20%"
```

---

## Task 6: Add all new strings (mute timer, settings, nav, privacy)

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-it/strings.xml`

- [ ] **Step 1: Add English strings**

Append before closing `</resources>` in `values/strings.xml`:

```xml
<!-- Mute Timer -->
<string name="mute_button">Mute ringtone</string>
<string name="mute_button_cancel">Re-enable ringtone</string>
<string name="mute_dialog_title">Mute ringtone</string>
<string name="mute_dialog_message">Override ringtone will be disabled for the selected duration. GPS location sharing will remain active.</string>
<string name="mute_dialog_hours">hours</string>
<string name="mute_countdown">Ringtone muted — re-enables in %1$dh %2$dm</string>
<string name="mute_activated">Ringtone muted for %1$d hours</string>
<string name="mute_cancelled">Ringtone re-enabled</string>
<!-- Settings -->
<string name="settings_sound_title">Override sound</string>
<string name="settings_sound_ringtone">Phone ringtone</string>
<string name="settings_sound_notification">Notification sound</string>
<!-- Navigation -->
<string name="nav_home">Home</string>
<string name="nav_settings">Settings</string>
<string name="nav_location_log">Location log</string>
<string name="nav_privacy">Privacy &amp; licenses</string>
<!-- Privacy -->
<string name="privacy_title">Privacy &amp; licenses</string>
<string name="privacy_data_title">Data handling</string>
<string name="privacy_data_body">SOS Ring processes data exclusively on your device. No personal data is sent to external servers.\n\nThe internal version uses an encrypted peer-to-peer channel (ntfy) for location sharing. Coordinates are encrypted end-to-end with AES-256-GCM before transmission.\n\nNo analytics, no tracking, no ads.</string>
<string name="privacy_license_title">License</string>
<string name="privacy_license_body">SOS Ring is free software licensed under GPL-3.0-or-later.\n\nSource code: github.com/JackRushante/SOSRing</string>
<string name="privacy_contact_title">Contact</string>
<string name="privacy_contact_body">Developer: Lorenzo Marci</string>
```

- [ ] **Step 2: Add Italian strings**

Append before closing `</resources>` in `values-it/strings.xml`:

```xml
<!-- Mute Timer -->
<string name="mute_button">Disattiva suoneria</string>
<string name="mute_button_cancel">Riattiva suoneria</string>
<string name="mute_dialog_title">Disattiva suoneria</string>
<string name="mute_dialog_message">La suoneria di override sarà disabilitata per la durata selezionata. La condivisione posizione GPS rimarrà attiva.</string>
<string name="mute_dialog_hours">ore</string>
<string name="mute_countdown">Suoneria disattivata — riattivazione tra %1$dh %2$dm</string>
<string name="mute_activated">Suoneria disattivata per %1$d ore</string>
<string name="mute_cancelled">Suoneria riattivata</string>
<!-- Settings -->
<string name="settings_sound_title">Suono di override</string>
<string name="settings_sound_ringtone">Suoneria telefono</string>
<string name="settings_sound_notification">Suono di notifica</string>
<!-- Navigation -->
<string name="nav_home">Home</string>
<string name="nav_settings">Impostazioni</string>
<string name="nav_location_log">Log posizioni</string>
<string name="nav_privacy">Privacy e licenze</string>
<!-- Privacy -->
<string name="privacy_title">Privacy e licenze</string>
<string name="privacy_data_title">Trattamento dati</string>
<string name="privacy_data_body">SOS Ring elabora i dati esclusivamente sul tuo dispositivo. Nessun dato personale viene inviato a server esterni.\n\nLa versione internal utilizza un canale peer-to-peer cifrato (ntfy) per la condivisione posizione. Le coordinate sono cifrate end-to-end con AES-256-GCM prima della trasmissione.\n\nNessuna analitica, nessun tracciamento, nessuna pubblicità.</string>
<string name="privacy_license_title">Licenza</string>
<string name="privacy_license_body">SOS Ring è software libero con licenza GPL-3.0-or-later.\n\nCodice sorgente: github.com/JackRushante/SOSRing</string>
<string name="privacy_contact_title">Contatto</string>
<string name="privacy_contact_body">Sviluppatore: Lorenzo Marci</string>
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/res/values-it/strings.xml
git commit -m "feat: add all new strings for mute timer, settings, nav, privacy (en + it)"
```

---

## Task 7: Navigation Drawer — activity_main.xml + nav_menu.xml

**Files:**
- Modify: `app/src/main/res/layout/activity_main.xml`
- Create: `app/src/main/res/menu/nav_menu.xml`

- [ ] **Step 1: Create nav_menu.xml**

```bash
mkdir -p app/src/main/res/menu
```

```xml
<?xml version="1.0" encoding="utf-8"?>
<menu xmlns:android="http://schemas.android.com/apk/res/android">
    <item
        android:id="@+id/nav_home"
        android:icon="@android:drawable/ic_menu_myplaces"
        android:title="@string/nav_home" />
    <item
        android:id="@+id/nav_settings"
        android:icon="@android:drawable/ic_menu_preferences"
        android:title="@string/nav_settings" />
    <item
        android:id="@+id/nav_location_log"
        android:icon="@android:drawable/ic_menu_recent_history"
        android:title="@string/nav_location_log" />
    <item
        android:id="@+id/nav_privacy"
        android:icon="@android:drawable/ic_menu_info_details"
        android:title="@string/nav_privacy" />
</menu>
```

- [ ] **Step 2: Replace activity_main.xml with DrawerLayout host**

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.drawerlayout.widget.DrawerLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:id="@+id/drawerLayout"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:fitsSystemWindows="true">

    <!-- Main content -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:orientation="vertical">

        <com.google.android.material.appbar.MaterialToolbar
            android:id="@+id/toolbar"
            android:layout_width="match_parent"
            android:layout_height="?attr/actionBarSize"
            android:background="?colorPrimary"
            app:title="@string/app_name"
            app:titleTextColor="@android:color/white"
            app:navigationIcon="@android:drawable/ic_menu_sort_by_size" />

        <FrameLayout
            android:id="@+id/fragmentContainer"
            android:layout_width="match_parent"
            android:layout_height="match_parent" />
    </LinearLayout>

    <!-- Navigation drawer -->
    <com.google.android.material.navigation.NavigationView
        android:id="@+id/navigationView"
        android:layout_width="wrap_content"
        android:layout_height="match_parent"
        android:layout_gravity="start"
        android:fitsSystemWindows="true"
        app:menu="@menu/nav_menu"
        app:headerLayout="@layout/nav_header" />

</androidx.drawerlayout.widget.DrawerLayout>
```

- [ ] **Step 3: Create nav_header layout**

Create `app/src/main/res/layout/nav_header.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:padding="16dp"
    android:background="?colorPrimary">

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="@string/app_name"
        android:textSize="24sp"
        android:textStyle="bold"
        android:textColor="@android:color/white" />

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="@string/subtitle"
        android:textSize="13sp"
        android:textColor="@android:color/white"
        android:alpha="0.8"
        android:layout_marginTop="4dp" />
</LinearLayout>
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/layout/activity_main.xml \
       app/src/main/res/menu/nav_menu.xml \
       app/src/main/res/layout/nav_header.xml
git commit -m "feat: add DrawerLayout with navigation menu to activity_main"
```

---

## Task 8: Fragment layouts

**Files:**
- Create: `app/src/main/res/layout/fragment_home.xml`
- Create: `app/src/main/res/layout/fragment_settings.xml`
- Create: `app/src/main/res/layout/fragment_location_log.xml`
- Create: `app/src/main/res/layout/fragment_privacy.xml`

- [ ] **Step 1: Create fragment_home.xml**

Contains: service toggle card, permissions card, VIP contacts RecyclerView, mute timer card, FAB.

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.coordinatorlayout.widget.CoordinatorLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <androidx.core.widget.NestedScrollView
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:fillViewport="true"
        android:clipToPadding="false"
        android:paddingBottom="72dp">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:padding="16dp">

            <!-- Service Toggle -->
            <com.google.android.material.card.MaterialCardView
                android:id="@+id/cardService"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                app:cardElevation="2dp"
                app:cardCornerRadius="12dp">

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="horizontal"
                    android:gravity="center_vertical"
                    android:padding="16dp">

                    <TextView
                        android:layout_width="0dp"
                        android:layout_height="wrap_content"
                        android:layout_weight="1"
                        android:text="@string/service_label"
                        android:textSize="18sp"
                        android:textStyle="bold" />

                    <com.google.android.material.materialswitch.MaterialSwitch
                        android:id="@+id/switchService"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content" />
                </LinearLayout>
            </com.google.android.material.card.MaterialCardView>

            <!-- Mute Timer -->
            <com.google.android.material.card.MaterialCardView
                android:id="@+id/cardMuteTimer"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="12dp"
                app:cardElevation="2dp"
                app:cardCornerRadius="12dp">

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="vertical"
                    android:padding="16dp">

                    <com.google.android.material.button.MaterialButton
                        android:id="@+id/btnMuteTimer"
                        style="@style/Widget.Material3.Button.TonalButton"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:text="@string/mute_button" />

                    <TextView
                        android:id="@+id/tvMuteCountdown"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:textSize="13sp"
                        android:textColor="?colorPrimary"
                        android:gravity="center"
                        android:visibility="gone"
                        android:layout_marginTop="8dp" />
                </LinearLayout>
            </com.google.android.material.card.MaterialCardView>

            <!-- Permissions -->
            <com.google.android.material.card.MaterialCardView
                android:id="@+id/cardPermissions"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="12dp"
                app:cardElevation="2dp"
                app:cardCornerRadius="12dp">

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="vertical"
                    android:padding="16dp">

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="@string/permissions_title"
                        android:textSize="16sp"
                        android:textStyle="bold"
                        android:layout_marginBottom="12dp" />

                    <!-- Runtime permissions row -->
                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:orientation="horizontal"
                        android:gravity="center_vertical"
                        android:layout_marginBottom="8dp">

                        <LinearLayout
                            android:layout_width="0dp"
                            android:layout_height="wrap_content"
                            android:layout_weight="1"
                            android:orientation="vertical">

                            <TextView
                                android:layout_width="wrap_content"
                                android:layout_height="wrap_content"
                                android:text="@string/runtime_perm_label"
                                android:textSize="14sp" />

                            <TextView
                                android:id="@+id/tvRuntimeStatus"
                                android:layout_width="wrap_content"
                                android:layout_height="wrap_content"
                                android:textSize="12sp" />
                        </LinearLayout>

                        <com.google.android.material.button.MaterialButton
                            android:id="@+id/btnRequestRuntime"
                            style="@style/Widget.Material3.Button.TonalButton"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="@string/authorize"
                            android:textSize="12sp" />
                    </LinearLayout>

                    <!-- DND permission row -->
                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:orientation="horizontal"
                        android:gravity="center_vertical">

                        <LinearLayout
                            android:layout_width="0dp"
                            android:layout_height="wrap_content"
                            android:layout_weight="1"
                            android:orientation="vertical">

                            <TextView
                                android:layout_width="wrap_content"
                                android:layout_height="wrap_content"
                                android:text="@string/dnd_perm_label"
                                android:textSize="14sp" />

                            <TextView
                                android:id="@+id/tvDndStatus"
                                android:layout_width="wrap_content"
                                android:layout_height="wrap_content"
                                android:textSize="12sp" />
                        </LinearLayout>

                        <com.google.android.material.button.MaterialButton
                            android:id="@+id/btnRequestDnd"
                            style="@style/Widget.Material3.Button.TonalButton"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="@string/configure"
                            android:textSize="12sp" />
                    </LinearLayout>
                </LinearLayout>
            </com.google.android.material.card.MaterialCardView>

            <!-- VIP Contacts header -->
            <TextView
                android:id="@+id/tvContactsTitle"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="@string/contacts_title"
                android:textSize="16sp"
                android:textStyle="bold"
                android:layout_marginTop="20dp" />

            <!-- Contacts list -->
            <androidx.recyclerview.widget.RecyclerView
                android:id="@+id/rvContacts"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="8dp"
                android:nestedScrollingEnabled="false" />
        </LinearLayout>
    </androidx.core.widget.NestedScrollView>

    <!-- FAB -->
    <com.google.android.material.floatingactionbutton.FloatingActionButton
        android:id="@+id/fabAdd"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_margin="16dp"
        android:contentDescription="@string/add_contact"
        app:srcCompat="@android:drawable/ic_input_add"
        android:layout_gravity="bottom|end" />

</androidx.coordinatorlayout.widget.CoordinatorLayout>
```

- [ ] **Step 2: Create fragment_settings.xml**

Contains: volume slider, sound type radio, quiet hours.

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.core.widget.NestedScrollView
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="16dp">

        <!-- Volume Slider -->
        <com.google.android.material.card.MaterialCardView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            app:cardElevation="2dp"
            app:cardCornerRadius="12dp">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:padding="16dp">

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="horizontal"
                    android:gravity="center_vertical">

                    <TextView
                        android:layout_width="0dp"
                        android:layout_height="wrap_content"
                        android:layout_weight="1"
                        android:text="@string/volume_label"
                        android:textSize="16sp"
                        android:textStyle="bold" />

                    <TextView
                        android:id="@+id/tvVolumeValue"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="100%"
                        android:textSize="16sp"
                        android:textStyle="bold"
                        android:textColor="?colorPrimary" />
                </LinearLayout>

                <com.google.android.material.slider.Slider
                    android:id="@+id/sliderVolume"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:valueFrom="25"
                    android:valueTo="100"
                    android:stepSize="5"
                    android:layout_marginTop="4dp" />
            </LinearLayout>
        </com.google.android.material.card.MaterialCardView>

        <!-- Sound Type -->
        <com.google.android.material.card.MaterialCardView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="12dp"
            app:cardElevation="2dp"
            app:cardCornerRadius="12dp">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:padding="16dp">

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/settings_sound_title"
                    android:textSize="16sp"
                    android:textStyle="bold"
                    android:layout_marginBottom="8dp" />

                <RadioGroup
                    android:id="@+id/rgSoundType"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content">

                    <com.google.android.material.radiobutton.MaterialRadioButton
                        android:id="@+id/rbRingtone"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:text="@string/settings_sound_ringtone" />

                    <com.google.android.material.radiobutton.MaterialRadioButton
                        android:id="@+id/rbNotification"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:text="@string/settings_sound_notification" />
                </RadioGroup>
            </LinearLayout>
        </com.google.android.material.card.MaterialCardView>

        <!-- Quiet Hours -->
        <com.google.android.material.card.MaterialCardView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="12dp"
            app:cardElevation="2dp"
            app:cardCornerRadius="12dp">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:padding="16dp">

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/quiet_hours_title"
                    android:textSize="16sp"
                    android:textStyle="bold" />

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/quiet_hours_subtitle"
                    android:textSize="12sp"
                    android:textColor="?android:textColorSecondary"
                    android:layout_marginBottom="8dp" />

                <LinearLayout
                    android:id="@+id/quietRulesContainer"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="vertical" />

                <com.google.android.material.button.MaterialButton
                    android:id="@+id/btnAddQuietRule"
                    style="@style/Widget.Material3.Button.TextButton"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/quiet_add_rule"
                    android:layout_gravity="center_horizontal"
                    android:layout_marginTop="4dp" />
            </LinearLayout>
        </com.google.android.material.card.MaterialCardView>

        <!-- Location Sharing (internal flavor) -->
        <com.google.android.material.card.MaterialCardView
            android:id="@+id/cardLocation"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="12dp"
            app:cardElevation="2dp"
            app:cardCornerRadius="12dp">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:padding="16dp">

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/location_title"
                    android:textSize="16sp"
                    android:textStyle="bold" />

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/location_subtitle"
                    android:textSize="12sp"
                    android:textColor="?android:textColorSecondary"
                    android:layout_marginBottom="12dp" />

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="horizontal"
                    android:gravity="center_vertical">

                    <com.google.android.material.textfield.TextInputLayout
                        android:layout_width="0dp"
                        android:layout_height="wrap_content"
                        android:layout_weight="1"
                        android:layout_marginEnd="8dp">

                        <com.google.android.material.textfield.TextInputEditText
                            android:id="@+id/etOwnNumber"
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:hint="@string/location_your_number_hint"
                            android:inputType="phone"
                            android:textSize="14sp" />
                    </com.google.android.material.textfield.TextInputLayout>

                    <com.google.android.material.button.MaterialButton
                        android:id="@+id/btnSaveNumber"
                        style="@style/Widget.Material3.Button.TonalButton"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="@string/location_save"
                        android:textSize="12sp" />
                </LinearLayout>

                <LinearLayout
                    android:id="@+id/layoutLocationStatus"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="horizontal"
                    android:gravity="center_vertical"
                    android:layout_marginTop="8dp"
                    android:visibility="gone">

                    <ImageView
                        android:id="@+id/ivLocationStatus"
                        android:layout_width="16dp"
                        android:layout_height="16dp"
                        android:layout_marginEnd="6dp" />

                    <TextView
                        android:id="@+id/tvLocationStatus"
                        android:layout_width="0dp"
                        android:layout_height="wrap_content"
                        android:layout_weight="1"
                        android:textSize="12sp" />
                </LinearLayout>

                <TextView
                    android:id="@+id/tvLocationServer"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:textSize="11sp"
                    android:textColor="?android:textColorSecondary"
                    android:layout_marginTop="4dp" />
            </LinearLayout>
        </com.google.android.material.card.MaterialCardView>

    </LinearLayout>
</androidx.core.widget.NestedScrollView>
```

- [ ] **Step 3: Create fragment_location_log.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="16dp">

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="@string/location_log_button"
        android:textSize="18sp"
        android:textStyle="bold"
        android:layout_marginBottom="16dp" />

    <ScrollView
        android:layout_width="match_parent"
        android:layout_height="match_parent">

        <LinearLayout
            android:id="@+id/locationLogContainer"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical" />
    </ScrollView>
</LinearLayout>
```

- [ ] **Step 4: Create fragment_privacy.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<ScrollView
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="16dp">

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/privacy_title"
            android:textSize="22sp"
            android:textStyle="bold"
            android:layout_marginBottom="16dp" />

        <com.google.android.material.card.MaterialCardView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            app:cardElevation="2dp"
            app:cardCornerRadius="12dp"
            android:layout_marginBottom="12dp">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:padding="16dp">

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/privacy_data_title"
                    android:textSize="16sp"
                    android:textStyle="bold"
                    android:layout_marginBottom="8dp" />

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/privacy_data_body"
                    android:textSize="14sp"
                    android:lineSpacingExtra="2dp" />
            </LinearLayout>
        </com.google.android.material.card.MaterialCardView>

        <com.google.android.material.card.MaterialCardView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            app:cardElevation="2dp"
            app:cardCornerRadius="12dp"
            android:layout_marginBottom="12dp">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:padding="16dp">

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/privacy_license_title"
                    android:textSize="16sp"
                    android:textStyle="bold"
                    android:layout_marginBottom="8dp" />

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/privacy_license_body"
                    android:textSize="14sp"
                    android:lineSpacingExtra="2dp" />
            </LinearLayout>
        </com.google.android.material.card.MaterialCardView>

        <com.google.android.material.card.MaterialCardView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            app:cardElevation="2dp"
            app:cardCornerRadius="12dp">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:padding="16dp">

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/privacy_contact_title"
                    android:textSize="16sp"
                    android:textStyle="bold"
                    android:layout_marginBottom="8dp" />

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/privacy_contact_body"
                    android:textSize="14sp" />
            </LinearLayout>
        </com.google.android.material.card.MaterialCardView>
    </LinearLayout>
</ScrollView>
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/layout/fragment_home.xml \
       app/src/main/res/layout/fragment_settings.xml \
       app/src/main/res/layout/fragment_location_log.xml \
       app/src/main/res/layout/fragment_privacy.xml
git commit -m "feat: add fragment layouts for home, settings, location log, privacy"
```

---

## Task 9: HomeFragment.kt

**Files:**
- Create: `app/src/main/java/com/lorenzomarci/sosring/HomeFragment.kt`

- [ ] **Step 1: Create HomeFragment with service toggle, permissions, VIP list, mute timer**

```kotlin
package com.lorenzomarci.sosring

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.NumberPicker
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lorenzomarci.sosring.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var prefs: PrefsManager
    private lateinit var adapter: VipNumbersAdapter
    private val contacts = mutableListOf<VipContact>()

    private val countdownHandler = Handler(Looper.getMainLooper())
    private val countdownRunnable = object : Runnable {
        override fun run() {
            updateMuteTimerUI()
            if (prefs.isMuted) {
                countdownHandler.postDelayed(this, 60_000)
            }
        }
    }

    private val runtimePermissions = buildList {
        add(Manifest.permission.READ_PHONE_STATE)
        add(Manifest.permission.READ_CALL_LOG)
        add(Manifest.permission.READ_CONTACTS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (BuildConfig.LOCATION_ENABLED) {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }.toTypedArray()

    private val contactPickerLauncher = registerForActivityResult(
        ActivityResultContracts.PickContact()
    ) { uri: Uri? ->
        uri?.let { handleContactPicked(it) }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        updatePermissionStatus()
        val allGranted = results.values.all { it }
        if (allGranted) {
            Toast.makeText(requireContext(), getString(R.string.perm_all_granted), Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), getString(R.string.perm_some_missing), Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = PrefsManager(requireContext())
        setupRecyclerView()
        setupListeners()
        loadContacts()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
        binding.switchService.isChecked = prefs.isServiceEnabled
        loadContacts()
        updateMuteTimerUI()
        if (prefs.isMuted) {
            countdownHandler.post(countdownRunnable)
        }
    }

    override fun onPause() {
        super.onPause()
        countdownHandler.removeCallbacks(countdownRunnable)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        countdownHandler.removeCallbacks(countdownRunnable)
        _binding = null
    }

    private fun setupRecyclerView() {
        adapter = VipNumbersAdapter(
            onEdit = { position, contact -> showEditDialog(position, contact) },
            onDelete = { position -> deleteContact(position) },
            onLocation = if (BuildConfig.LOCATION_ENABLED) { contact ->
                requestContactLocation(contact)
            } else null,
            onRingtoneToggle = { position, contact ->
                val updated = contact.copy(ringtoneEnabled = !contact.ringtoneEnabled)
                contacts[position] = updated
                saveAndRefresh()
            }
        )
        binding.rvContacts.layoutManager = LinearLayoutManager(requireContext())
        binding.rvContacts.adapter = adapter
    }

    private fun setupListeners() {
        binding.switchService.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (checkAllPermissions()) {
                    startMonitoring()
                } else {
                    binding.switchService.isChecked = false
                    Toast.makeText(requireContext(), getString(R.string.grant_perms_first), Toast.LENGTH_LONG).show()
                }
            } else {
                stopMonitoring()
            }
        }

        binding.btnRequestRuntime.setOnClickListener {
            permissionLauncher.launch(runtimePermissions)
        }

        binding.btnRequestDnd.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.dnd_dialog_title))
                .setMessage(getString(R.string.dnd_dialog_msg))
                .setPositiveButton(getString(R.string.btn_open_settings)) { _, _ ->
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                }
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show()
        }

        binding.fabAdd.setOnClickListener {
            showAddChoiceDialog()
        }

        // Mute timer button
        binding.btnMuteTimer.setOnClickListener {
            if (prefs.isMuted) {
                // Cancel mute
                prefs.muteUntilTimestamp = 0L
                BootReceiver.cancelMuteAlarm(requireContext())
                Toast.makeText(requireContext(), getString(R.string.mute_cancelled), Toast.LENGTH_SHORT).show()
                updateMuteTimerUI()
                countdownHandler.removeCallbacks(countdownRunnable)
            } else {
                showMuteTimerDialog()
            }
        }
    }

    private fun showMuteTimerDialog() {
        val picker = NumberPicker(requireContext()).apply {
            minValue = 1
            maxValue = 12
            value = 1
            wrapSelectorWheel = false
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.mute_dialog_title))
            .setMessage(getString(R.string.mute_dialog_message))
            .setView(picker)
            .setPositiveButton(getString(R.string.btn_save)) { _, _ ->
                val hours = picker.value
                val muteUntil = System.currentTimeMillis() + hours * 3600_000L
                prefs.muteUntilTimestamp = muteUntil
                BootReceiver.scheduleMuteAlarm(requireContext(), muteUntil)
                Toast.makeText(requireContext(), getString(R.string.mute_activated, hours), Toast.LENGTH_SHORT).show()
                updateMuteTimerUI()
                countdownHandler.post(countdownRunnable)
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun updateMuteTimerUI() {
        if (prefs.isMuted) {
            val remaining = prefs.muteUntilTimestamp - System.currentTimeMillis()
            val hours = (remaining / 3600_000).toInt()
            val minutes = ((remaining % 3600_000) / 60_000).toInt()
            binding.tvMuteCountdown.text = getString(R.string.mute_countdown, hours, minutes)
            binding.tvMuteCountdown.visibility = View.VISIBLE
            binding.btnMuteTimer.text = getString(R.string.mute_button_cancel)
        } else {
            binding.tvMuteCountdown.visibility = View.GONE
            binding.btnMuteTimer.text = getString(R.string.mute_button)
        }
    }

    private fun loadContacts() {
        contacts.clear()
        contacts.addAll(prefs.getContacts())
        adapter.submitList(contacts.toList())
    }

    private fun requestContactLocation(contact: VipContact) {
        if (prefs.ownPhoneNumber.isBlank()) {
            Toast.makeText(requireContext(), getString(R.string.location_no_number), Toast.LENGTH_SHORT).show()
            return
        }
        val ntfyService = CallMonitorService.getInstance()?.ntfyService
        if (ntfyService != null) {
            ntfyService.requestLocation(contact)
        } else {
            Toast.makeText(requireContext(), getString(R.string.grant_perms_first), Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAddChoiceDialog() {
        val options = arrayOf(getString(R.string.choice_from_contacts), getString(R.string.choice_manual))
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.add_choice_title))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> pickFromContacts()
                    1 -> showManualAddDialog()
                }
            }
            .show()
    }

    private fun pickFromContacts() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS))
            return
        }
        contactPickerLauncher.launch(null)
    }

    private fun handleContactPicked(contactUri: Uri) {
        var name = ""
        var phone = ""
        val ctx = requireContext()

        val nameCursor: Cursor? = ctx.contentResolver.query(
            contactUri, arrayOf(ContactsContract.Contacts.DISPLAY_NAME, ContactsContract.Contacts._ID),
            null, null, null
        )
        nameCursor?.use {
            if (it.moveToFirst()) {
                name = it.getString(it.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME)) ?: ""
                val contactId = it.getString(it.getColumnIndexOrThrow(ContactsContract.Contacts._ID))
                val phoneCursor: Cursor? = ctx.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                    "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                    arrayOf(contactId), null
                )
                phoneCursor?.use { pc ->
                    if (pc.moveToFirst()) {
                        phone = pc.getString(pc.getColumnIndexOrThrow(
                            ContactsContract.CommonDataKinds.Phone.NUMBER)) ?: ""
                    }
                }
            }
        }

        if (phone.isEmpty()) {
            Toast.makeText(ctx, getString(R.string.no_phone_found), Toast.LENGTH_LONG).show()
            return
        }

        val view = LayoutInflater.from(ctx).inflate(R.layout.dialog_add_number, null)
        val etName = view.findViewById<EditText>(R.id.etDialogName)
        val etNumber = view.findViewById<EditText>(R.id.etDialogNumber)
        etName.setText(name)
        etNumber.setText(phone)

        MaterialAlertDialogBuilder(ctx)
            .setTitle(getString(R.string.confirm_contact_title))
            .setView(view)
            .setPositiveButton(getString(R.string.btn_add)) { _, _ ->
                val finalName = etName.text.toString().trim()
                val finalNumber = etNumber.text.toString().trim()
                if (finalName.isNotEmpty() && finalNumber.length > 3) {
                    contacts.add(VipContact(finalName, finalNumber))
                    saveAndRefresh()
                }
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun showManualAddDialog() {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_number, null)
        val etName = view.findViewById<EditText>(R.id.etDialogName)
        val etNumber = view.findViewById<EditText>(R.id.etDialogNumber)
        etNumber.setText("+")

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.add_choice_title))
            .setView(view)
            .setPositiveButton(getString(R.string.btn_add)) { _, _ ->
                val name = etName.text.toString().trim()
                val number = etNumber.text.toString().trim()
                if (name.isNotEmpty() && number.length > 3) {
                    contacts.add(VipContact(name, number))
                    saveAndRefresh()
                }
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun showEditDialog(position: Int, contact: VipContact) {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_number, null)
        val etName = view.findViewById<EditText>(R.id.etDialogName)
        val etNumber = view.findViewById<EditText>(R.id.etDialogNumber)
        etName.setText(contact.name)
        etNumber.setText(contact.number)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.edit_contact_title))
            .setView(view)
            .setPositiveButton(getString(R.string.btn_save)) { _, _ ->
                val name = etName.text.toString().trim()
                val number = etNumber.text.toString().trim()
                if (name.isNotEmpty() && number.length > 3) {
                    contacts[position] = contact.copy(name = name, number = number)
                    saveAndRefresh()
                }
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun deleteContact(position: Int) {
        val contact = contacts[position]
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.remove_contact_title))
            .setMessage(getString(R.string.remove_contact_msg, contact.name, contact.number))
            .setPositiveButton(getString(R.string.btn_remove)) { _, _ ->
                contacts.removeAt(position)
                saveAndRefresh()
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun saveAndRefresh() {
        prefs.saveContacts(contacts)
        adapter.submitList(contacts.toList())
    }

    private fun startMonitoring() {
        prefs.isServiceEnabled = true
        CallMonitorService.start(requireContext())
        Toast.makeText(requireContext(), getString(R.string.monitoring_on), Toast.LENGTH_SHORT).show()
    }

    private fun stopMonitoring() {
        prefs.isServiceEnabled = false
        CallMonitorService.stop(requireContext())
        Toast.makeText(requireContext(), getString(R.string.monitoring_off), Toast.LENGTH_SHORT).show()
    }

    private fun checkAllPermissions(): Boolean {
        val ctx = requireContext()
        val runtimeOk = runtimePermissions.all {
            ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED
        }
        val dndOk = (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .isNotificationPolicyAccessGranted
        return runtimeOk && dndOk
    }

    private fun updatePermissionStatus() {
        val ctx = requireContext()
        val phoneOk = ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_PHONE_STATE) ==
                PackageManager.PERMISSION_GRANTED
        val callLogOk = ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALL_LOG) ==
                PackageManager.PERMISSION_GRANTED
        val notifOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
        } else true
        val dndOk = (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .isNotificationPolicyAccessGranted

        val runtimeAll = phoneOk && callLogOk && notifOk

        binding.tvRuntimeStatus.text = getString(if (runtimeAll) R.string.status_granted else R.string.status_missing)
        binding.tvRuntimeStatus.setTextColor(ctx.getColor(if (runtimeAll) R.color.status_ok else R.color.status_missing))
        binding.btnRequestRuntime.isEnabled = !runtimeAll

        binding.tvDndStatus.text = getString(if (dndOk) R.string.status_granted else R.string.status_missing)
        binding.tvDndStatus.setTextColor(ctx.getColor(if (dndOk) R.color.status_ok else R.color.status_missing))
        binding.btnRequestDnd.isEnabled = !dndOk

        val allOk = runtimeAll && dndOk
        binding.switchService.isEnabled = allOk || prefs.isServiceEnabled
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/lorenzomarci/sosring/HomeFragment.kt
git commit -m "feat: create HomeFragment with service toggle, VIP list, mute timer, permissions"
```

---

## Task 10: SettingsFragment.kt

**Files:**
- Create: `app/src/main/java/com/lorenzomarci/sosring/SettingsFragment.kt`

- [ ] **Step 1: Create SettingsFragment with volume, sound type, quiet hours, location sharing**

```kotlin
package com.lorenzomarci.sosring

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.lorenzomarci.sosring.databinding.FragmentSettingsBinding
import java.util.Calendar

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var prefs: PrefsManager
    private val quietRules = mutableListOf<QuietRule>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = PrefsManager(requireContext())
        setupVolumeSlider()
        setupSoundType()
        setupQuietHours()
        setupLocationSharing()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupVolumeSlider() {
        binding.sliderVolume.value = prefs.volumePercent.toFloat()
        binding.tvVolumeValue.text = "${prefs.volumePercent}%"
        binding.sliderVolume.addOnChangeListener { _, value, _ ->
            prefs.volumePercent = value.toInt()
            binding.tvVolumeValue.text = "${value.toInt()}%"
        }
    }

    private fun setupSoundType() {
        when (prefs.overrideSoundType) {
            PrefsManager.SOUND_TYPE_NOTIFICATION -> binding.rbNotification.isChecked = true
            else -> binding.rbRingtone.isChecked = true
        }

        binding.rgSoundType.setOnCheckedChangeListener { _, checkedId ->
            prefs.overrideSoundType = when (checkedId) {
                R.id.rbNotification -> PrefsManager.SOUND_TYPE_NOTIFICATION
                else -> PrefsManager.SOUND_TYPE_RINGTONE
            }
        }
    }

    private fun setupQuietHours() {
        loadQuietRules()

        binding.btnAddQuietRule.setOnClickListener {
            if (quietRules.size >= PrefsManager.MAX_QUIET_RULES) {
                Toast.makeText(requireContext(), getString(R.string.quiet_max_rules), Toast.LENGTH_SHORT).show()
            } else {
                showAddQuietRuleDialog()
            }
        }
    }

    private fun setupLocationSharing() {
        if (BuildConfig.LOCATION_ENABLED) {
            binding.cardLocation.visibility = View.VISIBLE
            binding.tvLocationServer.text = getString(R.string.location_server_label, prefs.ntfyServerUrl)
            updateLocationNumberUI()

            binding.btnSaveNumber.setOnClickListener {
                if (prefs.ownPhoneNumber.isNotBlank() && !binding.etOwnNumber.isEnabled) {
                    binding.etOwnNumber.isEnabled = true
                    binding.etOwnNumber.requestFocus()
                    binding.btnSaveNumber.text = getString(R.string.location_save)
                } else {
                    val number = binding.etOwnNumber.text.toString().trim()
                    if (number.startsWith("+") && number.length >= 10) {
                        prefs.ownPhoneNumber = number
                        Toast.makeText(requireContext(), getString(R.string.location_number_saved), Toast.LENGTH_SHORT).show()
                        updateLocationNumberUI()
                        if (prefs.isServiceEnabled) {
                            CallMonitorService.stop(requireContext())
                            CallMonitorService.start(requireContext())
                        }
                        checkNtfyHealth()
                    } else {
                        Toast.makeText(requireContext(), getString(R.string.location_number_invalid), Toast.LENGTH_LONG).show()
                    }
                }
            }
        } else {
            binding.cardLocation.visibility = View.GONE
        }
    }

    private fun updateLocationNumberUI() {
        val saved = prefs.ownPhoneNumber
        if (saved.isNotBlank()) {
            binding.etOwnNumber.setText(saved)
            binding.etOwnNumber.isEnabled = false
            binding.btnSaveNumber.text = getString(R.string.location_edit)
            checkNtfyHealth()
        } else {
            binding.etOwnNumber.setText("")
            binding.etOwnNumber.isEnabled = true
            binding.btnSaveNumber.text = getString(R.string.location_save)
            binding.layoutLocationStatus.visibility = View.GONE
        }
    }

    private fun checkNtfyHealth() {
        binding.layoutLocationStatus.visibility = View.VISIBLE
        binding.tvLocationStatus.text = getString(R.string.location_status_checking)
        binding.ivLocationStatus.setImageResource(android.R.drawable.ic_popup_sync)

        Thread {
            try {
                val url = "${prefs.ntfyServerUrl}/v1/health"
                val request = okhttp3.Request.Builder().url(url).build()
                val response = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                    .newCall(request).execute()
                val healthy = response.isSuccessful
                response.close()
                activity?.runOnUiThread {
                    if (_binding == null) return@runOnUiThread
                    if (healthy) {
                        binding.ivLocationStatus.setImageResource(android.R.drawable.presence_online)
                        binding.tvLocationStatus.text = getString(R.string.location_status_ok)
                        binding.tvLocationStatus.setTextColor(requireContext().getColor(R.color.status_ok))
                    } else {
                        binding.ivLocationStatus.setImageResource(android.R.drawable.presence_busy)
                        binding.tvLocationStatus.text = getString(R.string.location_status_fail)
                        binding.tvLocationStatus.setTextColor(requireContext().getColor(R.color.status_missing))
                    }
                }
            } catch (e: Exception) {
                activity?.runOnUiThread {
                    if (_binding == null) return@runOnUiThread
                    binding.ivLocationStatus.setImageResource(android.R.drawable.presence_busy)
                    binding.tvLocationStatus.text = getString(R.string.location_status_fail)
                    binding.tvLocationStatus.setTextColor(requireContext().getColor(R.color.status_missing))
                }
            }
        }.start()
    }

    private fun loadQuietRules() {
        quietRules.clear()
        quietRules.addAll(prefs.getQuietRules())
        refreshQuietRulesUI()
    }

    private fun refreshQuietRulesUI() {
        val container = binding.quietRulesContainer
        container.removeAllViews()

        quietRules.forEachIndexed { index, rule ->
            val itemView = LayoutInflater.from(requireContext()).inflate(R.layout.item_quiet_rule, container, false)
            val tvDays = itemView.findViewById<TextView>(R.id.tvRuleDays)
            val tvTime = itemView.findViewById<TextView>(R.id.tvRuleTime)
            val btnDelete = itemView.findViewById<ImageButton>(R.id.btnDeleteRule)

            tvDays.text = formatRuleDays(rule)
            tvTime.text = formatRuleTime(rule)
            btnDelete.setOnClickListener { deleteQuietRule(index, rule) }
            container.addView(itemView)
        }

        binding.btnAddQuietRule.isEnabled = quietRules.size < PrefsManager.MAX_QUIET_RULES
    }

    private fun formatRuleDays(rule: QuietRule): String {
        if (rule.days.size == 7) return getString(R.string.quiet_every_day)
        val dayNames = mapOf(
            Calendar.MONDAY to getString(R.string.day_mon),
            Calendar.TUESDAY to getString(R.string.day_tue),
            Calendar.WEDNESDAY to getString(R.string.day_wed),
            Calendar.THURSDAY to getString(R.string.day_thu),
            Calendar.FRIDAY to getString(R.string.day_fri),
            Calendar.SATURDAY to getString(R.string.day_sat),
            Calendar.SUNDAY to getString(R.string.day_sun)
        )
        val orderedDays = listOf(
            Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
            Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY
        )
        val sorted = orderedDays.filter { it in rule.days }
        if (sorted.size >= 2) {
            val first = orderedDays.indexOf(sorted.first())
            val last = orderedDays.indexOf(sorted.last())
            if (last - first + 1 == sorted.size) {
                return "${dayNames[sorted.first()]}-${dayNames[sorted.last()]}"
            }
        }
        return sorted.mapNotNull { dayNames[it] }.joinToString(", ")
    }

    private fun formatRuleTime(rule: QuietRule): String {
        val from = String.format("%02d:%02d", rule.startHour, rule.startMinute)
        val to = String.format("%02d:%02d", rule.endHour, rule.endMinute)
        val crossMidnight = rule.endHour * 60 + rule.endMinute <= rule.startHour * 60 + rule.startMinute
        return if (crossMidnight) "$from - $to ${getString(R.string.quiet_next_day)}" else "$from - $to"
    }

    private fun deleteQuietRule(index: Int, rule: QuietRule) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.quiet_delete_title))
            .setMessage(getString(R.string.quiet_delete_msg,
                formatRuleDays(rule),
                String.format("%02d:%02d", rule.startHour, rule.startMinute),
                String.format("%02d:%02d", rule.endHour, rule.endMinute)))
            .setPositiveButton(getString(R.string.btn_remove)) { _, _ ->
                quietRules.removeAt(index)
                prefs.saveQuietRules(quietRules)
                refreshQuietRulesUI()
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun showAddQuietRuleDialog() {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_quiet_rule, null)
        val chipMap = mapOf(
            Calendar.MONDAY to view.findViewById<Chip>(R.id.chipMon),
            Calendar.TUESDAY to view.findViewById<Chip>(R.id.chipTue),
            Calendar.WEDNESDAY to view.findViewById<Chip>(R.id.chipWed),
            Calendar.THURSDAY to view.findViewById<Chip>(R.id.chipThu),
            Calendar.FRIDAY to view.findViewById<Chip>(R.id.chipFri),
            Calendar.SATURDAY to view.findViewById<Chip>(R.id.chipSat),
            Calendar.SUNDAY to view.findViewById<Chip>(R.id.chipSun)
        )
        val btnFrom = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnFromTime)
        val btnTo = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnToTime)
        val tvHint = view.findViewById<TextView>(R.id.tvCrossMidnightHint)
        var fromHour = 9; var fromMinute = 0
        var toHour = 18; var toMinute = 0

        fun updateHint() {
            val startMin = fromHour * 60 + fromMinute
            val endMin = toHour * 60 + toMinute
            tvHint.visibility = if (endMin <= startMin) View.VISIBLE else View.GONE
        }

        btnFrom.setOnClickListener {
            MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setHour(fromHour).setMinute(fromMinute)
                .setTitleText(getString(R.string.quiet_from))
                .build().apply {
                    addOnPositiveButtonClickListener {
                        fromHour = hour; fromMinute = minute
                        btnFrom.text = String.format("%02d:%02d", hour, minute)
                        updateHint()
                    }
                }.show(childFragmentManager, "from_time")
        }

        btnTo.setOnClickListener {
            MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setHour(toHour).setMinute(toMinute)
                .setTitleText(getString(R.string.quiet_to))
                .build().apply {
                    addOnPositiveButtonClickListener {
                        toHour = hour; toMinute = minute
                        btnTo.text = String.format("%02d:%02d", hour, minute)
                        updateHint()
                    }
                }.show(childFragmentManager, "to_time")
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.quiet_new_rule_title))
            .setView(view)
            .setPositiveButton(getString(R.string.btn_save)) { _, _ ->
                val selectedDays = chipMap.filter { it.value.isChecked }.keys
                if (selectedDays.isEmpty()) {
                    Toast.makeText(requireContext(), getString(R.string.quiet_select_day), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val rule = QuietRule(selectedDays, fromHour, fromMinute, toHour, toMinute)
                quietRules.add(rule)
                prefs.saveQuietRules(quietRules)
                refreshQuietRulesUI()
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/lorenzomarci/sosring/SettingsFragment.kt
git commit -m "feat: create SettingsFragment with volume, sound type, quiet hours, location config"
```

---

## Task 11: LocationLogFragment.kt + PrivacyFragment.kt

**Files:**
- Create: `app/src/main/java/com/lorenzomarci/sosring/LocationLogFragment.kt`
- Create: `app/src/main/java/com/lorenzomarci/sosring/PrivacyFragment.kt`

- [ ] **Step 1: Create LocationLogFragment**

```kotlin
package com.lorenzomarci.sosring

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.lorenzomarci.sosring.databinding.FragmentLocationLogBinding

class LocationLogFragment : Fragment() {

    private var _binding: FragmentLocationLogBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLocationLogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadLogs()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun loadLogs() {
        val prefs = PrefsManager(requireContext())
        val container = binding.locationLogContainer
        container.removeAllViews()

        val logs = prefs.getLocationLogs().filter { it.type == "incoming" }
        val dateFormat = java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault())

        if (logs.isEmpty()) {
            val empty = TextView(requireContext()).apply {
                text = getString(R.string.location_log_empty)
                textSize = 14f
                setTextColor(requireContext().getColor(android.R.color.darker_gray))
                setPadding(0, 16, 0, 16)
            }
            container.addView(empty)
        } else {
            logs.take(50).forEach { entry ->
                val date = dateFormat.format(java.util.Date(entry.timestamp))
                val tv = TextView(requireContext()).apply {
                    text = "\u2B07 ${getString(R.string.location_log_incoming, entry.name)}\n     $date"
                    textSize = 14f
                    setPadding(0, 8, 0, 8)
                }
                container.addView(tv)
            }
        }
    }
}
```

- [ ] **Step 2: Create PrivacyFragment**

```kotlin
package com.lorenzomarci.sosring

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.lorenzomarci.sosring.databinding.FragmentPrivacyBinding

class PrivacyFragment : Fragment() {

    private var _binding: FragmentPrivacyBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPrivacyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/lorenzomarci/sosring/LocationLogFragment.kt \
       app/src/main/java/com/lorenzomarci/sosring/PrivacyFragment.kt
git commit -m "feat: create LocationLogFragment and PrivacyFragment"
```

---

## Task 12: Refactor MainActivity to drawer host

**Files:**
- Modify: `app/src/main/java/com/lorenzomarci/sosring/MainActivity.kt`

- [ ] **Step 1: Replace entire MainActivity with drawer navigation host**

```kotlin
package com.lorenzomarci.sosring

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.lorenzomarci.sosring.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: PrefsManager
    private lateinit var toggle: ActionBarDrawerToggle

    private val contactsUpdatedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Refresh current fragment if it's HomeFragment
            val current = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
            if (current is HomeFragment) {
                current.onResume()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PrefsManager(this)

        setSupportActionBar(binding.toolbar)
        toggle = ActionBarDrawerToggle(
            this, binding.drawerLayout, binding.toolbar,
            R.string.nav_home, R.string.nav_home
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        binding.navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> loadFragment(HomeFragment(), getString(R.string.nav_home))
                R.id.nav_settings -> loadFragment(SettingsFragment(), getString(R.string.nav_settings))
                R.id.nav_location_log -> loadFragment(LocationLogFragment(), getString(R.string.nav_location_log))
                R.id.nav_privacy -> loadFragment(PrivacyFragment(), getString(R.string.nav_privacy))
            }
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        // Load default fragment
        if (savedInstanceState == null) {
            loadFragment(HomeFragment(), getString(R.string.nav_home))
            binding.navigationView.setCheckedItem(R.id.nav_home)
        }

        handleUpdateIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        if (BuildConfig.LOCATION_ENABLED) {
            LocalBroadcastManager.getInstance(this).registerReceiver(
                contactsUpdatedReceiver,
                IntentFilter(NtfyService.ACTION_CONTACTS_UPDATED)
            )
            CallMonitorService.getInstance()?.ntfyService?.runDiscovery()
        }
        if (BuildConfig.UPDATE_URL.isNotBlank()) {
            UpdateChecker(this).checkAndNotify()
        }
    }

    override fun onPause() {
        super.onPause()
        if (BuildConfig.LOCATION_ENABLED) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(contactsUpdatedReceiver)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleUpdateIntent(intent)
    }

    private fun handleUpdateIntent(intent: Intent?) {
        if (intent?.action == "com.lorenzomarci.sosring.ACTION_DOWNLOAD_UPDATE") {
            val apkUrl = intent.getStringExtra("apk_url") ?: return
            Toast.makeText(this, getString(R.string.update_downloading), Toast.LENGTH_SHORT).show()
            UpdateChecker(this).downloadAndInstall(apkUrl)
        }
    }

    private fun loadFragment(fragment: androidx.fragment.app.Fragment, title: String) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
        supportActionBar?.title = title
    }

    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            val current = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
            if (current !is HomeFragment) {
                loadFragment(HomeFragment(), getString(R.string.nav_home))
                binding.navigationView.setCheckedItem(R.id.nav_home)
            } else {
                @Suppress("DEPRECATION")
                super.onBackPressed()
            }
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/lorenzomarci/sosring/MainActivity.kt
git commit -m "refactor: strip MainActivity to DrawerLayout navigation host"
```

---

## Task 13: Version bump + build verification

**Files:**
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Bump version to 2.0**

In `build.gradle.kts`, change:
```kotlin
// build.gradle.kts lines 20-21
versionCode = 4
versionName = "2.0"
```

- [ ] **Step 2: Build and verify**

Run:
```bash
cd /c/Users/Admin/AndroidStudioProjects/SOSRing && ./gradlew assembleInternalDebug 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Fix any compilation errors**

If the build fails, fix the errors based on the output. Common issues:
- Missing imports
- ViewBinding field names not matching layout IDs
- Type mismatches

- [ ] **Step 4: Commit**

```bash
git add app/build.gradle.kts
git commit -m "chore: bump version to v2.0 (versionCode 4)"
```

---

## Task 14: Update README.md

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Read current README**

Read the existing README.md to understand the current structure.

- [ ] **Step 2: Update README with v2.0 features**

Add a v2.0 section to the changelog/features area listing:
- Fix: no more double ringtone when phone is already in normal mode
- Fix: volume slider now correctly controls MediaPlayer volume (range 25-100%)
- New: temporary mute timer (1-12 hours) — disables ringtone override while keeping GPS active
- New: choose override sound type (ringtone or notification)
- New: per-contact ringtone toggle — disable override for specific VIP contacts while keeping GPS
- New: navigation drawer with Home, Settings, Location Log, Privacy & Licenses sections
- New: Privacy & licenses page

- [ ] **Step 3: Commit**

```bash
git add README.md
git commit -m "docs: update README with v2.0 features"
```
