# Mahadev AI Shorts Maker — Backend

Node.js/Express API starter for the Android app.

## Deploy on Render
- Runtime: Node
- Build command: `npm install`
- Start command: `npm start`
- Health check: `/health`
- Add `YOUTUBE_API_KEY` in Render Environment (never commit API keys).

## Endpoints
- `GET /` — service info
- `GET /health` — health check
- `GET /api/youtube/trending?regionCode=IN&maxResults=20` — YouTube most-popular video metadata
- `GET /api/status/integrations` — integration readiness

## Current scope
This backend retrieves public YouTube video metadata only. It does not download or re-upload other creators' videos. Clip generation, FFmpeg processing, YouTube OAuth upload, and Meta Page publishing are not enabled yet; they require additional implementation and account authorization.
