package com.example

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private object LocalAiRuntime {
    private const val MODEL_URL = "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/e6687177e00897731e7716267eebd4eba0dde7c2/Qwen3-0.6B.litertlm"
    private const val MODEL_SHA256 = "c5f39b4191926786c3e6668b62af4d6eb15248dfc7a5348252a9b406f33f2108"
    private const val MODEL_NAME = "Qwen3-0.6B.litertlm"
    private val executor = Executors.newSingleThreadExecutor()
    private val dispatcher = executor.asCoroutineDispatcher()
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.MINUTES)
        .callTimeout(30, TimeUnit.MINUTES)
        .build()
    private var engine: Engine? = null
    private var conversation: Conversation? = null

    suspend fun prepare(context: Context, progress: (Int) -> Unit) = withContext(dispatcher) {
        val model = File(context.filesDir, MODEL_NAME)
        if (!model.exists() || sha256(model) != MODEL_SHA256) {
            model.delete()
            val temp = File(context.filesDir, "$MODEL_NAME.part")
            temp.delete()
            val request = Request.Builder().url(MODEL_URL).get().build()
            client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "Model download failed (${response.code}). Please retry." }
                val body = response.body ?: error("Model download was empty.")
                val total = body.contentLength().coerceAtLeast(1L)
                body.byteStream().use { input ->
                    temp.outputStream().buffered().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var count: Int
                        var done = 0L
                        while (input.read(buffer).also { count = it } != -1) {
                            output.write(buffer, 0, count)
                            done += count
                            progress(((done * 100) / total).toInt().coerceIn(0, 99))
                        }
                    }
                }
            }
            check(sha256(temp) == MODEL_SHA256) { "Model verification failed. Please retry the download." }
            check(temp.renameTo(model)) { "Could not save the AI model on this device." }
        }
        if (engine == null) {
            val newEngine = Engine(EngineConfig(
                modelPath = model.absolutePath,
                backend = Backend.CPU(),
                cacheDir = context.cacheDir.absolutePath
            ))
            try {
                newEngine.initialize()
                engine = newEngine
                conversation = newEngine.createConversation(
                    ConversationConfig(systemInstruction = Contents.of(
                        "You are Mahadev AI Helper. Help with safe everyday tasks such as writing, prompts, ideas, summaries, translation, study explanations and simple code. Reply clearly in the user's language. Be honest about limitations."
                    ))
                )
            } catch (e: Exception) {
                runCatching { newEngine.close() }
                throw e
            }
        }
        progress(100)
    }

    suspend fun ask(prompt: String, onChunk: (String) -> Unit) = withContext(dispatcher) {
        val chat = conversation ?: error("Download and load the free AI model first.")
        chat.sendMessageAsync(prompt).collect { chunk -> onChunk(chunk.toString()) }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(128 * 1024)
            var n: Int
            while (input.read(buffer).also { n = it } != -1) digest.update(buffer, 0, n)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

@Composable
fun AiHelperFeature(sessionToken: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var prompt by remember { mutableStateOf("") }
    var taskType by remember { mutableStateOf("General") }
    var answer by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Free offline AI. First setup downloads a 614 MB model; after that it works offline.") }
    var busy by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableIntStateOf(0) }

    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("AI Helper • Free Offline AI", style = MaterialTheme.typography.titleLarge)
            Text("AI phone par chalega. Koi API key, subscription ya daily request limit nahi. Model ek baar download hoga (614 MB).")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("General", "Prompt", "Video", "Study", "Writing", "Code").forEach { type ->
                    FilterChip(selected = taskType == type, onClick = { taskType = type }, enabled = !busy, label = { Text(type) })
                }
            }
            if (!ready) {
                Button(
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        scope.launch {
                            busy = true
                            status = "Free AI model download/load ho raha hai…"
                            try {
                                LocalAiRuntime.prepare(context) { value ->
                                    scope.launch { downloadProgress = value }
                                }
                                ready = true
                                status = "AI ready hai. Ab offline bhi use kar sakte hain."
                            } catch (e: Exception) {
                                status = e.message ?: "AI model load nahi ho paya. Dobara try karein."
                            } finally { busy = false }
                        }
                    }
                ) { Text(if (busy) "Preparing free AI…" else "Download & Activate Free AI (614 MB)") }
                if (busy) LinearProgressIndicator(progress = { downloadProgress / 100f }, modifier = Modifier.fillMaxWidth())
            }
            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6,
                enabled = ready && !busy,
                label = { Text("Aapko kya karwana hai?") },
                placeholder = { Text("Example: Mere liye ek realistic 9:16 funny video prompt banao…") }
            )
            Button(
                enabled = ready && !busy && prompt.trim().length >= 3,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    scope.launch {
                        busy = true
                        answer = ""
                        status = "Phone par AI jawab taiyar kar raha hai…"
                        try {
                            val fullPrompt = "Task type: $taskType\nUser request: ${prompt.trim()}"
                            LocalAiRuntime.ask(fullPrompt) { chunk -> answer += chunk }
                            status = "Jawab taiyar hai • On-device AI • No daily limit"
                        } catch (e: Exception) {
                            status = e.message ?: "AI response nahi mil saka."
                        } finally { busy = false }
                    }
                }
            ) { Text(if (busy) "Please wait…" else "Ask AI Helper") }
            if (busy && ready) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            if (answer.isNotBlank()) {
                HorizontalDivider()
                Text("AI Response", style = MaterialTheme.typography.titleMedium)
                Text(answer, style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("AI Response", answer))
                }) { Text("Copy Response") }
            }
        }
    }
}
