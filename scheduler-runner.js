const API_BASE = process.env.AUTO_PUBLISH_API_BASE || "https://mahadev-ai-shorts-maker-api.onrender.com";
const TOKEN = process.env.AUTO_SCHEDULER_TOKEN || "";
if (!TOKEN) {
  console.error("AUTO_SCHEDULER_TOKEN is required.");
  process.exit(1);
}
(async () => {
  const response = await fetch(API_BASE + "/internal/auto/publish", {
    method: "POST",
    headers: { "x-auto-scheduler-token": TOKEN, "Content-Type": "application/json" },
    body: "{}"
  });
  const body = await response.text();
  console.log("Auto publish scheduler response:", response.status, body.slice(0, 6000));
  if (!response.ok) process.exit(1);
})().catch(error => {
  console.error("Auto publish scheduler request failed:", error.message);
  process.exit(1);
});
