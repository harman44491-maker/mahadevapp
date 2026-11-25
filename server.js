const express = require("express");
const cors = require("cors");
const crypto = require("crypto");

const app = express();
app.use(cors());
app.use(express.json({ limit: "10mb" }));

const PORT = process.env.PORT || 10000;
const YOUTUBE_API_KEY = process.env.YOUTUBE_API_KEY || "";
const GOOGLE_CLIENT_ID = process.env.GOOGLE_CLIENT_ID || "";
const GOOGLE_CLIENT_SECRET = process.env.GOOGLE_CLIENT_SECRET || "";
const GOOGLE_REDIRECT_URI = process.env.GOOGLE_REDIRECT_URI ||
  "https://mahadev-ai-shorts-maker-api.onrender.com/auth/youtube/callback";

const pendingOAuthStates = new Map();
let youtubeTokens = null; // In-memory only; use encrypted persistent storage before production.

app.get("/", (_req, res) => {
  res.json({
    name: "Mahadev AI Shorts Maker API",
    status: "running",
    version: "1.1.0",
    endpoints: ["/health", "/api/youtube/trending", "/auth/youtube", "/auth/youtube/callback", "/api/status/integrations"]
  });
});

app.get("/health", (_req, res) => {
  res.json({ ok: true, service: "mahadev-ai-shorts-maker", time: new Date().toISOString() });
});

app.get("/api/youtube/trending", async (req, res) => {
  if (!YOUTUBE_API_KEY) {
    return res.status(503).json({ error: "YOUTUBE_API_KEY_MISSING", message: "Add YOUTUBE_API_KEY in Render Environment settings." });
  }
  const regionCode = String(req.query.regionCode || "IN").toUpperCase().slice(0, 2);
  const maxResults = Math.min(Math.max(Number(req.query.maxResults || 20), 1), 50);
  const url = new URL("https://www.googleapis.com/youtube/v3/videos");
  url.search = new URLSearchParams({
    part: "snippet,contentDetails,statistics", chart: "mostPopular",
    regionCode, maxResults: String(maxResults), key: YOUTUBE_API_KEY
  });
  try {
    const response = await fetch(url);
    const data = await response.json();
    if (!response.ok) return res.status(response.status).json({
      error: "YOUTUBE_API_ERROR", message: data.error?.message || "YouTube API request failed"
    });
    res.json({
      regionCode, count: data.items?.length || 0,
      videos: (data.items || []).map(video => ({
        id: video.id, title: video.snippet?.title || "",
        description: video.snippet?.description || "",
        channelTitle: video.snippet?.channelTitle || "",
        publishedAt: video.snippet?.publishedAt || null,
        thumbnail: video.snippet?.thumbnails?.high?.url || video.snippet?.thumbnails?.default?.url || null,
        duration: video.contentDetails?.duration || null,
        viewCount: video.statistics?.viewCount || "0",
        likeCount: video.statistics?.likeCount || "0",
        url: "https://www.youtube.com/watch?v=" + video.id
      }))
    });
  } catch (error) {
    res.status(502).json({ error: "UPSTREAM_ERROR", message: error.message });
  }
});

app.get("/auth/youtube", (req, res) => {
  if (!GOOGLE_CLIENT_ID || !GOOGLE_CLIENT_SECRET) {
    return res.status(503).json({
      error: "YOUTUBE_OAUTH_NOT_CONFIGURED",
      message: "Set GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET and GOOGLE_REDIRECT_URI in Render Environment."
    });
  }
  const state = crypto.randomBytes(24).toString("hex");
  pendingOAuthStates.set(state, Date.now() + 10 * 60 * 1000);
  const authUrl = new URL("https://accounts.google.com/o/oauth2/v2/auth");
  authUrl.search = new URLSearchParams({
    client_id: GOOGLE_CLIENT_ID,
    redirect_uri: GOOGLE_REDIRECT_URI,
    response_type: "code",
    scope: "openid email https://www.googleapis.com/auth/youtube.readonly https://www.googleapis.com/auth/youtube.upload",
    access_type: "offline",
    prompt: "consent",
    state
  });
  res.redirect(authUrl.toString());
});

app.get("/auth/youtube/callback", async (req, res) => {
  const { code, state, error } = req.query;
  if (error) return res.status(400).send("Google authorization was not completed: " + String(error));
  const expiresAt = pendingOAuthStates.get(String(state || ""));
  pendingOAuthStates.delete(String(state || ""));
  if (!expiresAt || expiresAt < Date.now()) {
    return res.status(400).send("Invalid or expired OAuth state. Please start again at /auth/youtube.");
  }
  if (!code) return res.status(400).send("Missing authorization code.");
  try {
    const response = await fetch("https://oauth2.googleapis.com/token", {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({
        code: String(code),
        client_id: GOOGLE_CLIENT_ID,
        client_secret: GOOGLE_CLIENT_SECRET,
        redirect_uri: GOOGLE_REDIRECT_URI,
        grant_type: "authorization_code"
      })
    });
    const tokens = await response.json();
    if (!response.ok) return res.status(502).json({
      error: "OAUTH_TOKEN_EXCHANGE_FAILED",
      message: tokens.error_description || tokens.error || "Token exchange failed"
    });
    youtubeTokens = {
      access_token: tokens.access_token,
      refresh_token: tokens.refresh_token || youtubeTokens?.refresh_token || null,
      expires_at: Date.now() + (Number(tokens.expires_in || 3600) * 1000),
      scope: tokens.scope || ""
    };
    res.send("<h2>YouTube connected successfully</h2><p>You may close this page and return to Mahadev AI Shorts Maker.</p><p>Note: this starter currently keeps tokens in memory; reconnect after a server restart. Persistent encrypted token storage must be added before production use.</p>");
  } catch (err) {
    res.status(502).send("Could not exchange OAuth code: " + err.message);
  }
});

app.get("/api/status/integrations", (_req, res) => {
  res.json({
    youtubeTrending: Boolean(YOUTUBE_API_KEY),
    youtubeOAuthConfigured: Boolean(GOOGLE_CLIENT_ID && GOOGLE_CLIENT_SECRET),
    youtubeConnected: Boolean(youtubeTokens?.refresh_token || youtubeTokens?.access_token),
    youtubeTokenStorage: "memory_only_restart_will_disconnect",
    facebookPublishing: "meta_oauth_not_configured",
    clipProcessing: "worker_not_configured"
  });
});

app.listen(PORT, "0.0.0.0", () => {
  console.log(`Mahadev AI Shorts Maker API listening on ${PORT}`);
});
