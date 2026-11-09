# STARBOY Gemini backend

This is the private server-side AI connection for the STARBOY Android app.

## Provider and default model

- Google Gemini Developer API
- Default model: `gemini-3.5-flash-lite`
- The model has a free input/output tier according to Google's current pricing page. Free-tier availability and quotas can change.
- Research mode uses Google News RSS as a lightweight live news source and passes a small source list to Gemini.
- It does **not** use Gemini Google Search grounding, which is not included in the free tier for the selected model.
- Normal Companion, Creator and Career prompts do not trigger a web search.

## Deploy on Vercel

The Vercel project root must be `northstar-android`.

Set these server-side environment variables for Production and Preview:

- `GEMINI_API_KEY`: create a key at https://aistudio.google.com/apikey
- `GEMINI_MODEL`: `gemini-3.5-flash-lite` (optional; this is the default)
- `APP_ACCESS_TOKEN`: long random secret; this authenticates the single personal app installation.

Redeploy after setting the environment variables. The API key must never be stored in Android source, BuildConfig, resources, or the APK.

## Configure STARBOY

Open STARBOY → Settings:

- Server URL: `https://YOUR-VERCEL-PROJECT.vercel.app/api/companion`
- Private device access token: the exact `APP_ACCESS_TOKEN` value stored in Vercel.

Save settings, open Buddy, and send a short test message. Check the backend status at `https://YOUR-VERCEL-PROJECT.vercel.app/api/health`; it reports configuration booleans only and never returns secret values.

## Privacy and limits

- Journal and routine data remain on the phone. Only the message, selected mode, mood and energy value are sent to Gemini when the user taps Send.
- The backend does not persist prompts or responses.
- A small in-memory per-IP burst limiter is applied; Vercel serverless instances may reset it.
- The free Gemini tier has rate and daily quotas. Google states free-tier submitted content may be used to improve its products; do not send private journal entries unless you accept Google's applicable terms.
- Research mode performs a limited Google News RSS query based on the text you explicitly submit. RSS availability and result coverage are not guaranteed.
- Vercel Hobby is intended for personal, non-commercial use; check Vercel's plan terms before commercial use.

## Local development

From this directory:

```bash
npm install
vercel dev
```

Add the environment variables to a local `.env.local` file (never commit it).
