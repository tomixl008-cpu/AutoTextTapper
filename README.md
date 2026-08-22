# Auto Text Tapper

A user-controlled Android Accessibility Service assistant, built with Kotlin and traditional XML
Views (no Jetpack Compose).

## Authorized-use notice

This app is for automating **apps, screens, and accounts that the device owner is authorized to
automate.** It requires the Android Accessibility Service permission, which the owner must enable
manually through Android Settings — the app cannot enable it silently. The app performs no action
until the owner explicitly presses **Start**, and everything stops immediately when **Stop** is
pressed.

The app deliberately does **not** implement: screenshot capture, OCR, root access, overlays,
hidden background behavior, CAPTCHA handling, ad-click automation, banking/payment/OTP/password/
login automation, protected-screen bypasses, or anti-detection methods. It only reads
accessibility text/content descriptions that are already exposed to any accessibility service, and
only clicks/gestures on the screen that is currently active.

## Behavior flow

```
Start -> wait 5s -> Main Scan (Like video priority over Skip)
                       |
          -------------------------------
          |                             |
     "Like video" found            "Skip" found (only if
          |                         "Like video" absent)
          v                             v
      Like route                    Skip route
```

### Like video route

This route spans two apps: **Fantik** (`com.tikboost.fantik`), where Main Scan runs, and
**TikTok** (`com.zhiliaoapp.musically`), which Fantik hands off to when "Like video" is clicked.

1. Click **Like video** (on Fantik). Fantik itself brings TikTok to the foreground.
2. Wait 2 seconds.
3. Double-tap the exact centre of the screen — TikTok's native double-tap-to-like gesture (two
   ~60 ms taps, ~150 ms apart). Before tapping, the service verifies TikTok is actually in the
   foreground; if it isn't yet, it aborts back to Main Scan instead of blind-tapping the wrong app.
4. Check for the red-heart like confirmation (❤️). If it hasn't appeared, wait 1 second and
   double-tap again, up to 3 attempts, so a missing/misdetected heart can never stall automation.
5. Double-tap the system Recents/Overview action (`GLOBAL_ACTION_RECENTS` pressed twice, ~150 ms
   apart) — the first press opens the recent-apps screen, the second press switches straight back
   to Fantik (the app that was open right before TikTok), exactly like physically double-pressing
   the Overview button. No on-screen swipe coordinates are involved.
6. Wait for the "Loading" text to appear and then disappear on Fantik (checked every second and on
   every window/content-change event; 30-second timeout if "Loading" never appears at all).
7. Once loading is gone, wait 1 second extra, then return to Main Scan.

**Priority rule:** if "Like video" and "Skip" are both visible, only "Like video" is clicked. Skip
is only ever considered when "Like video" is completely absent from the screen.

**App scoping:** Main Scan only ever reads/clicks when Fantik is the foreground app, and the
double-tap/like-confirmation step only ever acts when TikTok is the foreground app. If the user
switches to any third app (WhatsApp, Chrome, etc.) mid-routine, the service takes no action at all
and simply waits for Fantik or TikTok to come back to the foreground.

### Skip route

1. Click **Skip**.
2. Wait exactly 4 seconds.
3. Return directly to Main Scan. No double-tap, no swipe, no Loading wait.

### Stop rule

Pressing **Stop** immediately cancels every pending delay, scan, retry, tap, and swipe in flight,
sets the internal state to `IDLE`, and shows "Stopped". No further action happens until Start is
pressed again.

## Project structure

- `app/src/main/java/com/example/autotexttapper/TextAutomationAccessibilityService.kt` — the
  finite state machine and all gesture/click logic. All tunable values (target text, delays,
  gesture coordinates/durations) live in the `AutomationConfig` object at the top of this file.
- `app/src/main/java/com/example/autotexttapper/MainActivity.kt` — the UI: Open Accessibility
  Settings / Start / Stop buttons and a live status line.
- `app/src/main/java/com/example/autotexttapper/AutomationStatusHolder.kt` — a tiny observable
  singleton the service uses to publish status text, which `MainActivity` displays live while open.
- `app/src/main/res/xml/accessibility_service_config.xml` — accessibility service capabilities
  (window content retrieval, gesture dispatch, window state/content change events).

## Local build

Requirements: JDK 17, Android SDK with `platforms;android-37` and `build-tools;36.0.0` installed,
and `ANDROID_HOME`/`local.properties` pointing at it.

```bash
./gradlew assembleDebug --no-daemon
```

The debug APK is produced at:

```
app/build/outputs/apk/debug/app-debug.apk
```

## Installing on a phone

1. Install the APK on the device (`adb install -r app-debug.apk`, or copy it to the device and open
   it with a file manager).
2. Open **Auto Text Tapper**.
3. Tap **Open Accessibility Settings**, find **Auto Text Tapper service**, and turn it on. Android
   requires this step to be done manually by the device owner — the app cannot do it for you.
4. Return to the app. The status line should read "Service enabled and ready."
5. Navigate to the authorized target screen, then tap **Start (5-second delay)**. You have 5
   seconds to make sure the correct screen is in the foreground before scanning begins.
6. Tap **Stop** at any time to halt automation immediately.

## Building via GitHub Actions (no local Android SDK needed)

1. Push this repository to `main` on GitHub.
2. Open the repository's **Actions** tab.
3. Open the **Build Android APK** workflow.
4. Wait for the run to finish with a green check (or trigger it manually via **Run workflow** /
   `workflow_dispatch`).
5. Open the completed workflow run.
6. Under **Artifacts**, download **AutoTextTapper-debug-APK**.
7. Extract the downloaded ZIP and install `app-debug.apk` on your device.

## Limitations

- Only UI elements that are exposed through Android's accessibility tree (text or content
  description) can be detected. Text rendered inside a video surface, `Canvas`, custom-drawn view,
  or a view that explicitly hides itself from accessibility services is invisible to this app.
- Some apps mark sensitive/protected screens so their content is not exposed to accessibility
  services at all; this app cannot and does not attempt to bypass that.
- The device owner must manually enable the Accessibility Service permission in Android Settings —
  this is an Android platform requirement, not a limitation of this app, and it cannot be automated
  or skipped.
- Automation only runs while explicitly started, and only reacts to on-screen text that already
  exists — it does not infer intent, use OCR, or read screen pixels.

## Changing text, waits, and gesture timing

Everything tunable lives in the `AutomationConfig` object at the top of
`TextAutomationAccessibilityService.kt`:

```kotlin
private object AutomationConfig {
    const val LIKE_VIDEO_TEXT = "Like video"
    const val SKIP_TEXT = "Skip"
    const val LOADING_TEXT = "Loading"

    const val INITIAL_DELAY_MS = 5000L
    const val WAIT_AFTER_LIKE_MS = 2000L
    const val WAIT_AFTER_SKIP_MS = 4000L
    const val MAIN_SCAN_INTERVAL_MS = 1000L
    const val LOADING_SETTLE_DELAY_MS = 500L
    const val LOADING_TIMEOUT_MS = 30000L

    const val TAP_DURATION_MS = 60L
    const val DOUBLE_TAP_GAP_MS = 150L
    const val RECENTS_DOUBLE_TAP_GAP_MS = 150L
}
```

Change the text constants to match a different target app's wording, adjust the `*_MS` delays to
retime the routine, or adjust `RECENTS_DOUBLE_TAP_GAP_MS` to widen/narrow the gap between the two
`GLOBAL_ACTION_RECENTS` presses used to jump back to the previous app. No other file needs to
change for these kinds of tweaks.
