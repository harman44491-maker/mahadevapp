package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.content.ContentValues
import android.provider.MediaStore
import java.io.File
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.json.JSONArray
import java.util.concurrent.TimeUnit

private const val API_BASE = "https://mahadev-ai-shorts-maker-api.onrender.com"
private val http = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(10, TimeUnit.MINUTES)
    .writeTimeout(10, TimeUnit.MINUTES)
    .build()

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { ShortsMakerScreen() } }
    }
}

@Composable
private fun ShortsMakerScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var apiKey by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var privacy by remember { mutableStateOf("private") }
    var publishAt by remember { mutableStateOf("") }
    var selectedVideo by remember { mutableStateOf<Uri?>(null) }
    var selectedName by remember { mutableStateOf("No video selected") }
    var message by remember { mutableStateOf("Ready. Enter your app access key to use channel and upload tools.") }
    var busy by remember { mutableStateOf(false) }
    var trending by remember { mutableStateOf(listOf<Triple<String, String, String>>()) }
    var channel by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            selectedVideo = uri
            selectedName = context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val index = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && c.moveToFirst()) c.getString(index) else null
            } ?: "Selected video"
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            message = "Video selected: $selectedName"
        }
    }

    fun runTask(task: suspend () -> String) {
        if (busy) return
        scope.launch {
            busy = true
            message = "Please wait…"
            message = try { task() } catch (e: Exception) { "Error: ${e.message ?: "Request failed"}" }
            busy = false
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("MAHADEV", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text("AI Shorts Maker", style = MaterialTheme.typography.headlineMedium)
        Text("YouTube tools • Hindi workflow", style = MaterialTheme.typography.bodyMedium)

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("1. YouTube Account", style = MaterialTheme.typography.titleMedium)
                Text("Connect your own channel. Google sign-in opens in your browser.")
                Button(onClick = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$API_BASE/auth/youtube")))
                    }.onFailure { message = "Could not open Google sign-in: ${it.message}" }
                }, modifier = Modifier.fillMaxWidth()) { Text("Connect YouTube with Google") }
                OutlinedTextField(
                    value = apiKey, onValueChange = { apiKey = it },
                    label = { Text("App access key") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    enabled = apiKey.isNotBlank() && !busy,
                    onClick = { runTask {
                        val req = Request.Builder().url("$API_BASE/api/youtube/channel")
                            .header("x-app-api-key", apiKey).get().build()
                        val body = withContext(Dispatchers.IO) { http.newCall(req).execute().use { r ->
                            val text = r.body?.string().orEmpty()
                            if (!r.isSuccessful) error(JSONObject(text).optString("message", "Channel request failed (${r.code})"))
                            text
                        } }
                        val j = JSONObject(body)
                        channel = j.optString("title")
                        "Connected channel: $channel"
                    } }, modifier = Modifier.fillMaxWidth()
                ) { Text("Check My Channel") }
                if (channel.isNotBlank()) Text("Channel: $channel", color = MaterialTheme.colorScheme.primary)
            }
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("2. India YouTube Trending", style = MaterialTheme.typography.titleMedium)
                Button(enabled = !busy, onClick = { runTask {
                    val req = Request.Builder().url("$API_BASE/api/youtube/trending?regionCode=IN&maxResults=15").get().build()
                    val body = withContext(Dispatchers.IO) { http.newCall(req).execute().use { r ->
                        val text = r.body?.string().orEmpty()
                        if (!r.isSuccessful) error("Trending request failed (${r.code})")
                        text
                    } }
                    val arr = JSONObject(body).optJSONArray("videos") ?: JSONArray()
                    val result = (0 until arr.length()).map { i ->
                        val v = arr.getJSONObject(i)
                        Triple(v.optString("title"), v.optString("channelTitle"), v.optString("url"))
                    }
                    trending = result
                    "Loaded ${result.size} trending video records. Use only content you own or have permission to use."
                } }, modifier = Modifier.fillMaxWidth()) { Text("Load Trending Videos") }
                trending.forEachIndexed { index, item ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text("${index + 1}. ${item.first}", style = MaterialTheme.typography.bodyMedium)
                        Text(item.second, style = MaterialTheme.typography.labelSmall)
                        Text(item.third, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        HorizontalDivider()
                    }
                }
            }
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("3. Create Hindi Captioned Short", style = MaterialTheme.typography.titleMedium)
                Text("Selected video ko 9:16 vertical format mein crop karein, Hindi speech captions burn karein, aur MP4 ko phone mein save karein.")
                OutlinedButton(onClick = { picker.launch(arrayOf("video/*")) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Choose Source Video")
                }
                Text(selectedName, style = MaterialTheme.typography.bodySmall)
                Button(
                    enabled = !busy && apiKey.isNotBlank() && selectedVideo != null,
                    onClick = { runTask {
                        val uri = selectedVideo ?: error("Choose a source video first")
                        val bytes = withContext(Dispatchers.IO) {
                            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                                ?: error("Could not read selected video")
                        }
                        if (bytes.size > 120 * 1024 * 1024) error("Source video must be under 120 MB for this free-hosting build.")
                        val multipart = MultipartBody.Builder().setType(MultipartBody.FORM)
                            .addFormDataPart("startSeconds", "0")
                            .addFormDataPart("durationSeconds", "30")
                            .addFormDataPart("video", selectedName, bytes.toRequestBody("video/mp4".toMediaType()))
                            .build()
                        val req = Request.Builder().url("$API_BASE/api/video/process")
                            .header("x-app-api-key", apiKey).post(multipart).build()
                        val processed = withContext(Dispatchers.IO) { http.newCall(req).execute().use { r ->
                            if (!r.isSuccessful) {
                                val errorText = r.body?.string().orEmpty()
                                val detail = runCatching { JSONObject(errorText).optString("message") }.getOrNull()
                                error(detail?.takeIf { it.isNotBlank() } ?: "Short processing failed (" + r.code + ")")
                            }
                            r.body?.bytes() ?: error("Empty processed video")
                        } }
                        val filename = "Mahadev_Hindi_Short_" + System.currentTimeMillis() + ".mp4"
                        val savedUri = withContext(Dispatchers.IO) {
                            if (android.os.Build.VERSION.SDK_INT >= 29) {
                                val values = ContentValues().apply {
                                    put(MediaStore.Video.Media.DISPLAY_NAME, filename)
                                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                                    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/MahadevShorts")
                                    put(MediaStore.Video.Media.IS_PENDING, 1)
                                }
                                val outUri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: error("Could not create output file")
                                context.contentResolver.openOutputStream(outUri)?.use { it.write(processed) } ?: error("Could not save processed video")
                                values.clear()
                                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                                context.contentResolver.update(outUri, values, null, null)
                                outUri
                            } else {
                                val folder = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "MahadevShorts")
                                folder.mkdirs()
                                val outputFile = File(folder, filename)
                                outputFile.writeBytes(processed)
                                Uri.fromFile(outputFile)
                            }
                        }
                        selectedVideo = savedUri
                        selectedName = filename
                        "Hindi captioned 9:16 Short saved and selected for publishing (" + processed.size + " bytes)."
                    } }, modifier = Modifier.fillMaxWidth()
                ) { Text("Generate Hindi Short (30 sec)") }
            }
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("4. Upload Your Video", style = MaterialTheme.typography.titleMedium)
                Text("Upload only videos you own or are licensed to publish.")
                OutlinedButton(onClick = { picker.launch(arrayOf("video/*")) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Choose Video")
                }
                Text(selectedName, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Video title") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                Text("Privacy")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("private", "unlisted", "public").forEach { value ->
                        FilterChip(selected = privacy == value, onClick = { privacy = value }, label = { Text(value.replaceFirstChar { it.uppercase() }) })
                    }
                }
                OutlinedTextField(
                    value = publishAt, onValueChange = { publishAt = it },
                    label = { Text("Schedule (optional ISO date/time)") },
                    placeholder = { Text("2026-11-26T18:30:00+05:30") },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    enabled = !busy && apiKey.isNotBlank() && selectedVideo != null && title.isNotBlank(),
                    onClick = { runTask {
                        val uri = selectedVideo ?: error("Choose a video first")
                        val bytes = withContext(Dispatchers.IO) {
                            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                                ?: error("Could not read selected video")
                        }
                        if (bytes.size > 250 * 1024 * 1024) error("Video is larger than 250 MB. Choose a smaller file.")
                        val videoBody = bytes.toRequestBody("application/octet-stream".toMediaType())
                        val multipart = MultipartBody.Builder().setType(MultipartBody.FORM)
                            .addFormDataPart("title", title)
                            .addFormDataPart("description", description)
                             .addFormDataPart("privacyStatus", privacy)
                            .apply { if (publishAt.isNotBlank()) addFormDataPart("publishAt", publishAt.trim()) }
                            .addFormDataPart("video", selectedName, videoBody)
                            .build()
                        val req = Request.Builder().url("$API_BASE/api/youtube/upload")
                            .header("x-app-api-key", apiKey).post(multipart).build()
                        val body = withContext(Dispatchers.IO) { http.newCall(req).execute().use { r ->
                            val text = r.body?.string().orEmpty()
                            if (!r.isSuccessful) error(JSONObject(text).optString("message", "Upload failed (${r.code})"))
                            text
                        } }
                        val j = JSONObject(body)
                        "Upload successful: ${j.optString("url")}"
                    } }, modifier = Modifier.fillMaxWidth()
                ) { Text("Upload to YouTube") }
                Button(
                    enabled = !busy && apiKey.isNotBlank() && selectedVideo != null,
                    onClick = { runTask {
                        val uri = selectedVideo ?: error("Choose a video first")
                        val bytes = withContext(Dispatchers.IO) {
                            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                                ?: error("Could not read selected video")
                        }
                        if (bytes.size > 120 * 1024 * 1024) error("Video must be under 120 MB.")
                        val multipart = MultipartBody.Builder().setType(MultipartBody.FORM)
                            .addFormDataPart("title", title.ifBlank { "Mahadev AI Short" })
                            .addFormDataPart("description", description)
                            .apply { if (publishAt.isNotBlank()) addFormDataPart("publishAt", publishAt.trim()) }
                            .addFormDataPart("video", selectedName, bytes.toRequestBody("video/mp4".toMediaType()))
                            .build()
                        val req = Request.Builder().url("$API_BASE/api/facebook/reels")
                            .header("x-app-api-key", apiKey).post(multipart).build()
                        val body = withContext(Dispatchers.IO) { http.newCall(req).execute().use { r ->
                            val text = r.body?.string().orEmpty()
                            if (!r.isSuccessful) {
                                val detail = runCatching { JSONObject(text).optString("message") }.getOrNull()
                                error(detail?.takeIf { it.isNotBlank() } ?: "Facebook publishing failed (" + r.code + ")")
                            }
                            text
                        } }
                        val j = JSONObject(body)
                        if (publishAt.isBlank()) "Facebook Reel publish request submitted. Video ID: " + j.optString("videoId")
                        else "Facebook Reel scheduled. Video ID: " + j.optString("videoId")
                    } }, modifier = Modifier.fillMaxWidth()
                ) { Text(if (publishAt.isBlank()) "Publish Facebook Reel" else "Schedule Facebook Reel") }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(16.dp)) {
                Text("Status", style = MaterialTheme.typography.titleSmall)
                Text(message)
            }
        }
        Text("Note: Trending shows public metadata only. It does not grant rights to download or repost videos. Hindi Shorts processing requires Google Speech-to-Text credentials on the server. Facebook publishing requires a Page access token and Page permissions. Scheduled publishing is available when a publish time is provided to the API.", style = MaterialTheme.typography.bodySmall)
    }
}
