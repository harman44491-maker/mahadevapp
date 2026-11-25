# Mahadev AI Shorts Maker — Backend

Node.js/Express API starter for the Android app.

## Render environment variables
- `YOUTUBE_API_KEY` — YouTube Data API v3 key
- `GOOGLE_CLIENT_ID` — OAuth 2.0 Web application client ID
- `GOOGLE_CLIENT_SECRET` — OAuth 2.0 client secret
- `GOOGLE_REDIRECT_URI` — `https://mahadev-ai-shorts-maker-api.onrender.com/auth/youtube/callback`

Never commit API keys or OAuth secrets.

## YouTube OAuth setup
1. In Google Cloud, create an OAuth client of type **Web application**.
2. Add this exact Authorized redirect URI:
   `https://mahadev-ai-shorts-maker-api.onrender.com/auth/youtube/callback`
3. Add the Client ID and Client Secret to Render Environment using the variable names above.
4. Open `/auth/youtube` to begin channel authorization.
5. Check `/api/status/integrations` for connection status.

## Endpoints
- `GET /` — service info
- `GET /health` — health check
- `GET /api/youtube/trending?regionCode=IN&maxResults=20` — public YouTube most-popular metadata
- `GET /auth/youtube` — start YouTube OAuth
- `GET /auth/youtube/callback` — OAuth callback
- `GET /api/status/integrations` — integration readiness

## Important limitations
This starter retrieves public YouTube video metadata and supports the OAuth authorization-code handshake. OAuth tokens are currently stored in memory only and are lost on service restart; add encrypted persistent storage before production use. Actual video upload endpoints, FFmpeg clip processing, and Meta Page publishing are not implemented yet. Do not download or repost other creators' videos without permission; use owned or licensed content.
