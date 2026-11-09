# STARBOY — Your Personal Life Operating System

STARBOY is a private, Android-first personal life companion. It combines a calm check-in, a short detachment/reset flow, mixed-topic discovery, an original YouTube Creator Lab, habit tracking and an optional Grok-backed assistant.

## What works in this foundation

- **Today:** mood and energy check-in, three small daily actions, a preview of the routine and a rotating daily thought, with quick paths into Reset, Creator and Buddy.
- **Reset:** a 60-second breathing pause and a brief prompt for separating a feeling from an action. It does not encourage monitoring or contacting a former partner.
- **Discover:** fetches four varied Google News RSS searches (creator stories, technology/science, careers/biographies, and ideas/culture), stores headlines locally and opens source links in the browser.
- **Personalisation:** explicit `Useful` / `Show me less` feedback adjusts category weights. Mood nudges the balance, and category repetition is reduced to preserve variety. The model is intentionally transparent rather than a black box.
- **Creator Lab:** original video idea starters and a reusable story structure. It studies broad storytelling techniques, not another creator's exact voice or scripts.
- **Daily routine & progress:** local scheduled reminders for waking up, morning soup, bathing/getting ready, a progressive Bible reading, a daily quote, movement, a financial action, creator/career focus, reflection and sleep wind-down. Every task has an editable time and an enable/disable switch. Confirm completion from the notification or in the app. Completions are date-scoped; the Bible plan advances only when that reading is marked complete.
- **Mobile data controls:** mobile data is allowed by default, background discovery defaults to a 12-hour interval and the estimated feed-response budget defaults to 5 MB/day. Users can switch to unmetered-only work, choose 6/12/24 hours and adjust the estimate.
- **Notifications:** Android notification permission is requested on first launch. Reminder notifications include **Confirm complete** and **Remind in 10 min** actions. Routine alarms are restored after reboot, app replacement and clock/time-zone changes. A precise wake-up alarm can be enabled through Android's special alarm access screen; other routine reminders use battery-aware scheduling.
- **Privacy:** no login, ads, analytics SDK or cloud journal. Check-ins, tasks and completion history remain in app-private local storage. The optional device access token is encrypted using Android Keystore. The xAI API key is never stored in the APK.

## Important network behaviour

Northstar does **not** run a continuous web crawler. Android can defer background jobs for battery and network reasons. Periodic refresh is best-effort; a manual refresh is also available. The in-app data budget counts feed response-body bytes only. It is an estimate, not a carrier-grade data meter; HTTP overhead, operating-system traffic and links opened in the external browser are not included.

The discovery feed currently uses public RSS search results. It does not yet use an AI search agent to crawl and synthesise arbitrary web pages. Grok is the optional reasoning/writing layer; a separate search-and-summarise agent can be added after the initial build is verified.

## Build the APK

The build environment used to prepare this source does not include the Android SDK or Gradle distribution, and external downloads are unavailable. Therefore no APK has been compiled or device-tested here. The included GitHub Actions workflow is configured to build it in a clean Android build runner.

1. Create a private GitHub repository named `northstar` (or another name you prefer).
2. Upload the contents of this folder to the repository's `main` branch.
3. Open **Actions → Build Northstar APK → Run workflow** (or push to `main`).
4. When the run completes, open the workflow run and download the `northstar-debug-apk` artifact.
5. Extract the ZIP and install `app-debug.apk` on your Android phone. On first install, Android may ask you to allow installation from the browser/files app.

The workflow uses Java 17, Gradle 8.9 and Android platform 35. It runs the Bible-plan unit tests and produces `STARBOY-debug.apk`. A JUnit test for the progressive Bible reading sequence is included, but has not yet been run in this restricted environment. Before public distribution, create a signed release build, review the privacy/data declarations and test on a physical device.

## Connect Gemini (configured backend)

STARBOY uses a private Vercel serverless endpoint that calls the Google Gemini Developer API. The default model is `gemini-3.5-flash-lite`, selected for low-cost text inference. Google currently lists free input/output usage for this model, subject to per-project quotas and access availability. Free-tier prompts may be used by Google to improve its products; avoid sending private journal entries unless you accept the applicable terms.

The companion API is in `api/companion.ts`; the health check is `api/health.ts`. The Vercel project root is `northstar-android`. The `research` mode uses a limited Google News RSS search and attaches source URLs to the response. It does not use paid Gemini Search grounding.

Required Vercel environment variables:
- `GEMINI_API_KEY` — your Google AI Studio API key, stored only in Vercel.
- `APP_ACCESS_TOKEN` — the private device token configured for your STARBOY installation.
- `GEMINI_MODEL` — optional; defaults to `gemini-3.5-flash-lite`.

Never put `GEMINI_API_KEY` in Android source, `local.properties`, app resources, or the APK. Configure the server URL and device token in STARBOY Settings. See `GEMINI_BACKEND_SETUP.md` for the exact deployment and setup instructions.
## Current limitations / next build stage

- The RSS searches and category ranking are a working first discovery layer, not a full semantic preference-learning model.
- There is no cloud sync across devices. Data stays local by design.
- Reminder timing remains subject to Android battery and idle policies. The wake-up task can request exact-alarm access; the other routine tasks use inexact alarms. Users should test behaviour on their own device and review battery optimisation settings if reminders are delayed.
- Buddy uses the Gemini endpoint when configured; otherwise the app keeps its local/offline guidance. Research mode fetches a small live Google News RSS sample.
- There is no signed release APK yet.

## Project structure

```text
app/                  Native Kotlin + Jetpack Compose Android app
api/companion.ts      Vercel serverless proxy to Google Gemini
.github/workflows/    APK build workflow
GEMINI_BACKEND_SETUP.md  Handoff instructions for Grok to continue and verify the build
```

### Live Grok research

The Buddy screen includes an opt-in **Research** mode. It uses the xAI Responses API with the `web_search` server-side tool; ordinary Life/YouTube/Career replies do not invoke web search. Search-tool calls are billed separately from model tokens according to xAI's current pricing. The app does not run automatic Grok searches in the background; automatic refresh remains the lower-data RSS feed.

## Personalised ranking model

The first ranking layer is intentionally understandable: category feedback adds or removes weight, mood lightly shifts the mix, and a repetition penalty promotes topic diversity. This is a seed model; it does not infer private traits or use a hidden engagement-maximisation objective. It can be improved later with user-controlled interest weights, recency/novelty, reading completion and a weekly review.
