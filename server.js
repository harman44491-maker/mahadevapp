const express = require("express");
const cors = require("cors");
const crypto = require("crypto");
const { Pool } = require("pg");
const { google } = require("googleapis");
const multer = require("multer");
const { Readable } = require("stream");
const { makeHindiShort } = require("./video-processing");
const META_GRAPH_VERSION = process.env.META_GRAPH_VERSION || "v27.0";

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
const APP_API_KEY = process.env.APP_API_KEY || "";
const upload = multer({ storage: multer.memoryStorage(), limits: { fileSize: 128 * 1024 * 1024 } });

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
    endpoints: ["/health", "/api/youtube/trending", "/auth/youtube", "/auth/youtube/callback", "/api/youtube/channel", "/api/youtube/upload", "/api/video/process", "/api/facebook/reels", "/api/status/integrations"]
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


function requireAppKey(req, res, next) {
  const supplied = req.get("x-app-api-key") || "";
  if (!APP_API_KEY || supplied.length !== APP_API_KEY.length ||
      !crypto.timingSafeEqual(Buffer.from(supplied), Buffer.from(APP_API_KEY))) {
    return res.status(401).json({ error: "UNAUTHORIZED", message: "A valid x-app-api-key header is required." });
  }
  next();
}
async function getYoutubeClient() {
  if (!youtubeTokens?.refresh_token) throw new Error("YOUTUBE_NOT_CONNECTED");
  const oauth = new google.auth.OAuth2(GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET, GOOGLE_REDIRECT_URI);
  oauth.setCredentials({ refresh_token: youtubeTokens.refresh_token });
  oauth.on("tokens", async refreshed => {
    try {
      await saveYoutubeTokens({
        ...youtubeTokens,
        access_token: refreshed.access_token || youtubeTokens.access_token,
        refresh_token: refreshed.refresh_token || youtubeTokens.refresh_token,
        expires_at: refreshed.expiry_date || youtubeTokens.expires_at,
        scope: refreshed.scope || youtubeTokens.scope
      });
    } catch (e) { console.error("Could not persist refreshed YouTube token:", e.message); }
  });
  await oauth.getAccessToken();
  return google.youtube({ version: "v3", auth: oauth });
}
app.get("/api/youtube/channel", requireAppKey, async (_req, res) => {
  try {
    const youtube = await getYoutubeClient();
    const result = await youtube.channels.list({ part: ["snippet", "statistics"], mine: true });
    const channel = result.data.items?.[0];
    if (!channel) return res.status(404).json({ error: "CHANNEL_NOT_FOUND" });
    res.json({ id: channel.id, title: channel.snippet?.title || "", thumbnail: channel.snippet?.thumbnails?.default?.url || null, subscribers: channel.statistics?.subscriberCount || "0", videos: channel.statistics?.videoCount || "0", views: channel.statistics?.viewCount || "0" });
  } catch (e) {
    console.error("YouTube channel lookup failed:", e.message);
    res.status(e.message === "YOUTUBE_NOT_CONNECTED" ? 409 : 502).json({ error: "YOUTUBE_CHANNEL_ERROR", message: e.message });
  }
});
app.post("/api/youtube/upload", requireAppKey, upload.single("video"), async (req, res) => {
  if (!req.file) return res.status(400).json({ error: "VIDEO_REQUIRED", message: "Send a video file in multipart field 'video'." });
  const title = String(req.body.title || "").trim();
  const description = String(req.body.description || "").trim();
  const privacyStatus = String(req.body.privacyStatus || "private").toLowerCase();
  const publishAtRaw = String(req.body.publishAt || "").trim();
  let publishAt = null;
  if (publishAtRaw) { publishAt = new Date(publishAtRaw); if (Number.isNaN(publishAt.getTime()) || publishAt.getTime() < Date.now() + 10 * 60 * 1000) return res.status(400).json({ error: "INVALID_SCHEDULE_TIME", message: "Schedule at least 10 minutes in the future." }); }
  if (publishAt && privacyStatus !== "private") return res.status(400).json({ error: "SCHEDULE_REQUIRES_PRIVATE", message: "YouTube scheduled uploads must be private until their publish time." });
  if (!title) return res.status(400).json({ error: "TITLE_REQUIRED" });
  if (!["private", "unlisted", "public"].includes(privacyStatus)) return res.status(400).json({ error: "INVALID_PRIVACY_STATUS" });
  try {
    const youtube = await getYoutubeClient();
    const result = await youtube.videos.insert({
      part: ["snippet", "status"],
      requestBody: { snippet: { title: title.slice(0, 100), description: description.slice(0, 5000), categoryId: "22" }, status: { privacyStatus, selfDeclaredMadeForKids: false, ...(publishAt ? { publishAt: publishAt.toISOString() } : {}) } },
      media: { body: Readable.from(req.file.buffer) }
    });
    res.status(201).json({ success: true, id: result.data.id, title: result.data.snippet?.title || title, privacyStatus: result.data.status?.privacyStatus || privacyStatus, publishAt: publishAt ? publishAt.toISOString() : null, url: result.data.id ? "https://www.youtube.com/watch?v=" + result.data.id : null });
  } catch (e) {
    console.error("YouTube upload failed:", e.message);
    res.status(502).json({ error: "YOUTUBE_UPLOAD_FAILED", message: e.message });
  }
});

app.post("/api/video/process", requireAppKey, upload.single("video"), async (req, res) => {
  if (!req.file) return res.status(400).json({ error: "VIDEO_REQUIRED" });
  if (!req.file.mimetype.startsWith("video/")) return res.status(415).json({ error: "VIDEO_TYPE_REQUIRED" });
  try {
    const result = await makeHindiShort(req.file.buffer, {
      startSeconds: req.body.startSeconds,
      durationSeconds: req.body.durationSeconds
    });
    res.set({
      "Content-Type": "video/mp4",
      "Content-Disposition": 'attachment; filename="mahadev-hindi-short.mp4"',
      "X-Short-Duration": String(result.durationSeconds),
      "X-Caption-Language": "hi-IN"
    });
    res.status(200).send(result.buffer);
  } catch (e) {
    console.error("Short processing failed:", e.message);
    const status = e.message.includes("GOOGLE_SERVICE_ACCOUNT_JSON") ? 503 : 422;
    res.status(status).json({ error: "SHORT_PROCESSING_FAILED", message: e.message });
  }
});

app.post("/api/facebook/reels", requireAppKey, upload.single("video"), async (req, res) => {
  const pageId = process.env.FACEBOOK_PAGE_ID || "";
  const pageToken = process.env.FACEBOOK_PAGE_ACCESS_TOKEN || "";
  if (!pageId || !pageToken) return res.status(503).json({ error: "FACEBOOK_NOT_CONFIGURED", message: "Add FACEBOOK_PAGE_ID and FACEBOOK_PAGE_ACCESS_TOKEN in Render after granting Page publishing permissions." });
  if (!req.file) return res.status(400).json({ error: "VIDEO_REQUIRED" });
  const title = String(req.body.title || "Mahadev AI Short").slice(0, 100);
  const description = String(req.body.description || "").slice(0, 5000);
  const publishAtRaw = String(req.body.publishAt || "").trim();
  let scheduledAt = null;
  if (publishAtRaw) {
    scheduledAt = new Date(publishAtRaw);
    if (Number.isNaN(scheduledAt.getTime()) || scheduledAt.getTime() < Date.now() + 10 * 60 * 1000 || scheduledAt.getTime() > Date.now() + 75 * 24 * 60 * 60 * 1000) {
      return res.status(400).json({ error: "INVALID_SCHEDULE_TIME", message: "Facebook Reel scheduling must be 10 minutes to 75 days ahead." });
    }
  }
  try {
    const base = "https://graph.facebook.com/" + META_GRAPH_VERSION + "/" + encodeURIComponent(pageId) + "/video_reels";
    const startUrl = new URL(base);
    startUrl.searchParams.set("access_token", pageToken);
    startUrl.searchParams.set("upload_phase", "START");
    const startResponse = await fetch(startUrl, { method: "POST" });
    const startData = await startResponse.json();
    if (!startResponse.ok || !startData.video_id || !startData.upload_url) throw new Error(startData.error?.message || "Facebook Reel upload could not start.");
    const uploadResponse = await fetch(startData.upload_url, {
      method: "POST",
      headers: { Authorization: "OAuth " + pageToken, "offset": "0", "file_size": String(req.file.size), "Content-Type": "application/octet-stream" },
      body: req.file.buffer
    });
    const uploadData = await uploadResponse.json().catch(() => ({}));
    if (!uploadResponse.ok) throw new Error(uploadData.error?.message || "Facebook Reel binary upload failed.");
    const finishUrl = new URL(base);
    finishUrl.searchParams.set("access_token", pageToken);
    finishUrl.searchParams.set("upload_phase", "FINISH");
    finishUrl.searchParams.set("video_id", String(startData.video_id));
    finishUrl.searchParams.set("video_state", scheduledAt ? "SCHEDULED" : "PUBLISHED");
    finishUrl.searchParams.set("title", title);
    finishUrl.searchParams.set("description", description);
    if (scheduledAt) finishUrl.searchParams.set("scheduled_publish_time", String(Math.floor(scheduledAt.getTime() / 1000)));
    const finishResponse = await fetch(finishUrl, { method: "POST" });
    const finishData = await finishResponse.json();
    if (!finishResponse.ok || finishData.success === false) throw new Error(finishData.error?.message || "Facebook Reel publish step failed.");
    res.status(201).json({ success: true, platform: "facebook_page_reel", videoId: startData.video_id, scheduledAt: scheduledAt ? scheduledAt.toISOString() : null, result: finishData });
  } catch (e) {
    console.error("Facebook Reel publish failed:", e.message);
    res.status(502).json({ error: "FACEBOOK_REEL_FAILED", message: e.message });
  }
});

app.get("/api/status/integrations", (_req, res) => {
  res.json({
    youtubeTrending: Boolean(YOUTUBE_API_KEY),
    youtubeOAuthConfigured: Boolean(GOOGLE_CLIENT_ID && GOOGLE_CLIENT_SECRET),
    youtubeConnected: Boolean(youtubeTokens?.refresh_token || youtubeTokens?.access_token),
    youtubeUploadConfigured: Boolean(APP_API_KEY),
    youtubeTokenStorage: dbReady ? "postgres_encrypted" : "memory_only_database_not_configured",
    facebookPublishing: Boolean(process.env.FACEBOOK_PAGE_ID && process.env.FACEBOOK_PAGE_ACCESS_TOKEN),
    hindiSpeechToText: Boolean(process.env.GOOGLE_SERVICE_ACCOUNT_JSON),
    clipProcessing: Boolean(process.env.GOOGLE_SERVICE_ACCOUNT_JSON && process.env.GOOGLE_SERVICE_ACCOUNT_JSON),
    scheduledYouTubePublishing: Boolean(youtubeTokens?.refresh_token),
    scheduledFacebookPublishing: Boolean(process.env.FACEBOOK_PAGE_ID && process.env.FACEBOOK_PAGE_ACCESS_TOKEN)
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
