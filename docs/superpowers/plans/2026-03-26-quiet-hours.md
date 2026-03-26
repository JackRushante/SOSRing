# Quiet Hours Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add time-based quiet rules that suppress SOS Ring's audio override during user-defined day+time windows.

**Architecture:** QuietRule data class + JSON serialization in PrefsManager (same pattern as VipContact). isInQuietPeriod() check inserted before overrideAudio() in CallMonitorService. New UI card in MainActivity with add/delete dialog.

**Tech Stack:** Kotlin, SharedPreferences + JSONArray, Material Components (Chip, MaterialTimePicker, CardView), ViewBinding.

---

## File Map

| File | Action | Responsibility |
|------|--------|---------------|
| `app/src/main/java/com/lorenzomarci/sosring/PrefsManager.kt` | Modify | + QuietRule data class, serialization, isInQuietPeriod() |
| `app/src/main/java/com/lorenzomarci/sosring/CallMonitorService.kt` | Modify | + 1 line quiet period check |
| `app/src/main/java/com/lorenzomarci/sosring/MainActivity.kt` | Modify | + quiet hours UI section, add/delete logic |
| `app/src/main/res/layout/activity_main.xml` | Modify | + quiet hours card between volume and permissions |
| `app/src/main/res/layout/item_quiet_rule.xml` | Create | Rule item layout |
| `app/src/main/res/layout/dialog_quiet_rule.xml` | Create | Add rule dialog layout |
| `app/src/main/res/values/strings.xml` | Modify | + English quiet hours strings |
| `app/src/main/res/values-it/strings.xml` | Modify | + Italian quiet hours strings |

---

### Task 1: Add i18n strings

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-it/strings.xml`

- [ ] **Step 1: Add English strings**

Add before the closing `</resources>` tag in `app/src/main/res/values/strings.xml`:

```xml
    <!-- Quiet Hours -->
    <string name="quiet_hours_title">Quiet Hours</string>
    <string name="quiet_hours_subtitle">SOS Ring pauses during these times</string>
    <string name="quiet_add_rule">Add quiet rule</string>
    <string name="quiet_new_rule_title">New quiet rule</string>
    <string name="quiet_from">From</string>
    <string name="quiet_to">To</string>
    <string name="quiet_days_label">Days</string>
    <string name="quiet_next_day">(+1)</string>
    <string name="quiet_delete_title">Remove quiet rule</string>
    <string name="quiet_delete_msg">Remove this quiet rule?\n%1$s  %2$s - %3$s</string>
    <string name="quiet_max_rules">Maximum rules reached</string>
    <string name="quiet_select_day">Select at least one day</string>
    <string name="quiet_every_day">Every day</string>
    <string name="day_mon">Mon</string>
    <string name="day_tue">Tue</string>
    <string name="day_wed">Wed</string>
    <string name="day_thu">Thu</string>
    <string name="day_fri">Fri</string>
    <string name="day_sat">Sat</string>
    <string name="day_sun">Sun</string>
```

- [ ] **Step 2: Add Italian strings**

Add before the closing `</resources>` tag in `app/src/main/res/values-it/strings.xml`:

```xml
    <!-- Quiet Hours -->
    <string name="quiet_hours_title">Orari silenziosi</string>
    <string name="quiet_hours_subtitle">SOS Ring si mette in pausa in questi orari</string>
    <string name="quiet_add_rule">Aggiungi regola</string>
    <string name="quiet_new_rule_title">Nuova regola silenziosa</string>
    <string name="quiet_from">Dalle</string>
    <string name="quiet_to">Alle</string>
    <string name="quiet_days_label">Giorni</string>
    <string name="quiet_next_day">(+1)</string>
    <string name="quiet_delete_title">Rimuovi regola</string>
    <string name="quiet_delete_msg">Rimuovere questa regola?\n%1$s  %2$s - %3$s</string>
    <string name="quiet_max_rules">Numero massimo di regole raggiunto</string>
    <string name="quiet_select_day">Seleziona almeno un giorno</string>
    <string name="quiet_every_day">Ogni giorno</string>
    <string name="day_mon">Lun</string>
    <string name="day_tue">Mar</string>
    <string name="day_wed">Mer</string>
    <string name="day_thu">Gio</string>
    <string name="day_fri">Ven</string>
    <string name="day_sat">Sab</string>
    <string name="day_sun">Dom</string>
```

- [ ] **Step 3: Build to verify strings compile**

Run: `cd /c/Users/Admin/AndroidStudioProjects/SOSRing && ./gradlew assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/res/values-it/strings.xml
git commit -m "feat(quiet-hours): add i18n strings for en and it"
```

---

### Task 2: QuietRule data model and PrefsManager logic

**Files:**
- Modify: `app/src/main/java/com/lorenzomarci/sosring/PrefsManager.kt`

- [ ] **Step 1: Add QuietRule data class**

Add after the `VipContact` data class (line 8) in `PrefsManager.kt`:

```kotlin
data class QuietRule(
    val days: Set<Int>,    // Calendar.MONDAY(2)..SUNDAY(1)
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int
)
```

- [ ] **Step 2: Add companion object constants**

Add inside the `companion object` block, after `val DEFAULT_CONTACTS`:

```kotlin
        private const val KEY_QUIET_RULES = "quiet_rules"
        const val MAX_QUIET_RULES = 10
```

- [ ] **Step 3: Add getQuietRules()**

Add after the `normalizeNumber()` method:

```kotlin
    fun getQuietRules(): List<QuietRule> {
        val json = prefs.getString(KEY_QUIET_RULES, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                val daysArr = obj.getJSONArray("days")
                val days = (0 until daysArr.length()).map { daysArr.getInt(it) }.toSet()
                QuietRule(
                    days = days,
                    startHour = obj.getInt("startHour"),
                    startMinute = obj.getInt("startMinute"),
                    endHour = obj.getInt("endHour"),
                    endMinute = obj.getInt("endMinute")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
```

- [ ] **Step 4: Add saveQuietRules()**

Add after `getQuietRules()`:

```kotlin
    fun saveQuietRules(rules: List<QuietRule>) {
        val arr = JSONArray()
        rules.forEach { r ->
            arr.put(JSONObject().apply {
                put("days", JSONArray(r.days.toList()))
                put("startHour", r.startHour)
                put("startMinute", r.startMinute)
                put("endHour", r.endHour)
                put("endMinute", r.endMinute)
            })
        }
        prefs.edit().putString(KEY_QUIET_RULES, arr.toString()).apply()
    }
```

- [ ] **Step 5: Add isInQuietPeriod()**

Add after `saveQuietRules()`. This is the core logic. Needs `import java.util.Calendar` at top of file.

```kotlin
    fun isInQuietPeriod(): Boolean {
        val rules = getQuietRules()
        if (rules.isEmpty()) return false

        val cal = Calendar.getInstance()
        val currentDay = cal.get(Calendar.DAY_OF_WEEK)
        val currentTime = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

        val previousDay = if (currentDay == Calendar.SUNDAY) Calendar.SATURDAY
            else if (currentDay == Calendar.MONDAY) Calendar.SUNDAY
            else currentDay - 1

        return rules.any { rule ->
            val start = rule.startHour * 60 + rule.startMinute
            val end = rule.endHour * 60 + rule.endMinute

            if (end > start) {
                // Same-day rule: e.g. 09:00-18:00
                currentDay in rule.days && currentTime >= start && currentTime < end
            } else {
                // Cross-midnight rule: e.g. 22:00-06:00
                (currentDay in rule.days && currentTime >= start) ||
                (previousDay in rule.days && currentTime < end)
            }
        }
    }
```

- [ ] **Step 6: Add import**

Add at top of `PrefsManager.kt` with other imports:

```kotlin
import java.util.Calendar
```

- [ ] **Step 7: Build to verify**

Run: `cd /c/Users/Admin/AndroidStudioProjects/SOSRing && ./gradlew assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/lorenzomarci/sosring/PrefsManager.kt
git commit -m "feat(quiet-hours): add QuietRule model, serialization, isInQuietPeriod()"
```

---

### Task 3: Service integration

**Files:**
- Modify: `app/src/main/java/com/lorenzomarci/sosring/CallMonitorService.kt:71`

- [ ] **Step 1: Add quiet period check**

In `CallMonitorService.kt`, change line 71 from:

```kotlin
                    if (number != null && isVipNumber(number)) {
```

to:

```kotlin
                    if (number != null && isVipNumber(number) && !prefs.isInQuietPeriod()) {
```

- [ ] **Step 2: Build to verify**

Run: `cd /c/Users/Admin/AndroidStudioProjects/SOSRing && ./gradlew assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/lorenzomarci/sosring/CallMonitorService.kt
git commit -m "feat(quiet-hours): suppress override during quiet periods"
```

---

### Task 4: Create item_quiet_rule.xml layout

**Files:**
- Create: `app/src/main/res/layout/item_quiet_rule.xml`

- [ ] **Step 1: Create the layout file**

Create `app/src/main/res/layout/item_quiet_rule.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<com.google.android.material.card.MaterialCardView
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginTop="6dp"
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
                android:id="@+id/tvRuleDays"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:textSize="15sp"
                android:textStyle="bold" />

            <TextView
                android:id="@+id/tvRuleTime"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:textSize="14sp"
                android:textColor="?android:textColorSecondary" />
        </LinearLayout>

        <ImageButton
            android:id="@+id/btnDeleteRule"
            android:layout_width="40dp"
            android:layout_height="40dp"
            android:background="?selectableItemBackgroundBorderless"
            android:contentDescription="@string/btn_remove"
            android:src="@android:drawable/ic_menu_delete" />
    </LinearLayout>
</com.google.android.material.card.MaterialCardView>
```

- [ ] **Step 2: Build to verify**

Run: `cd /c/Users/Admin/AndroidStudioProjects/SOSRing && ./gradlew assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/layout/item_quiet_rule.xml
git commit -m "feat(quiet-hours): add rule item layout"
```

---

### Task 5: Create dialog_quiet_rule.xml layout

**Files:**
- Create: `app/src/main/res/layout/dialog_quiet_rule.xml`

- [ ] **Step 1: Create the dialog layout**

Create `app/src/main/res/layout/dialog_quiet_rule.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:padding="24dp">

    <!-- Days label -->
    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="@string/quiet_days_label"
        android:textSize="14sp"
        android:textStyle="bold"
        android:layout_marginBottom="8dp" />

    <!-- Day chips row -->
    <com.google.android.material.chip.ChipGroup
        android:id="@+id/chipGroupDays"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginBottom="16dp">

        <com.google.android.material.chip.Chip
            android:id="@+id/chipMon"
            style="@style/Widget.Material3.Chip.Filter"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/day_mon" />

        <com.google.android.material.chip.Chip
            android:id="@+id/chipTue"
            style="@style/Widget.Material3.Chip.Filter"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/day_tue" />

        <com.google.android.material.chip.Chip
            android:id="@+id/chipWed"
            style="@style/Widget.Material3.Chip.Filter"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/day_wed" />

        <com.google.android.material.chip.Chip
            android:id="@+id/chipThu"
            style="@style/Widget.Material3.Chip.Filter"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/day_thu" />

        <com.google.android.material.chip.Chip
            android:id="@+id/chipFri"
            style="@style/Widget.Material3.Chip.Filter"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/day_fri" />

        <com.google.android.material.chip.Chip
            android:id="@+id/chipSat"
            style="@style/Widget.Material3.Chip.Filter"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/day_sat" />

        <com.google.android.material.chip.Chip
            android:id="@+id/chipSun"
            style="@style/Widget.Material3.Chip.Filter"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/day_sun" />
    </com.google.android.material.chip.ChipGroup>

    <!-- Time row -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="center_vertical">

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/quiet_from"
            android:textSize="14sp" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/btnFromTime"
            style="@style/Widget.Material3.Button.OutlinedButton"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginStart="8dp"
            android:text="09:00"
            android:textSize="16sp" />

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/quiet_to"
            android:textSize="14sp"
            android:layout_marginStart="16dp" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/btnToTime"
            style="@style/Widget.Material3.Button.OutlinedButton"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginStart="8dp"
            android:text="18:00"
            android:textSize="16sp" />
    </LinearLayout>

    <!-- Cross-midnight hint -->
    <TextView
        android:id="@+id/tvCrossMidnightHint"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="@string/quiet_next_day"
        android:textSize="12sp"
        android:textColor="?colorPrimary"
        android:visibility="gone"
        android:layout_marginTop="4dp" />
</LinearLayout>
```

- [ ] **Step 2: Build to verify**

Run: `cd /c/Users/Admin/AndroidStudioProjects/SOSRing && ./gradlew assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/layout/dialog_quiet_rule.xml
git commit -m "feat(quiet-hours): add rule dialog layout with day chips and time pickers"
```

---

### Task 6: Add Quiet Hours card to activity_main.xml

**Files:**
- Modify: `app/src/main/res/layout/activity_main.xml:120-131`

- [ ] **Step 1: Insert Quiet Hours card**

In `activity_main.xml`, insert the following block between `cardVolume` (ends at line 119) and `cardPermissions` (starts at line 121). The new card goes right after `</com.google.android.material.card.MaterialCardView>` for cardVolume:

```xml
    <!-- Quiet Hours -->
    <com.google.android.material.card.MaterialCardView
        android:id="@+id/cardQuietHours"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_marginTop="12dp"
        app:cardElevation="2dp"
        app:cardCornerRadius="12dp"
        app:layout_constraintTop_toBottomOf="@id/cardVolume"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent">

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
```

- [ ] **Step 2: Update cardPermissions constraint**

Change `cardPermissions` constraint from:

```xml
        app:layout_constraintTop_toBottomOf="@id/cardVolume"
```

to:

```xml
        app:layout_constraintTop_toBottomOf="@id/cardQuietHours"
```

- [ ] **Step 3: Build to verify**

Run: `cd /c/Users/Admin/AndroidStudioProjects/SOSRing && ./gradlew assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/layout/activity_main.xml
git commit -m "feat(quiet-hours): add quiet hours card to main layout"
```

---

### Task 7: Wire up Quiet Hours UI in MainActivity

**Files:**
- Modify: `app/src/main/java/com/lorenzomarci/sosring/MainActivity.kt`

- [ ] **Step 1: Add imports**

Add to the imports section of `MainActivity.kt`:

```kotlin
import android.view.View
import android.widget.LinearLayout
import android.widget.ImageButton
import android.widget.TextView
import com.google.android.material.chip.Chip
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.util.Calendar
```

- [ ] **Step 2: Add quiet rules list field**

Add after `private val contacts = mutableListOf<VipContact>()` (line 29):

```kotlin
    private val quietRules = mutableListOf<QuietRule>()
```

- [ ] **Step 3: Load quiet rules in onCreate**

In `onCreate()`, add after `loadContacts()` (line 66):

```kotlin
        loadQuietRules()
```

- [ ] **Step 4: Add loadQuietRules() method**

Add after `loadContacts()` method:

```kotlin
    private fun loadQuietRules() {
        quietRules.clear()
        quietRules.addAll(prefs.getQuietRules())
        refreshQuietRulesUI()
    }

    private fun refreshQuietRulesUI() {
        val container = binding.quietRulesContainer
        container.removeAllViews()

        quietRules.forEachIndexed { index, rule ->
            val itemView = LayoutInflater.from(this).inflate(R.layout.item_quiet_rule, container, false)
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
```

- [ ] **Step 5: Add day formatting helpers**

Add after `refreshQuietRulesUI()`:

```kotlin
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

        // Try to compress consecutive runs
        if (sorted.size >= 2) {
            val first = orderedDays.indexOf(sorted.first())
            val last = orderedDays.indexOf(sorted.last())
            if (last - first + 1 == sorted.size) {
                // Consecutive run
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
```

- [ ] **Step 6: Add delete quiet rule**

Add after formatting helpers:

```kotlin
    private fun deleteQuietRule(index: Int, rule: QuietRule) {
        MaterialAlertDialogBuilder(this)
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
```

- [ ] **Step 7: Add show add quiet rule dialog**

Add after delete method:

```kotlin
    private fun showAddQuietRuleDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_quiet_rule, null)

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
                }.show(supportFragmentManager, "from_time")
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
                }.show(supportFragmentManager, "to_time")
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.quiet_new_rule_title))
            .setView(view)
            .setPositiveButton(getString(R.string.btn_save)) { _, _ ->
                val selectedDays = chipMap.filter { it.value.isChecked }.keys
                if (selectedDays.isEmpty()) {
                    Toast.makeText(this, getString(R.string.quiet_select_day), Toast.LENGTH_SHORT).show()
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
```

- [ ] **Step 8: Wire up add button in setupListeners()**

In `setupListeners()`, add before `binding.fabAdd.setOnClickListener` (line 122):

```kotlin
        binding.btnAddQuietRule.setOnClickListener {
            if (quietRules.size >= PrefsManager.MAX_QUIET_RULES) {
                Toast.makeText(this, getString(R.string.quiet_max_rules), Toast.LENGTH_SHORT).show()
            } else {
                showAddQuietRuleDialog()
            }
        }
```

- [ ] **Step 9: Build to verify**

Run: `cd /c/Users/Admin/AndroidStudioProjects/SOSRing && ./gradlew assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/lorenzomarci/sosring/MainActivity.kt
git commit -m "feat(quiet-hours): wire up quiet hours UI with add/delete dialog"
```

---

### Task 8: Final build and manual test

- [ ] **Step 1: Full release build**

Run: `cd /c/Users/Admin/AndroidStudioProjects/SOSRing && ./gradlew clean assembleRelease 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Manual test checklist**

Install on device and verify:
1. Quiet Hours card appears between Volume and Permissions
2. Tap "+ Add quiet rule" opens dialog with day chips and time pickers
3. Select days (e.g. Mon-Fri), set 09:00-18:00, save — rule appears in card
4. Cross-midnight: set 22:00-06:00 — "(+1)" hint appears in dialog and in rule display
5. Delete a rule — confirmation dialog, then removed
6. Add 10 rules — button disables, toast shows limit message
7. With a quiet rule active for current time: VIP call does NOT trigger override
8. Outside quiet period: VIP call triggers override normally

- [ ] **Step 3: Commit any fixes if needed**
