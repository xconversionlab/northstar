import type { VercelRequest, VercelResponse } from "@vercel/node";
import { timingSafeEqual } from "node:crypto";

const MODEL = process.env.GEMINI_MODEL || "gemini-3.5-flash-lite";
const MAX_MESSAGE_CHARS = 3_000;
const MAX_RSS_ITEMS = 5;
const RATE_WINDOW_MS = 60_000;
const MAX_REQUESTS_PER_WINDOW = 12;
const recentRequests = new Map<string, { startedAt: number; count: number }>();

const SYSTEM_PROMPTS: Record<string, string> = {
  companion: `You are STARBOY, a private, practical and emotionally steady life companion. Your signature line is "A better you, one day at a time." Be warm, direct, thoughtful and specific. Help the user choose one useful next action rather than giving empty motivation. Support emotional detachment through boundaries, self-respect, acceptance and rebuilding a full life; do not encourage monitoring, contacting, or repeatedly analysing a former partner. Never diagnose or present yourself as a therapist. For health questions, give conservative general information and encourage qualified care for concerning symptoms. Respect the user's Christian faith when they ask for spiritual guidance; never shame or coerce. Encourage balanced routines, sleep, food, exercise, learning, meaningful relationships, financial responsibility and real-world action. Do not promise that using this app guarantees success. If the user expresses imminent intent to hurt themselves or another person, respond compassionately and urge immediate local emergency help and support from a trusted person.`,
  creator: `You are STARBOY Creator Lab. Help the user build an original YouTube identity. Create strong hooks, specific human stories, a clear insight and a useful ending. You may discuss high-level storytelling techniques from creators, but never imitate a living creator's exact voice, scripts, catchphrases or persona. Prefer ideas the user can honestly document and produce with modest equipment. Give concrete outlines, practice drills and a realistic next action.`,
  career: `You are STARBOY Career Architect. Help the user build income-relevant skills, a credible portfolio and sustainable work habits. Prefer low-cost experiments, measurable weekly actions and proof of work. Never invent job openings, qualifications, salaries or guaranteed income. Distinguish verified facts from suggestions and give one next step the user can complete today.`,
  research: `You are STARBOY Research Desk. Investigate the question using the supplied live Google News RSS results. Summarise what the sources actually say, distinguish facts from interpretation, identify uncertainty and include source references by their exact [1], [2] labels. Do not claim to have searched the entire internet: the supplied results are a limited news-search sample. Prefer useful discoveries, biographies, science, technology, history, business and culture. Avoid sensationalism and unsupported health claims.`,
};

function safeTokenMatch(expected: string, received: string): boolean {
  const a = Buffer.from(expected);
  const b = Buffer.from(received);
  return a.length === b.length && timingSafeEqual(a, b);
}

function allowRequest(ip: string): boolean {
  const now = Date.now();
  const current = recentRequests.get(ip);
  if (!current || now - current.startedAt >= RATE_WINDOW_MS) {
    recentRequests.set(ip, { startedAt: now, count: 1 });
    return true;
  }
  if (current.count >= MAX_REQUESTS_PER_WINDOW) return false;
  current.count += 1;
  return true;
}

function decodeXml(value: string): string {
  return value
    .replace(/<!\[CDATA\[([\s\S]*?)\]\]>/g, "$1")
    .replace(/<[^>]*>/g, " ")
    .replace(/&amp;/g, "&")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/&quot;/g, '"')
    .replace(/&#39;|&apos;/g, "'")
    .replace(/&#(\d+);/g, (_, code: string) => String.fromCodePoint(Number(code)))
    .replace(/\s+/g, " ")
    .trim();
}

function readTag(xml: string, tag: string): string {
  const match = xml.match(new RegExp(`<${tag}(?:\\s[^>]*)?>([\\s\\S]*?)<\\/${tag}>`, "i"));
  return match ? decodeXml(match[1]) : "";
}

type ResearchSource = { title: string; url: string; published: string };

async function searchGoogleNews(query: string): Promise<ResearchSource[]> {
  const params = new URLSearchParams({ q: query.slice(0, 180), hl: "en-IN", gl: "IN", ceid: "IN:en" });
  const response = await fetch(`https://news.google.com/rss/search?${params.toString()}`, {
    headers: { "User-Agent": "STARBOY-Personal-Assistant/1.0" },
    signal: AbortSignal.timeout(8_000),
  });
  if (!response.ok) throw new Error("News search is temporarily unavailable.");
  const xml = (await response.text()).slice(0, 180_000);
  const items = [...xml.matchAll(/<item>([\s\S]*?)<\/item>/gi)].slice(0, MAX_RSS_ITEMS);
  return items.map((match) => ({
    title: readTag(match[1], "title"),
    url: readTag(match[1], "link"),
    published: readTag(match[1], "pubDate"),
  })).filter((item) => item.title && /^https:\/\//i.test(item.url));
}

function extractGeminiText(data: any): string {
  const parts = data?.candidates?.[0]?.content?.parts ?? [];
  return parts.filter((part: any) => typeof part?.text === "string").map((part: any) => part.text).join("\n").trim();
}

export default async function handler(req: VercelRequest, res: VercelResponse) {
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type, x-app-token");
  res.setHeader("Cache-Control", "no-store");

  if (req.method === "OPTIONS") return res.status(204).end();
  if (req.method !== "POST") return res.status(405).json({ error: "Method not allowed" });

  const expected = process.env.APP_ACCESS_TOKEN;
  const received = String(req.headers["x-app-token"] ?? "");
  if (!expected || !safeTokenMatch(expected, received)) {
    return res.status(401).json({ error: "Unauthorised device. Check the private access token in STARBOY settings." });
  }

  const ipHeader = req.headers["x-forwarded-for"];
  const ip = String(Array.isArray(ipHeader) ? ipHeader[0] : ipHeader ?? "unknown").split(",")[0].trim();
  if (!allowRequest(ip)) return res.status(429).json({ error: "Too many requests. Please wait a minute and try again." });

  const apiKey = process.env.GEMINI_API_KEY;
  if (!apiKey) return res.status(503).json({ error: "Gemini is not configured yet. Add GEMINI_API_KEY to the Vercel project environment variables." });

  const body = typeof req.body === "object" && req.body !== null ? req.body : {};
  const message = String(body.message ?? "").trim().slice(0, MAX_MESSAGE_CHARS);
  const requestedMode = String(body.mode ?? "companion");
  const mode = Object.hasOwn(SYSTEM_PROMPTS, requestedMode) ? requestedMode : "companion";
  const mood = String(body.mood ?? "Steady").slice(0, 40);
  const energy = Math.max(0, Math.min(4, Number(body.energy ?? 2) || 0));
  if (!message) return res.status(400).json({ error: "A message is required." });

  try {
    let sources: ResearchSource[] = [];
    let researchContext = "";
    if (mode === "research") {
      try {
        sources = await searchGoogleNews(message);
        researchContext = sources.length
          ? "\n\nLIVE GOOGLE NEWS RESULTS (limited RSS sample; cite only these exact items):\n" +
            sources.map((source, index) => `[${index + 1}] ${source.title}\nURL: ${source.url}\nPublished: ${source.published || "Date not supplied"}`).join("\n\n")
          : "\n\nNo live news results were returned. Say so clearly; do not invent sources.";
      } catch {
        researchContext = "\n\nLive news search could not be reached. Answer from general knowledge only and clearly say that current sources could not be checked.";
      }
    }

    const prompt = `Check-in mood: ${mood}. Energy level: ${energy}/4.\n\nUser message:\n${message}${researchContext}`;
    const upstream = await fetch(
      `https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(MODEL)}:generateContent`,
      {
        method: "POST",
        headers: { "x-goog-api-key": apiKey, "Content-Type": "application/json" },
        body: JSON.stringify({
          systemInstruction: { parts: [{ text: SYSTEM_PROMPTS[mode] }] },
          contents: [{ role: "user", parts: [{ text: prompt }] }],
          generationConfig: { temperature: 0.65, maxOutputTokens: 900 },
        }),
        signal: AbortSignal.timeout(25_000),
      }
    );

    const data = await upstream.json();
    if (!upstream.ok) {
      const detail = String(data?.error?.message ?? "Gemini API request failed").slice(0, 500);
      if (upstream.status === 401 || upstream.status === 403) {
        return res.status(502).json({ error: "Gemini rejected the server API key. Check GEMINI_API_KEY in Vercel.", detail });
      }
      if (upstream.status === 429) {
        return res.status(429).json({ error: "Gemini free-tier limit reached. Wait for the quota to reset and try again.", detail });
      }
      return res.status(502).json({ error: "Gemini request failed.", detail });
    }

    const reply = extractGeminiText(data);
    if (!reply) return res.status(502).json({ error: "Gemini returned an empty response. Please try again." });

    const sourceFooter = mode === "research" && sources.length
      ? "\n\nSources checked:\n" + sources.map((source, index) => `[${index + 1}] ${source.title}\n${source.url}`).join("\n")
      : "";
    return res.status(200).json({ reply: reply + sourceFooter, provider: "gemini", model: MODEL });
  } catch {
    return res.status(502).json({ error: "Unable to reach Gemini right now. Check the network and try again." });
  }
}
