# Mahadev AI Shorts Maker

Android app + Node.js API for YouTube discovery and publishing of videos you own or are licensed to use.

## Current features
- Android dashboard branded **Mahadev AI Shorts Maker**.
- Fetches India's public YouTube most-popular video metadata.
- Google OAuth connection for the user's YouTube channel.
- Encrypted OAuth token storage in PostgreSQL using AES-256-GCM.
- Protected channel-details endpoint.
- Protected YouTube video upload endpoint with title, description and Private / Unlisted / Public visibility.
- Android video picker and upload form.

## Render environment
Required:
- `YOUTUBE_API_KEY`
- `GOOGLE_CLIENT_ID`
- `GOOGLE_CLIENT_SECRET`
- `GOOGLE_REDIRECT_URI=https://mahadev-ai-shorts-maker-api.onrender.com/auth/youtube/callback`
- `DATABASE_URL` (Neon pooled PostgreSQL URL)
- `TOKEN_ENCRYPTION_KEY` (64 hexadecimal characters; keep stable or previously encrypted tokens cannot be decrypted)
- `APP_API_KEY` (long random secret required by the app for protected channel and upload endpoints)

Never commit secrets or put them in source code. If a secret is exposed, rotate it.

## Google OAuth
OAuth consent screen must include the account as a test user while the app is in Testing mode. Authorized redirect URI must exactly match:
`https://mahadev-ai-shorts-maker-api.onrender.com/auth/youtube/callback`

## API endpoints
- `GET /` service information
- `GET /health` health check
- `GET /api/youtube/trending?regionCode=IN&maxResults=20` public trending metadata
- `GET /auth/youtube` start Google OAuth
- `GET /auth/youtube/callback` OAuth callback
- `GET /api/status/integrations` integration status
- `GET /api/youtube/channel` requires `x-app-api-key`
- `POST /api/youtube/upload` multipart form fields `video`, `title`, `description`, `privacyStatus`; requires `x-app-api-key`

## Build Android APK
Pushes to main run the Android build workflow. The debug APK is uploaded as a GitHub Actions artifact named `mahadev-ai-shorts-maker-debug-apk`.

## Important limitations
- Trending API returns metadata only; it does not provide rights to download or repost videos.
- The Android build currently uploads a selected source video. It does not yet auto-generate clips, crop/reframe to 9:16, burn Hindi subtitles, or schedule Shorts.
- Facebook Page OAuth/publishing is not implemented.
- Google OAuth in Testing mode is restricted to added test users. Public release may require Google's verification depending on scopes.
- Render Free services may sleep and have limited CPU/RAM; large video uploads can time out or exceed memory limits.
- Upload only content you own or have explicit permission to publish.
