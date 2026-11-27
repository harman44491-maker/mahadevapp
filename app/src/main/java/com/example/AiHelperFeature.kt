package com.example

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

@Composable
fun AiHelperFeature(sessionToken: String) {
    val scope = rememberCoroutineScope()
    var prompt by remember { mutableStateOf("") }
    var taskType by remember { mutableStateOf("General") }
    var answer by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Prompts, captions, scripts, ideas, summaries, translation, planning aur simple code mein madad.") }
    var busy by remember { mutableStateOf(false) }
    val http = remember { OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).readTimeout(90, TimeUnit.SECONDS).build() }
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("AI Helper • Any Safe Task", style = MaterialTheme.typography.titleLarge)
            Text("Jo kaam chahiye, simple language mein likhein. AI prompt, content, ideas ya explanation bana dega. Free tier: 10 requests/day.")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("General", "Prompt", "Video", "Study", "Writing", "Code").forEach { type ->
                    FilterChip(selected = taskType == type, onClick = { taskType = type }, enabled = !busy, label = { Text(type) })
                }
            }
            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6,
                enabled = !busy,
                label = { Text("Aapko kya karwana hai?") },
                placeholder = { Text("Example: Mere liye ek realistic 9:16 funny video prompt banao…") }
            )
            Button(
                enabled = !busy && sessionToken.isNotBlank() && prompt.trim().length >= 3,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    scope.launch {
                        busy = true
                        answer = ""
                        status = "AI jawab taiyar kar raha hai…"
                        try {
                            val body = JSONObject().put("prompt", prompt.trim()).put("taskType", taskType).toString()
                            val request = Request.Builder()
                                .url("https://mahadev-ai-shorts-maker-api.onrender.com/api/ai/helper")
                                .header("Authorization", "Bearer $sessionToken")
                                .post(body.toRequestBody("application/json".toMediaType()))
                                .build()
                            val responseText = withContext(Dispatchers.IO) {
                                http.newCall(request).execute().use { response ->
                                    val text = response.body?.string().orEmpty()
                                    if (!response.isSuccessful) {
                                        val message = runCatching { JSONObject(text).optString("message") }.getOrNull()
                                        error(message?.takeIf { it.isNotBlank() } ?: "AI Helper unavailable (${response.code}).")
                                    }
                                    text
                                }
                            }
                            val result = JSONObject(responseText)
                            answer = result.optString("answer")
                            status = "Free requests remaining today: ${result.optInt("remainingToday")}"
                        } catch (e: Exception) {
                            status = e.message ?: "AI Helper abhi available nahi hai."
                        } finally {
                            busy = false
                        }
                    }
                }
            ) { Text(if (busy) "Please wait…" else "Ask AI Helper") }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            if (answer.isNotBlank()) {
                HorizontalDivider()
                Text("AI Response", style = MaterialTheme.typography.titleMedium)
                Text(answer, style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = {
                    val clipboard = android.content.Context.CLIPBOARD_SERVICE
                    // Clipboard is accessed through the current application context in the host screen.
                }, enabled = false) { Text("Copy response (coming soon)") }
            }
        }
    }
}
