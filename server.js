const express = require("express");
const cors = require("cors");
const crypto = require("crypto");
const { Pool } = require("pg");

const app = express();
app.use(cors());
app.use(express.json({ limit: "10mb" }));

const PORT = process.env.PORT || 10000;
const YOUTUBE_API_KEY = process.env.YOUTUBE_API_KEY || "";
const GOOGLE_CLIENT_ID = process.env.GOOGLE_CLIENT_ID || "";
const GOOGLE_CLIENT_SECRET = process.env.GOOGLE_CLIENT_SECRET || "";
const GOOGLE_REDIRECT_URI = process.env.GOOGLE_REDIRECT_URI ||
  "https://mahadev-ai-shorts-maker-api.onrender.com/auth/youtube/callback";
const TOKEN_ENCRYPTION_KEY = process.env.TOKEN_ENCRYPTION_KEY || "";
const DATABASE_URL = process.env.DATABASE_URL || "";

const pendingOAuthStates = new Map();
let youtubeTokens = null;
let dbReady = false;
let dbPool = null;

if (DATABASE_URL) {
  dbPool = new Pool({
    connectionString: DATABASE_URL,
    ssl: process.env.NODE_ENV === "production" ? { rejectUnauthorized: false } : false,
    max: 3,
    idleTimeoutMillis: 30000,
    connectionTimeoutMillis: 10000
  });
}

function encryptSecret(value) {
  if (!TOKEN_ENCRYPTION_KEY || !/^[a-f0-9]{64}$/i.test(TOKEN_ENCRYPTION_KEY)) {
    throw new Error("TOKEN_ENCRYPTION_KEY must be a 64-character hexadecimal key.");
  }
  const key = Buffer.from(TOKEN_ENCRYPTION_KEY, "hex");
  const iv = crypto.randomBytes(12);
  const cipher = crypto.createCipheriv("aes-256-gcm", key, iv);
  const encrypted = Buffer.concat([cipher.update(JSON.stringify(value), "utf8"), cipher.final()]);
  return JSON.stringify({
    version: 1,
    iv: iv.toString("base64"),
    tag: cipher.getAuthTag().toString("base64"),
    data: encrypted.toString("base64")
  });
}

function decryptSecret(payload) {
  const item = typeof payload === "string" ? JSON.parse(payload) : payload;
  const key = Buffer.from(TOKEN_ENCRYPTION_KEY, "hex");
  const decipher = crypto.createDecipheriv("aes-256-gcm", key, Buffer.from(item.iv, "base64"));
  decipher.setAuthTag(Buffer.from(item.tag, "base64"));
  const plain = Buffer.concat([
    decipher.update(Buffer.from(item.data, "base64")),
    decipher.final()
  ]);
  return JSON.parse(plain.toString("utf8"));
}

async function initializeTokenStore() {
  if (!dbPool || !TOKEN_ENCRYPTION_KEY) return;
  await dbPool.query(`
    CREATE TABLE IF NOT EXISTS app_secrets (
      secret_name TEXT PRIMARY KEY,
      encrypted_value TEXT NOT NULL,
      updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    )
  `);
  const result = await dbPool.query(
    "SELECT encrypted_value FROM app_secrets WHERE secret_name = $1",
    ["youtube_oauth_tokens"]
  );
  if (result.rows[0]) youtubeTokens = decryptSecret(result.rows[0].encrypted_value);
  dbReady = true;
  console.log("Encrypted token store initialized.");
}

async function saveYoutubeTokens(tokens) {
  if (dbPool && TOKEN_ENCRYPTION_KEY && dbReady) {
    const encrypted = encryptSecret(tokens);
    await dbPool.query(
      `INSERT INTO app_secrets (secret_name, encrypted_value, updated_at)
       VALUES ($1, $2, NOW())
       ON CONFLICT (secret_name)
       DO UPDATE SET encrypted_value = EXCLUDED.encrypted_value, updated_at = NOW()`,
      ["youtube_oauth_tokens", encrypted]
    );
    youtubeTokens = tokens;
    return "postgres_encrypted";
  }
  youtubeTokens = tokens;
  return "memory_only";
}

app.get("/", (_req, res) => {
  res.json({
    name: "Mahadev AI Shorts Maker API",
    status: "running",
    version: "1.2.0",
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
    const nextTokens = {
      access_token: tokens.access_token,
      refresh_token: tokens.refresh_token || youtubeTokens?.refresh_token || null,
      expires_at: Date.now() + (Number(tokens.expires_in || 3600) * 1000),
      scope: tokens.scope || ""
    };
    const storage = await saveYoutubeTokens(nextTokens);
    const message = storage === "postgres_encrypted"
      ? "Your YouTube connection is saved with encrypted persistent storage."
      : "Connected, but token storage is currently memory-only. Configure DATABASE_URL and TOKEN_ENCRYPTION_KEY in Render before production use.";
    res.send("<h2>YouTube connected successfully</h2><p>You may close this page and return to Mahadev AI Shorts Maker.</p><p>" + message + "</p>");
  } catch (err) {
    console.error("OAuth callback failed:", err.message);
    res.status(502).send("Could not complete secure token storage. Check server configuration and logs.");
  }
});

app.get("/api/status/integrations", (_req, res) => {
  res.json({
    youtubeTrending: Boolean(YOUTUBE_API_KEY),
    youtubeOAuthConfigured: Boolean(GOOGLE_CLIENT_ID && GOOGLE_CLIENT_SECRET),
    youtubeConnected: Boolean(youtubeTokens?.refresh_token || youtubeTokens?.access_token),
    youtubeTokenStorage: dbReady ? "postgres_encrypted" : "memory_only_database_not_configured",
    facebookPublishing: "meta_oauth_not_configured",
    clipProcessing: "worker_not_configured"
  });
});

app.listen(PORT, "0.0.0.0", async () => {
  console.log(`Mahadev AI Shorts Maker API listening on ${PORT}`);
  try {
    await initializeTokenStore();
  } catch (error) {
    dbReady = false;
    console.error("Encrypted token store initialization failed:", error.message);
  }
});
