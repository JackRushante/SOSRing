# SOS Ring v2.0 — Design Spec

**Data:** 2026-03-30
**Versione target:** v2.0 (versionCode 4)
**Scope:** 6 nuove feature + 1 bugfix

---

## Feature 1 — Fix doppia suoneria

### Problema
Quando il telefono e' gia' in modalita' normale con suoneria attiva, `overrideAudio()` avvia un `MediaPlayer` su ALARM stream in parallelo alla suoneria di sistema, causando doppia suoneria.

### Soluzione
In `CallMonitorService.overrideAudio()`, prima di qualsiasi override, controllare `audioManager.ringerMode`:
- Se `RINGER_MODE_NORMAL` → **skip completo**: niente DND override, niente MediaPlayer, niente vibrazione, niente save/restore state
- Se `RINGER_MODE_SILENT` o `RINGER_MODE_VIBRATE` → override come ora

### File coinvolti
- `CallMonitorService.kt`: aggiungere guard clause all'inizio di `overrideAudio()`

---

## Feature 7 — Fix volume slider + range esteso

### Problema
Lo slider volume ha range 50-100%. L'utente vuole poter scendere fino al 25%.

### Modifiche
1. **PrefsManager.kt**: `MIN_VOLUME_PERCENT` da 50 a **25**
2. **MainActivity.kt** (poi `HomeFragment`): slider `valueFrom` da 50f a **25f**, step 5f confermato
3. **CallMonitorService.kt**: in `startRingtone()`, dopo `prepare()` e prima di `start()`, aggiungere:
   ```kotlin
   val vol = prefs.volumePercent / 100f
   setVolume(vol, vol)
   ```
   Questo garantisce che il volume del MediaPlayer rispecchi lo slider, oltre al `setStreamVolume(STREAM_ALARM)` gia' presente.
4. Restore logic: `STREAM_ALARM` gia' ripristinato in `restoreAudio()` — nessuna modifica necessaria.

### File coinvolti
- `PrefsManager.kt`: costante MIN_VOLUME_PERCENT
- Layout slider (XML): valueFrom
- `CallMonitorService.kt`: setVolume sul MediaPlayer

---

## Feature 2 — Disattiva temporanea suoneria (timer 1-12h)

### Comportamento
- Bottone nella Home: "Disattiva suoneria"
- Tap → dialog con `NumberPicker` (1-12, step 1h)
- Conferma → countdown visibile nella Home card ("Suoneria disattivata - riattivazione tra Xh Ym")
- **Solo override suoneria/vibrazione disattivato** — GPS location sharing continua a funzionare normalmente
- A scadenza → riattivazione automatica
- Bottone cambia stato: durante pausa mostra "Riattiva suoneria" per cancellazione manuale

### Implementazione
1. **PrefsManager**: nuovo campo `muteUntilTimestamp: Long` (0 = non attivo)
2. **CallMonitorService**: in `overrideAudio()`, controllare `prefs.muteUntilTimestamp`:
   - Se `> System.currentTimeMillis()` → skip override audio (ma non skip location)
   - Se scaduto → resettare a 0
3. **AlarmManager**: `setExactAndAllowWhileIdle()` per riattivazione precisa
4. **BroadcastReceiver**: nuovo `MuteTimerReceiver` per gestire l'alarm di riattivazione
5. **UI**: countdown aggiornato ogni minuto via `Handler.postDelayed`
6. **Persistenza**: timestamp in SharedPreferences sopravvive a kill/reboot. `BootReceiver` ri-registra l'alarm se timestamp futuro.

### File coinvolti
- `PrefsManager.kt`: muteUntilTimestamp
- `HomeFragment.kt` (nuovo): UI timer + dialog
- `MuteTimerReceiver.kt` (nuovo): receiver per alarm
- `CallMonitorService.kt`: guard clause per mute
- `BootReceiver.kt`: ri-registra alarm
- `AndroidManifest.xml`: dichiarare MuteTimerReceiver
- Layout XML: card timer nella Home
- `strings.xml` (ita + eng): stringhe timer

---

## Feature 3 — Scelta suono override (suoneria vs notifica)

### Comportamento
- In Impostazioni: "Suono override" con due radio button
  - **Suoneria telefono** (default) — `RingtoneManager.TYPE_RINGTONE`
  - **Suono notifica** — `RingtoneManager.TYPE_NOTIFICATION`
- Globale per tutti i VIP
- Meno invasivo in contesti come riunioni

### Implementazione
1. **PrefsManager**: nuovo campo `overrideSoundType: Int` (default `TYPE_RINGTONE`)
2. **CallMonitorService.startRingtone()**: usare `RingtoneManager.getDefaultUri(prefs.overrideSoundType)` invece di hardcoded `TYPE_RINGTONE`
3. **SettingsFragment** (nuovo): radio group per selezione suono

### File coinvolti
- `PrefsManager.kt`: overrideSoundType
- `CallMonitorService.kt`: startRingtone() usa pref
- `SettingsFragment.kt` (nuovo): UI radio
- Layout XML: radio group in settings
- `strings.xml` (ita + eng): etichette suono

---

## Feature 4 — Toggle suoneria per singolo contatto VIP

### Comportamento
- Nuovo campo `ringtoneEnabled: Boolean` (default `true`) in `VipContact`
- Nell'item VIP: icona campanella (on) / campanella barrata (off) come toggle
- Se `ringtoneEnabled = false`: chiamata VIP **non** trigga override audio, ma il bottone GPS resta funzionante
- Tutte e 4 le icone nell'item VIP **ridotte del 20%** rispetto a dimensione attuale

### Implementazione
1. **PrefsManager**: aggiungere `ringtoneEnabled` a `VipContact` data class, serializzazione/deserializzazione JSON
2. **VipNumbersAdapter**: nuova icona campanella con toggle, `OnClickListener` per toggle stato
3. **CallMonitorService.isVipNumber()**: cambiare in metodo che ritorna `VipContact?` invece di `Boolean`, poi controllare `ringtoneEnabled` prima di override
4. **Layout**: icona campanella, ridurre tutte le icone del 20%

### Icone
- `ic_notifications` (Material Icons) — campanella attiva
- `ic_notifications_off` — campanella disattivata

### File coinvolti
- `PrefsManager.kt`: VipContact con ringtoneEnabled
- `VipNumbersAdapter.kt`: icona + toggle logic
- `CallMonitorService.kt`: check ringtoneEnabled
- `item_vip_number.xml`: layout icona + resize
- `strings.xml`: tooltip/content description

---

## Feature 5 — Riorganizzazione UI con Navigation Drawer

### Architettura
`MainActivity` diventa host di Fragment con `DrawerLayout` + `NavigationView`.

### Sezioni drawer
1. **Home** (default) — `HomeFragment`
   - Service toggle (switch + stato)
   - Sezione permessi (runtime + DND, badge status)
   - Lista contatti VIP (RecyclerView, 4 icone ridotte: edit, delete, campanella toggle, GPS request)
   - Card disattivazione temporanea (bottone + countdown timer)

2. **Impostazioni** — `SettingsFragment`
   - Volume slider (25-100%, step 5%)
   - Scelta suono override (radio: suoneria / notifica)
   - Quiet hours (lista regole + bottone aggiungi, max 10)

3. **Log posizioni** — `LocationLogFragment`
   - Lista "Chi ha richiesto la mia posizione?" (attualmente in Home)
   - Stesso formato: nome, numero, timestamp, tipo

4. **Privacy e licenze** — `PrivacyFragment`
   - Informativa trattamento dati
   - Licenze open source (GPL-3.0)
   - Contatto sviluppatore

### Navigazione
- `FragmentContainerView` in `activity_main.xml`
- `NavigationView` con menu XML per le 4 voci
- Hamburger icon nella toolbar
- Gestione back: drawer si chiude, poi back naviga a Home, poi esce

### Migration strategy
- Estrarre logica da `MainActivity` nei rispettivi Fragment
- `MainActivity` mantiene solo: DrawerLayout, toolbar, service binding, permission handling
- I Fragment comunicano con l'Activity tramite interfacce o shared ViewModel

### File coinvolti
- `MainActivity.kt`: refactor a navigation host
- `HomeFragment.kt` (nuovo): logica home estratta
- `SettingsFragment.kt` (nuovo): volume + suono + quiet hours
- `LocationLogFragment.kt` (nuovo): log posizioni estratto
- `PrivacyFragment.kt` (nuovo): testo privacy + licenze
- `activity_main.xml`: refactor con DrawerLayout
- `fragment_home.xml` (nuovo)
- `fragment_settings.xml` (nuovo)
- `fragment_location_log.xml` (nuovo)
- `fragment_privacy.xml` (nuovo)
- `nav_menu.xml` (nuovo): voci drawer
- `strings.xml` (ita + eng): voci menu + stringhe privacy

---

## Feature 6 — Tracciamento GPS continuato (30 secondi)

### Comportamento attuale
`LocationHelper.requestSingleFix()` ottiene il primo fix con accuracy <= 30m e termina.

### Nuovo comportamento
1. Primo fix valido (accuracy <= 30m) → invia posizione + notifica "Posizione trovata"
2. **Continua tracking per 30 secondi** con aggiornamenti ogni **1 secondo**
3. Stessa notifica aggiornata con accuracy in tempo reale ("+/-Xm")
4. Link Google Maps nella notifica aggiornato con ultima posizione nota
5. Dopo 30 secondi → stop tracking, notifica resta con ultima posizione
6. **Nessuna notifica aggiuntiva** durante il tracking continuato

### Implementazione
1. **LocationHelper**: nuovo metodo `requestContinuousFix(durationMs: Long = 30000, intervalMs: Long = 1000, callback)`
   - Dopo primo fix valido, continua `requestLocationUpdates` per 30s
   - Callback: `onLocationUpdate(location)` per ogni aggiornamento, `onTrackingComplete(lastLocation)` alla fine
2. **NtfyService**: aggiornare la notifica con nuove coordinate ad ogni update
   - Aggiornare il testo accuracy nella notifica esistente
   - Aggiornare il PendingIntent di Google Maps con nuove coordinate
3. **Timeout**: `Handler.postDelayed(30000)` per fermare il tracking

### File coinvolti
- `LocationHelper.kt` (flavor internal): requestContinuousFix
- `NtfyService.kt`: gestione aggiornamenti continui + notifica
- `strings.xml`: stringhe accuracy/tracking

---

## Trasversale — Localizzazione

Tutte le stringhe nuove aggiunte in:
- `values/strings.xml` (inglese, default)
- `values-it/strings.xml` (italiano)

Pattern naming: prefisso per sezione (`mute_`, `settings_`, `privacy_`, `nav_`, `tracking_`).

## Trasversale — Versione e Release

- `build.gradle.kts`: versionName "2.0", versionCode 4
- README.md: aggiornare con nuove feature
- Git tag: v2.0
- Build: assembleInternalRelease + assembleFdroidRelease
- Deploy APK su unraid

## Note F-Droid flavor

- Feature 2 (timer): disponibile in entrambi i flavor
- Feature 3 (scelta suono): disponibile in entrambi i flavor
- Feature 4 (toggle campanella): disponibile in entrambi i flavor
- Feature 5 (drawer): disponibile in entrambi i flavor
- Feature 6 (tracking continuato): solo flavor **internal** (LocationHelper e' stub in fdroid)
- Privacy fragment: contenuto diverso per flavor (fdroid: no location mention)
