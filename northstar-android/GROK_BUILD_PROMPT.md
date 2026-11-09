# Grok implementation handoff — Northstar

You are continuing an existing native Android project. Do not throw it away and do not convert it to a website or React Native app. Keep the native Kotlin + Jetpack Compose architecture.

## Mission

Finish and verify **Northstar**, a private Android personal life operating system for one user. Its purpose is to support healthy detachment after a breakup, broaden curiosity through fresh discoveries, help the user build an original YouTube career, and turn career ambitions into real actions.

The deliverable is a working installable Android APK, not just a mock-up or a design preview.

## First: inspect, then compile

1. Inspect every project file and identify compile errors before changing architecture.
2. Build `:app:assembleDebug` with Java 17, Gradle 8.9 and Android SDK 35.
3. Fix all Kotlin, Compose, Gradle and Android manifest issues. Do not claim it builds until the build command passes.
4. Add a small set of unit tests for the personalisation ranking, data-budget guard and article de-duplication. Run them.
5. Verify the GitHub Actions workflow produces `app-debug.apk` and uploads it as an artifact.
6. Do not add paid SDKs, analytics, ads, Firebase or Supabase unless explicitly asked.

## Product principles

- The app is personal, discreet, calm, premium and respectful. Use the existing midnight/navy and warm-gold visual direction.
- Do not make the product addictive. No infinite scroll, streak guilt, manipulative notifications, forced daily check-ins or endless engagement loops.
- The purpose of detachment is to rebuild identity and agency, not to keep the user consuming breakup content. Never add ex-partner surveillance, profile checking, location tracking, or prompts to contact the former partner.
- Encourage real-world action, supportive relationships, sleep, movement, learning and meaningful creation.
- Be emotionally supportive without diagnosing the user or pretending to replace human support or professional care.
- Keep private journal/check-in data on-device unless a future explicit opt-in is added.

## Required product areas

1. **Today:** personalised daily direction from mood, energy and chosen goals. Show at most three practical actions.
2. **Reset:** fast grounding/breathing, trigger reflection and an urge-delay tool. Keep language non-judgemental.
3. **Discovery:** curated stories across creator journeys, biographies, technology, AI, science, business, history, craft and unusual ideas. Always show the publisher/source, publication date where available and the original link. Diversify the feed; do not overfit to one click.
4. **Human-centred ranking:** learn from explicit Useful / Show me less feedback, interests, mood, energy, novelty and topic diversity. Make ranking explainable. Do not infer sensitive attributes.
5. **Creator Lab:** a practical YouTube learning workspace. Analyse high-level techniques from Jonathan Brisco's public content (direct delivery, personal-growth themes, story-led motivation) but never imitate his exact writing, voice, catchphrases, or persona. Help the user form an original channel identity, hooks, story outlines, scripts, shot lists, content calendar and weekly review.
6. **Career Architect:** low-cost skill sprints, portfolio projects, career exploration and weekly outputs. Distinguish verified opportunities from speculative suggestions; do not invent job openings or earnings.
7. **Buddy:** use Grok through the existing Vercel proxy only. Keep `XAI_API_KEY` on the server. The APK may contain only the device access token, encrypted with Android Keystore. Add clear offline fallback behaviour.
8. **Daily routine & reminders:** provide editable, switchable reminders for wake-up, morning soup, bathing/getting ready, Bible reading, a daily motivational quote, movement, one financial action, creator/career focus, reflection and sleep wind-down. Default times are starter suggestions and must be editable. Store tasks and completion history locally. Every reminder notification must include a **Confirm complete** action and a short snooze option. Completing the Bible task advances the reading plan by one chapter; unchecking/undoing it reverses that one step. Start with John 1 and then continue through John, Acts, Romans, Proverbs and Psalms. Rotate original, unattributed encouragement lines daily. Do not promise that following the app guarantees financial, health or life success.
9. **Settings:** background refresh frequency, mobile data permission, daily approximate response-byte budget, privacy controls, Grok endpoint and token deletion. Include notification-permission guidance and an option to enable Android exact-alarm access for the wake-up reminder.

## Network and battery rules

- Never create a continuous crawler or a loop that repeatedly wakes the device.
- Use WorkManager periodic work only, with explicit network constraints and user-controlled cadence. Android may defer jobs for Doze/battery/network policy.
- Keep the manual refresh button.
- Default to low data usage. The byte budget must be described as approximate feed-response bytes, not a guarantee of total carrier data use.
- De-duplicate saved items, cap the local feed and cache articles.
- Respect connectivity failures and return useful states without crashing.

## Security and privacy

- Never embed xAI keys or server secrets in Android code or BuildConfig.
- Do not log user prompts, check-ins, tokens or response bodies.
- Do not add ads, trackers or third-party analytics.
- Validate URL schemes before opening articles; only allow HTTPS links.
- Add API input-size limits and safe error messages. Do not return upstream secrets to the app.

## Acceptance criteria

- App launches with no network connection.
- All five navigation destinations render and are usable.
- User receives a runtime notification-permission request on Android 13+ and can open notification settings if denied.
- Every default routine task appears in the app with editable time and enable/disable controls.
- Reminder notifications expose Confirm complete and Snooze actions; both work from the background.
- Manual RSS discovery works on a metered cellular network when enabled.
- Unmetered-only mode does not schedule on metered networks.
- Background refresh is periodic, never continuous.
- The feed changes when feedback is given and retains category diversity.
- Reset timer can start, pause and complete.
- Routine tasks reset their completion state by local date without deleting history.
- Reminder times can be edited, tasks enabled/disabled and alarms rescheduled immediately.
- Notification actions mark the task complete and reflect in app progress when the app resumes.
- The Bible reading index advances only after confirmation and can be undone.
- Quotes rotate daily and also appear in the morning notification.
- Alarms are restored after reboot, app replacement and time/time-zone changes.
- Wake-up uses `setExactAndAllowWhileIdle` only if exact-alarm access is granted; otherwise fall back to `setAndAllowWhileIdle`. Other tasks use the battery-aware inexact path. Explain clearly that Android may delay inexact alarms and exact alarms are still subject to system policy.
- Grok works through the server proxy when configured and degrades cleanly when it is not.
- APK installs and launches on a real Android device/emulator.
- Build and tests pass. Provide the exact artifact location and test results; do not claim physical-device testing if only CI compilation was performed.

## Final output

Produce the actual debug APK artifact and a short QA report containing build command, test command, package ID, Android minimum/target SDK, known limitations and whether physical-device installation was tested. Preserve this project and explain every changed file briefly.
