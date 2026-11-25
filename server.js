const express = require("express");
const cors = require("cors");
const crypto = require("crypto");
const { promisify } = require("util");
const scryptAsync = promisify(crypto.scrypt);
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
const META_APP_ID = process.env.META_APP_ID || "";
const META_APP_SECRET = process.env.META_APP_SECRET || "";
const META_REDIRECT_URI = process.env.META_REDIRECT_URI || "https://mahadev-ai-shorts-maker-api.onrender.com/auth/facebook/callback";
const META_CONFIG_ID = process.env.META_CONFIG_ID || "";
const TOKEN_ENCRYPTION_KEY = process.env.TOKEN_ENCRYPTION_KEY || "";
const DATABASE_URL = process.env.DATABASE_URL || "";
const APP_API_KEY = process.env.APP_API_KEY || "";
const upload = multer({ storage: multer.memoryStorage(), limits: { fileSize: 128 * 1024 * 1024 } });


let youtubeTokens = null;
let facebookConnection = { userToken: null, pages: [], selectedPage: null };
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
  if (!dbPool) return;
  if (!TOKEN_ENCRYPTION_KEY || !/^[a-f0-9]{64}$/i.test(TOKEN_ENCRYPTION_KEY)) {
    throw new Error("TOKEN_ENCRYPTION_KEY must be configured as a 64-character hexadecimal key before account connections can be enabled.");
  }
  await dbPool.query(`
    CREATE TABLE IF NOT EXISTS app_users (
      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      email TEXT NOT NULL UNIQUE,
      password_hash TEXT NOT NULL,
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    )
  `);
  await dbPool.query(`
    CREATE TABLE IF NOT EXISTS app_sessions (
      token_hash TEXT PRIMARY KEY,
      user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
      expires_at TIMESTAMPTZ NOT NULL,
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    )
  `);
  await dbPool.query(`
    CREATE TABLE IF NOT EXISTS oauth_states (
      state_hash TEXT PRIMARY KEY,
      user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
      provider TEXT NOT NULL CHECK (provider IN ('youtube', 'facebook')),
      expires_at TIMESTAMPTZ NOT NULL,
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    )
  `);
  await dbPool.query(`
    CREATE TABLE IF NOT EXISTS user_connections (
      user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
      provider TEXT NOT NULL CHECK (provider IN ('youtube', 'facebook')),
      encrypted_value TEXT NOT NULL,
      updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      PRIMARY KEY (user_id, provider)
    )
  `);
  await dbPool.query(`
    CREATE TABLE IF NOT EXISTS app_secrets (
      secret_name TEXT PRIMARY KEY,
      encrypted_value TEXT NOT NULL,
      updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    )
  `);
  if (TOKEN_ENCRYPTION_KEY && /^[a-f0-9]{64}$/i.test(TOKEN_ENCRYPTION_KEY)) {
    const result = await dbPool.query(
      "SELECT encrypted_value FROM app_secrets WHERE secret_name = $1",
      ["youtube_oauth_tokens"]
    );
    if (result.rows[0]) youtubeTokens = decryptSecret(result.rows[0].encrypted_value);
    const fbResult = await dbPool.query("SELECT encrypted_value FROM app_secrets WHERE secret_name = $1", ["facebook_connection"]);
    if (fbResult.rows[0]) facebookConnection = decryptSecret(fbResult.rows[0].encrypted_value);
  }
  dbReady = true;
  console.log("Encrypted token store initialized.");
}


async function saveUserConnection(userId, provider, value) {
  if (!dbPool || !dbReady || !TOKEN_ENCRYPTION_KEY) throw new Error("SECURE_CONNECTION_STORAGE_NOT_CONFIGURED");
  const encrypted = encryptSecret(value);
  await dbPool.query(`INSERT INTO user_connections (user_id, provider, encrypted_value, updated_at)
    VALUES ($1, $2, $3, NOW()) ON CONFLICT (user_id, provider)
    DO UPDATE SET encrypted_value = EXCLUDED.encrypted_value, updated_at = NOW()`, [userId, provider, encrypted]);
  return "postgres_encrypted";
}
async function getUserConnection(userId, provider) {
  if (!dbPool || !dbReady || !TOKEN_ENCRYPTION_KEY) return null;
  const result = await dbPool.query("SELECT encrypted_value FROM user_connections WHERE user_id = $1 AND provider = $2", [userId, provider]);
  return result.rows[0] ? decryptSecret(result.rows[0].encrypted_value) : null;
}
async function saveFacebookConnection(connection) {
  if (dbPool && TOKEN_ENCRYPTION_KEY && dbReady) {
    const encrypted = encryptSecret(connection);
    await dbPool.query("INSERT INTO app_secrets (secret_name, encrypted_value, updated_at) VALUES ($1, $2, NOW()) ON CONFLICT (secret_name) DO UPDATE SET encrypted_value = EXCLUDED.encrypted_value, updated_at = NOW()", ["facebook_connection", encrypted]);
  }
  facebookConnection = connection;
  return dbPool && TOKEN_ENCRYPTION_KEY && dbReady ? "postgres_encrypted" : "memory_only";
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
    endpoints: ["/health", "/api/auth/signup", "/api/auth/login", "/api/auth/me", "/api/auth/logout", "/api/youtube/trending", "/auth/youtube", "/auth/youtube/callback", "/auth/facebook", "/auth/facebook/callback", "/api/facebook/pages", "/api/facebook/select-page", "/api/youtube/channel", "/api/youtube/upload", "/api/video/process", "/api/facebook/reels", "/api/status/integrations"]
  });
});

app.get("/health", (_req, res) => {
  res.json({ ok: true, service: "mahadev-ai-shorts-maker", time: new Date().toISOString() });
});

const authAttempts = new Map();
function authRateLimit(req, res, next) {
  const key = String(req.ip || req.socket?.remoteAddress || "unknown");
  const now = Date.now();
  const record = authAttempts.get(key) || { count: 0, resetAt: now + 15 * 60 * 1000 };
  if (record.resetAt <= now) { record.count = 0; record.resetAt = now + 15 * 60 * 1000; }
  record.count += 1;
  authAttempts.set(key, record);
  if (record.count > 20) {
    res.set("Retry-After", String(Math.ceil((record.resetAt - now) / 1000)));
    return res.status(429).json({ error: "TOO_MANY_AUTH_ATTEMPTS", message: "Too many login attempts. Please wait and try again." });
  }
  if (authAttempts.size > 5000) {
    for (const [ip, item] of authAttempts) if (item.resetAt <= now) authAttempts.delete(ip);
  }
  next();
}
function normalizeEmail(value) { return String(value || "").trim().toLowerCase(); }
function validPassword(value) { return typeof value === "string" && value.length >= 8 && value.length <= 128; }
async function hashPassword(password) {
  const salt = crypto.randomBytes(16);
  const derived = await scryptAsync(password, salt, 64);
  return salt.toString("hex") + ":" + Buffer.from(derived).toString("hex");
}
async function verifyPassword(password, stored) {
  const parts = String(stored || "").split(":");
  if (parts.length !== 2) return false;
  const salt = Buffer.from(parts[0], "hex");
  const expected = Buffer.from(parts[1], "hex");
  if (!salt.length || expected.length !== 64) return false;
  const actual = Buffer.from(await scryptAsync(password, salt, 64));
  return crypto.timingSafeEqual(actual, expected);
}
function hashSession(token) { return crypto.createHash("sha256").update(token).digest("hex"); }
async function createSession(userId) {
  const token = crypto.randomBytes(32).toString("base64url");
  const expiresAt = new Date(Date.now() + 30 * 24 * 60 * 60 * 1000);
  await dbPool.query("INSERT INTO app_sessions (token_hash, user_id, expires_at) VALUES ($1, $2, $3)", [hashSession(token), userId, expiresAt]);
  return { token, expiresAt };
}
app.post("/api/auth/signup", authRateLimit, async (req, res) => {
  if (!dbPool || !dbReady) return res.status(503).json({ error: "AUTH_DATABASE_UNAVAILABLE", message: "Account service is temporarily unavailable. Please try again later." });
  const email = normalizeEmail(req.body.email);
  const password = req.body.password;
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) || email.length > 254) return res.status(400).json({ error: "INVALID_EMAIL", message: "Enter a valid email address." });
  if (!validPassword(password)) return res.status(400).json({ error: "INVALID_PASSWORD", message: "Password must be 8 to 128 characters." });
  try {
    const passwordHash = await hashPassword(password);
    const inserted = await dbPool.query("INSERT INTO app_users (email, password_hash) VALUES ($1, $2) RETURNING id, email", [email, passwordHash]);
    const session = await createSession(inserted.rows[0].id);
    res.status(201).json({ success: true, user: inserted.rows[0], token: session.token, expiresAt: session.expiresAt.toISOString() });
  } catch (error) {
    if (error.code === "23505") return res.status(409).json({ error: "EMAIL_ALREADY_REGISTERED", message: "An account with this email already exists. Please log in." });
    console.error("Signup failed:", error.message);
    res.status(500).json({ error: "SIGNUP_FAILED", message: "Could not create account. Please try again." });
  }
});
app.post("/api/auth/login", authRateLimit, async (req, res) => {
  if (!dbPool || !dbReady) return res.status(503).json({ error: "AUTH_DATABASE_UNAVAILABLE", message: "Account service is temporarily unavailable. Please try again later." });
  const email = normalizeEmail(req.body.email);
  const password = req.body.password;
  if (!email || typeof password !== "string" || password.length > 128) return res.status(400).json({ error: "INVALID_CREDENTIALS", message: "Enter your email and password." });
  try {
    const found = await dbPool.query("SELECT id, email, password_hash FROM app_users WHERE email = $1", [email]);
    if (!found.rows[0] || !(await verifyPassword(password, found.rows[0].password_hash))) return res.status(401).json({ error: "INVALID_CREDENTIALS", message: "Email or password is incorrect." });
    const session = await createSession(found.rows[0].id);
    res.json({ success: true, user: { id: found.rows[0].id, email: found.rows[0].email }, token: session.token, expiresAt: session.expiresAt.toISOString() });
  } catch (error) {
    console.error("Login failed:", error.message);
    res.status(500).json({ error: "LOGIN_FAILED", message: "Could not log in. Please try again." });
  }
});
async function requireUser(req, res, next) {
  if (!dbPool || !dbReady) return res.status(503).json({ error: "AUTH_DATABASE_UNAVAILABLE" });
  const auth = req.get("authorization") || "";
  const match = auth.match(/^Bearer ([A-Za-z0-9_-]{30,})$/);
  if (!match) return res.status(401).json({ error: "LOGIN_REQUIRED", message: "Please log in to continue." });
  try {
    const result = await dbPool.query("SELECT u.id, u.email FROM app_sessions s JOIN app_users u ON u.id = s.user_id WHERE s.token_hash = $1 AND s.expires_at > NOW()", [hashSession(match[1])]);
    if (!result.rows[0]) return res.status(401).json({ error: "SESSION_INVALID", message: "Your session expired. Please log in again." });
    req.user = result.rows[0];
    req.sessionTokenHash = hashSession(match[1]);
    next();
  } catch (error) { console.error("Session validation failed:", error.message); res.status(503).json({ error: "AUTH_UNAVAILABLE" }); }
}
async function storeOAuthState(provider, state, userId) {
  const stateHash = hashSession(state);
  await dbPool.query("INSERT INTO oauth_states (state_hash, user_id, provider, expires_at) VALUES ($1, $2, $3, NOW() + INTERVAL '10 minutes')", [stateHash, userId, provider]);
}
async function consumeOAuthState(provider, state) {
  const stateHash = hashSession(String(state || ""));
  const result = await dbPool.query("DELETE FROM oauth_states WHERE state_hash = $1 AND provider = $2 AND expires_at > NOW() RETURNING user_id", [stateHash, provider]);
  return result.rows[0] ? { userId: result.rows[0].user_id } : null;
}
app.get("/api/auth/me", requireUser, (req, res) => res.json({ user: req.user }));
app.post("/api/auth/logout", requireUser, async (req, res) => {
  await dbPool.query("DELETE FROM app_sessions WHERE token_hash = $1", [req.sessionTokenHash]);
  res.json({ success: true });
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

app.get("/api/oauth/youtube/url", requireUser, async (req, res) => {
  if (!GOOGLE_CLIENT_ID || !GOOGLE_CLIENT_SECRET) return res.status(503).json({ error: "YOUTUBE_OAUTH_NOT_CONFIGURED" });
  const state = crypto.randomBytes(24).toString("hex");
  await storeOAuthState("youtube", state, req.user.id);
  const authUrl = new URL("https://accounts.google.com/o/oauth2/v2/auth");
  authUrl.search = new URLSearchParams({
    client_id: GOOGLE_CLIENT_ID, redirect_uri: GOOGLE_REDIRECT_URI, response_type: "code",
    scope: "openid email https://www.googleapis.com/auth/youtube.readonly https://www.googleapis.com/auth/youtube.upload",
    access_type: "offline", prompt: "consent", state
  });
  res.json({ url: authUrl.toString() });
});
app.get("/api/oauth/facebook/url", requireUser, async (req, res) => {
  if (!META_APP_ID || !META_APP_SECRET) return res.status(503).json({ error: "FACEBOOK_OAUTH_NOT_CONFIGURED" });
  const state = crypto.randomBytes(32).toString("hex");
  await storeOAuthState("facebook", state, req.user.id);
  const authUrl = new URL("https://www.facebook.com/" + META_GRAPH_VERSION + "/dialog/oauth");
  const params = { client_id: META_APP_ID, redirect_uri: META_REDIRECT_URI, response_type: "code", scope: "pages_show_list,pages_read_engagement,pages_manage_posts", state };
  if (META_CONFIG_ID) params.config_id = META_CONFIG_ID;
  authUrl.search = new URLSearchParams(params);
  res.json({ url: authUrl.toString() });
});
app.get("/auth/youtube", requireUser, async (req, res) => {
  if (!GOOGLE_CLIENT_ID || !GOOGLE_CLIENT_SECRET) {
    return res.status(503).json({
      error: "YOUTUBE_OAUTH_NOT_CONFIGURED",
      message: "Set GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET and GOOGLE_REDIRECT_URI in Render Environment."
    });
  }
  const state = crypto.randomBytes(24).toString("hex");
  await storeOAuthState("youtube", state, req.user.id);
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
  let stateRecord;
  try { stateRecord = await consumeOAuthState("youtube", state); }
  catch (err) { console.error("Google OAuth state lookup failed:", err.message); return res.status(503).send("Secure OAuth state storage is temporarily unavailable. Please try again."); }
  if (!stateRecord) {
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
    const existingTokens = await getUserConnection(stateRecord.userId, "youtube");
    const nextTokens = {
      access_token: tokens.access_token,
      refresh_token: tokens.refresh_token || existingTokens?.refresh_token || null,
      expires_at: Date.now() + (Number(tokens.expires_in || 3600) * 1000),
      scope: tokens.scope || ""
    };
    const storage = await saveUserConnection(stateRecord.userId, "youtube", nextTokens);
    const message = storage === "postgres_encrypted"
      ? "Your YouTube connection is saved with encrypted persistent storage."
      : "Connected, but token storage is currently memory-only. Configure DATABASE_URL and TOKEN_ENCRYPTION_KEY in Render before production use.";
    res.send("<h2>YouTube connected successfully</h2><p>You may close this page and return to Mahadev AI Shorts Maker.</p><p>" + message + "</p>");
  } catch (err) {
    console.error("OAuth callback failed:", err.message);
    res.status(502).send("Could not complete secure token storage. Check server configuration and logs.");
  }
});



app.get("/auth/facebook", requireUser, async (req, res) => {
  if (!META_APP_ID || !META_APP_SECRET) return res.status(503).json({ error: "FACEBOOK_OAUTH_NOT_CONFIGURED", message: "Set META_APP_ID, META_APP_SECRET and META_REDIRECT_URI in Render Environment." });
  const state = crypto.randomBytes(32).toString("hex");
  await storeOAuthState("facebook", state, req.user.id);
  const authUrl = new URL("https://www.facebook.com/" + META_GRAPH_VERSION + "/dialog/oauth");
  const params = { client_id: META_APP_ID, redirect_uri: META_REDIRECT_URI, response_type: "code", scope: "pages_show_list,pages_read_engagement,pages_manage_posts", state };
  if (META_CONFIG_ID) params.config_id = META_CONFIG_ID;
  authUrl.search = new URLSearchParams(params);
  res.redirect(authUrl.toString());
});

app.get("/auth/facebook/callback", async (req, res) => {
  const { code, state, error, error_description: errorDescription } = req.query;
  let stateRecord;
  try { stateRecord = await consumeOAuthState("facebook", state); }
  catch (err) { console.error("Facebook OAuth state lookup failed:", err.message); return res.status(503).send("Secure OAuth state storage is temporarily unavailable. Please try again."); }
  if (error) return res.status(400).send("Facebook authorization was not completed: " + String(errorDescription || error));
  if (!stateRecord) return res.status(400).send("Invalid or expired Facebook OAuth state. Start again at /auth/facebook.");
  if (!code) return res.status(400).send("Missing Facebook authorization code.");
  let oauthStage = "initial_code_exchange";
  try {
    const tokenUrl = new URL("https://graph.facebook.com/" + META_GRAPH_VERSION + "/oauth/access_token");
    tokenUrl.search = new URLSearchParams({ client_id: META_APP_ID, client_secret: META_APP_SECRET, redirect_uri: META_REDIRECT_URI, code: String(code) });
    const tokenResponse = await fetch(tokenUrl);
    const tokenData = await tokenResponse.json();
    if (!tokenResponse.ok || !tokenData.access_token) {
      const metaError = tokenData.error || {};
      const detail = [metaError.message || "Facebook token exchange failed.", metaError.code ? "Meta code " + metaError.code : "", metaError.type ? "type " + metaError.type : ""].filter(Boolean).join(" | ");
      const err = new Error(detail);
      err.metaCode = metaError.code || null;
      err.metaType = metaError.type || null;
      err.fbtraceId = metaError.fbtrace_id || null;
      err.httpStatus = tokenResponse.status;
      throw err;
    }
    let userToken = tokenData.access_token;
    oauthStage = "long_lived_token_exchange";
    const exchangeUrl = new URL("https://graph.facebook.com/" + META_GRAPH_VERSION + "/oauth/access_token");
    exchangeUrl.search = new URLSearchParams({ grant_type: "fb_exchange_token", client_id: META_APP_ID, client_secret: META_APP_SECRET, fb_exchange_token: userToken });
    const exchangeResponse = await fetch(exchangeUrl);
    const exchangeData = await exchangeResponse.json();
    if (exchangeResponse.ok && exchangeData.access_token) userToken = exchangeData.access_token;
    else if (!exchangeResponse.ok) {
      const metaError = exchangeData.error || {};
      console.error("Facebook long-lived token exchange rejected:", JSON.stringify({
        httpStatus: exchangeResponse.status, metaCode: metaError.code || null,
        metaType: metaError.type || null, fbtraceId: metaError.fbtrace_id || null,
        message: metaError.message || "No Meta error message"
      }));
    }
    oauthStage = "page_list_lookup";
    const accountsUrl = new URL("https://graph.facebook.com/" + META_GRAPH_VERSION + "/me/accounts");
    accountsUrl.search = new URLSearchParams({ fields: "id,name,access_token,tasks", access_token: userToken });
    const accountsResponse = await fetch(accountsUrl);
    const accountsData = await accountsResponse.json();
    if (!accountsResponse.ok) {
      const metaError = accountsData.error || {};
      const err = new Error(metaError.message || "Could not retrieve Facebook Pages.");
      err.metaCode = metaError.code || null;
      err.metaType = metaError.type || null;
      err.fbtraceId = metaError.fbtrace_id || null;
      err.httpStatus = accountsResponse.status;
      throw err;
    }
    const pages = (accountsData.data || []).map(p => ({ id: p.id, name: p.name, access_token: p.access_token, tasks: p.tasks || [] }));
    const storage = await saveUserConnection(stateRecord.userId, "facebook", { userToken, pages, selectedPage: null });
    res.send("<h2>Facebook connected</h2><p>" + pages.length + " Page(s) found. Return to Mahadev AI Shorts Maker and select the Page to publish Reels.</p><p>Token storage: " + storage + ".</p>");
  } catch (err) {
    console.error("Facebook OAuth callback failed:", JSON.stringify({
      stage: oauthStage, message: err.message, httpStatus: err.httpStatus || null,
      metaCode: err.metaCode || null, metaType: err.metaType || null,
      fbtraceId: err.fbtraceId || null
    }));
    res.status(502).send("Facebook connection failed during " + oauthStage + ". Check Render logs for the Meta error code; no credentials are logged.");
  }
});

app.get("/api/facebook/pages", requireUser, async (req, res) => {
  const connection = await getUserConnection(req.user.id, "facebook");
  res.json({ connected: Boolean(connection?.userToken), pages: (connection?.pages || []).map(p => ({ id: p.id, name: p.name, tasks: p.tasks })) });
});

app.post("/api/facebook/select-page", requireUser, async (req, res) => {
  const connection = await getUserConnection(req.user.id, "facebook");
  const pageId = String(req.body.pageId || "");
  const page = (connection?.pages || []).find(p => p.id === pageId);
  if (!page) return res.status(404).json({ error: "FACEBOOK_PAGE_NOT_FOUND" });
  if (!(page.tasks || []).some(task => ["CREATE_CONTENT", "MANAGE"].includes(task))) return res.status(403).json({ error: "FACEBOOK_PAGE_PERMISSION_MISSING", message: "The selected Page does not grant content creation or management access." });
  const next = { ...connection, selectedPage: { id: page.id, name: page.name, access_token: page.access_token } };
  const storage = await saveUserConnection(req.user.id, "facebook", next);
  res.json({ success: true, page: { id: page.id, name: page.name }, tokenStorage: storage });
});

function requireAppKey(req, res, next) {
  const supplied = req.get("x-app-api-key") || "";
  if (!APP_API_KEY || supplied.length !== APP_API_KEY.length ||
      !crypto.timingSafeEqual(Buffer.from(supplied), Buffer.from(APP_API_KEY))) {
    return res.status(401).json({ error: "UNAUTHORIZED", message: "A valid x-app-api-key header is required." });
  }
  next();
}
async function getYoutubeClient(userId) {
  const youtubeTokens = await getUserConnection(userId, "youtube");
  if (!youtubeTokens?.refresh_token) throw new Error("YOUTUBE_NOT_CONNECTED");
  const oauth = new google.auth.OAuth2(GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET, GOOGLE_REDIRECT_URI);
  oauth.setCredentials({ refresh_token: youtubeTokens.refresh_token });
  oauth.on("tokens", async refreshed => {
    try {
      await saveUserConnection(userId, "youtube", {
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
app.get("/api/youtube/channel", requireUser, async (req, res) => {
  try {
    const youtube = await getYoutubeClient(req.user.id);
    const result = await youtube.channels.list({ part: ["snippet", "statistics"], mine: true });
    const channel = result.data.items?.[0];
    if (!channel) return res.status(404).json({ error: "CHANNEL_NOT_FOUND" });
    res.json({ id: channel.id, title: channel.snippet?.title || "", thumbnail: channel.snippet?.thumbnails?.default?.url || null, subscribers: channel.statistics?.subscriberCount || "0", videos: channel.statistics?.videoCount || "0", views: channel.statistics?.viewCount || "0" });
  } catch (e) {
    console.error("YouTube channel lookup failed:", e.message);
    res.status(e.message === "YOUTUBE_NOT_CONNECTED" ? 409 : 502).json({ error: "YOUTUBE_CHANNEL_ERROR", message: e.message });
  }
});
app.post("/api/youtube/upload", requireUser, upload.single("video"), async (req, res) => {
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
    const youtube = await getYoutubeClient(req.user.id);
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

app.post("/api/video/process", requireUser, upload.single("video"), async (req, res) => {
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

app.post("/api/facebook/reels", requireUser, upload.single("video"), async (req, res) => {
  const facebookConnection = await getUserConnection(req.user.id, "facebook");
  const pageId = facebookConnection?.selectedPage?.id || "";
  const pageToken = facebookConnection?.selectedPage?.access_token || "";
  if (!pageId || !pageToken) return res.status(409).json({ error: "FACEBOOK_PAGE_NOT_SELECTED", message: "Connect Facebook and select a Page before publishing." });
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

app.get("/api/status/integrations", requireUser, async (req, res) => {
  const youtubeTokens = await getUserConnection(req.user.id, "youtube");
  const facebookConnection = await getUserConnection(req.user.id, "facebook");
  res.json({
    youtubeTrending: Boolean(YOUTUBE_API_KEY),
    youtubeOAuthConfigured: Boolean(GOOGLE_CLIENT_ID && GOOGLE_CLIENT_SECRET),
    youtubeConnected: Boolean(youtubeTokens?.refresh_token || youtubeTokens?.access_token),
    youtubeTokenStorage: youtubeTokens ? "postgres_encrypted" : "not_connected",
    facebookOAuthConfigured: Boolean(META_APP_ID && META_APP_SECRET),
    facebookConnected: Boolean(facebookConnection?.userToken),
    facebookPageSelected: Boolean(facebookConnection?.selectedPage?.id),
    facebookTokenStorage: facebookConnection ? "postgres_encrypted" : "not_connected",
    facebookPublishing: Boolean(facebookConnection?.selectedPage?.id && facebookConnection?.selectedPage?.access_token),
    hindiSpeechToText: Boolean(process.env.GOOGLE_SERVICE_ACCOUNT_JSON),
    clipProcessing: Boolean(process.env.GOOGLE_SERVICE_ACCOUNT_JSON),
    scheduledYouTubePublishing: Boolean(youtubeTokens?.refresh_token),
    scheduledFacebookPublishing: Boolean(facebookConnection?.selectedPage?.id && facebookConnection?.selectedPage?.access_token)
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
