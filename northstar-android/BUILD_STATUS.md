# Northstar build status — 10 November 2026

## Foundation and this update
- Native Android project: Kotlin, Jetpack Compose, WorkManager.
- Local data store and Android Keystore encrypted device token.
- RSS discovery reader with response-byte budget, metered/unmetered preference and scheduled/manual refresh.
- Mood/energy check-in, detachment reset, Creator Lab, discovery personalisation and Grok/Vercel companion proxy.
- Daily routine tasks now include wake-up, morning soup, bath/get-ready, Bible reading, daily encouragement, movement, financial action, creator/career focus, reflection and wind-down.
- Each routine task has a user-editable time, enable/disable control and date-scoped completion state.
- Android notifications provide **Confirm complete** and **Remind in 10 min** actions. Completion is persisted locally and updates the app's progress when it resumes.
- Bible reading begins at John 1 and progresses through John, Acts, Romans, Proverbs and Psalms only when the user confirms completion; undo reverses the current completion step.
- Original encouragement lines rotate daily and appear in the app and the daily quote reminder.
- Reminder alarms are recreated after boot, app replacement, clock changes and time-zone changes. The wake-up task uses exact-and-allow-while-idle only if the user grants Android exact-alarm access; all other tasks use the inexact battery-aware alarm path.
- Added JUnit unit tests for the Bible reading sequence.
- GitHub Actions workflow remains configured to produce and upload a debug APK.

## Validation performed in this environment
- Android manifest XML parsed successfully.
- Kotlin compiler parser pass produced no syntax/parser diagnostics for the changed Kotlin sources.
- Static source inspection performed for scheduling, completion actions and Bible-plan progression.

## Not yet verified
- Android Gradle build / `assembleDebug`.
- JUnit execution and Android instrumentation tests.
- Notification permission prompt and action handling on a physical device.
- Exact-alarm settings flow on Android 12+.
- Real reboot/time-zone restoration on a device.
- Live RSS response parsing from a phone.
- Live Vercel deployment and xAI request.

## Why there is no APK in this package
This environment has no Android SDK, no Gradle installation/distribution, no Android platform `android.jar`, and no network access to download the missing toolchain. The GitHub Actions workflow is provided to produce the APK on a hosted Android runner. The project archive is source code, not a compiled APK.
