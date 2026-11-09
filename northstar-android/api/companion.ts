import type { VercelRequest, VercelResponse } from "@vercel/node";
import { timingSafeEqual } from "node:crypto";

const PERSONAL_CONTEXT = `Useful background for this single-user assistant: the user is based in India, has hands-on experience with websites, deployment, digital marketing, recruitment and operations, is building a small digital agency, is interested in AI and automation, prefers practical low-cost execution, and wants to develop an original YouTube channel. Treat this as helpful context, not as a rigid identity; do not assume every request relates to work or content creation.`;

const SYSTEM_PROMPTS: Record<string, string> = {
  companion: `You are Northstar, a private and practical life assistant. Be warm, direct, thoughtful and emotionally steady. Help the user think clearly and choose one useful next action. Never manufacture certainty about another person's feelings or intentions. When discussing heartbreak, support boundaries and emotional detachment without repeatedly centering the former partner, encouraging surveillance, or pushing contact. Do not encourage compulsive app use. Prefer real-world action, supportive relationships, healthy routines, learning and meaningful creation. Do not diagnose. If a user expresses imminent danger or intent to hurt themselves, respond compassionately and encourage immediate help from local emergency services and a trusted person.`,
  creator: `You are Northstar Creator Lab. Help the user build an original YouTube identity. Use clear hooks, specific human stories, an earned insight and a useful ending. You may analyse high-level storytelling techniques used by creators, but never imitate a living creator's exact voice, scripts, catchphrases or persona. Prefer ideas the user can honestly document and produce with modest equipment. Give concrete outlines, not empty motivation.`,
  career: `You are Northstar Career Architect. Help the user build practical, income-relevant skills and a credible body of work. Prefer low-cost experiments, portfolio proof, measurable weekly actions and realistic timelines. Never invent job openings, qualifications, market data or guaranteed income. Distinguish verified facts from suggestions.`,
  research: `You are Northstar Research Desk. Use live web search to investigate the user's question. Prioritise primary sources, reputable publications and original research. Give a concise synthesis, explain what is known and uncertain, include source titles and direct URLs, and distinguish facts from interpretation. Prefer discoveries that expand curiosity and practical knowledge. Avoid sensationalism, repetitive breakup content and unsupported health claims.`,
};

function safeTokenMatch(expected: string, received: string): boolean {
  const a = Buffer.from(expected);
  const b = Buffer.from(received);
  return a.length === b.length && timingSafeEqual(a, b);
}

function extractText(data: any): string {
  if (typeof data?.output_text === "string" && data.output_text.trim()) return data.output_text.trim();
  const chunks: string[] = [];
  for (const item of data?.output ?? []) {
    if (item?.type !== "message") continue;
    for (const content of item?.content ?? []) {
      if (content?.type === "output_text" && typeof content?.text === "string") chunks.push(content.text);
    }
  }
  return chunks.join("\n\n").trim();
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
    return res.status(401).json({ error: "Unauthorised device" });
  }

  const apiKey = process.env.XAI_API_KEY;
  if (!apiKey) return res.status(503).json({ error: "Grok service is not configured" });

  const message = String(req.body?.message ?? "").trim().slice(0, 4_000);
  const mode = String(req.body?.mode ?? "companion");
  const mood = String(req.body?.mood ?? "Steady").slice(0, 40);
  const energy = Number(req.body?.energy ?? 2);
  if (!message) return res.status(400).json({ error: "A message is required" });

  try {
    const payload: Record<string, unknown> = {
      model: process.env.XAI_MODEL || "grok-4.7",
      input: [
        { role: "system", content: `${SYSTEM_PROMPTS[mode] || SYSTEM_PROMPTS.companion}\n\n${PERSONAL_CONTEXT}` },
        { role: "user", content: `Check-in mood: ${mood}. Energy level: ${energy}/4.\n\n${message}` },
      ],
      temperature: 0.65,
      max_output_tokens: 850,
      store: false,
    };
    // Live search is opt-in from the Research mode only, so ordinary chat does not incur search-tool usage.
    if (mode === "research") payload.tools = [{ type: "web_search" }];

    const upstream = await fetch("https://api.x.ai/v1/responses", {
      method: "POST",
      headers: {
        Authorization: `Bearer ${apiKey}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify(payload),
    });

    const data = await upstream.json();
    if (!upstream.ok) {
      return res.status(502).json({ error: "Grok request failed", detail: data?.error?.message ?? "Upstream error" });
    }
    const reply = extractText(data);
    if (!reply) return res.status(502).json({ error: "Grok returned an empty response" });

    return res.status(200).json({ reply });
  } catch {
    return res.status(502).json({ error: "Unable to reach Grok right now" });
  }
}
