import type { VercelRequest, VercelResponse } from "@vercel/node";

export default function handler(req: VercelRequest, res: VercelResponse) {
  res.setHeader("Cache-Control", "no-store");
  if (req.method !== "GET") return res.status(405).json({ error: "Method not allowed" });
  return res.status(200).json({
    service: "STARBOY Companion API",
    provider: "Google Gemini",
    model: process.env.GEMINI_MODEL || "gemini-3.5-flash-lite",
    ready: Boolean(process.env.GEMINI_API_KEY && process.env.APP_ACCESS_TOKEN),
    apiKeyConfigured: Boolean(process.env.GEMINI_API_KEY),
    accessTokenConfigured: Boolean(process.env.APP_ACCESS_TOKEN),
  });
}
