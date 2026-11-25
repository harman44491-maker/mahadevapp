const express = require("express");
const cors = require("cors");

const app = express();
app.use(cors());
app.use(express.json({ limit: "10mb" }));

const PORT = process.env.PORT || 10000;
const YOUTUBE_API_KEY = process.env.YOUTUBE_API_KEY || "";

app.get("/", (_req, res) => {
  res.json({
    name: "Mahadev AI Shorts Maker API",
    status: "running",
    version: "1.0.0",
    endpoints: ["/health", "/api/youtube/trending"]
  });
});

app.get("/health", (_req, res) => {
  res.json({ ok: true, service: "mahadev-ai-shorts-maker", time: new Date().toISOString() });
});

app.get("/api/youtube/trending", async (req, res) => {
  if (!YOUTUBE_API_KEY) {
    return res.status(503).json({
      error: "YOUTUBE_API_KEY_MISSING",
      message: "Add YOUTUBE_API_KEY in Render Environment settings."
    });
  }

  const regionCode = String(req.query.regionCode || "IN").toUpperCase().slice(0, 2);
  const maxResults = Math.min(Math.max(Number(req.query.maxResults || 20), 1), 50);
  const url = new URL("https://www.googleapis.com/youtube/v3/videos");
  url.search = new URLSearchParams({
    part: "snippet,contentDetails,statistics",
    chart: "mostPopular",
    regionCode,
    maxResults: String(maxResults),
    key: YOUTUBE_API_KEY
  });

  try {
    const response = await fetch(url);
    const data = await response.json();
    if (!response.ok) {
      return res.status(response.status).json({
        error: "YOUTUBE_API_ERROR",
        message: data.error?.message || "YouTube API request failed",
        details: data.error?.errors || []
      });
    }

    res.json({
      regionCode,
      count: data.items?.length || 0,
      videos: (data.items || []).map(video => ({
        id: video.id,
        title: video.snippet?.title || "",
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

// Publishing is intentionally disabled until the channel owner completes OAuth
// and grants the required YouTube/Meta permissions.
app.get("/api/status/integrations", (_req, res) => {
  res.json({
    youtubeTrending: Boolean(YOUTUBE_API_KEY),
    youtubeUpload: "oauth_not_configured",
    facebookPublishing: "meta_oauth_not_configured",
    clipProcessing: "worker_not_configured"
  });
});

app.listen(PORT, "0.0.0.0", () => {
  console.log(`Mahadev AI Shorts Maker API listening on ${PORT}`);
});
