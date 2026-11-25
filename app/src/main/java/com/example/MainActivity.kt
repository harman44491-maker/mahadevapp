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
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val SESSION_PREFS = "mahadev_secure_session"
private const val SESSION_KEY_ALIAS = "mahadev_session_aes_key"

private fun sessionKey(): SecretKey {
    val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    val existing = store.getKey(SESSION_KEY_ALIAS, null) as? SecretKey
    if (existing != null) return existing
    val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
    generator.init(KeyGenParameterSpec.Builder(
        SESSION_KEY_ALIAS,
        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
    ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setRandomizedEncryptionRequired(true)
        .build())
    return generator.generateKey()
}

private fun saveSession(context: android.content.Context, token: String) {
    if (token.isBlank()) return
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, sessionKey())
    val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
    val packed = cipher.iv + encrypted
    val encoded = Base64.encodeToString(packed, Base64.NO_WRAP)
    context.getSharedPreferences(SESSION_PREFS, android.content.Context.MODE_PRIVATE)
        .edit().putString("token", encoded).apply()
}

private fun loadSession(context: android.content.Context): String {
    return try {
        val encoded = context.getSharedPreferences(SESSION_PREFS, android.content.Context.MODE_PRIVATE)
            .getString("token", null) ?: return ""
        val packed = Base64.decode(encoded, Base64.NO_WRAP)
        if (packed.size <= 12) return ""
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, sessionKey(), GCMParameterSpec(128, packed.copyOfRange(0, 12)))
        String(cipher.doFinal(packed.copyOfRange(12, packed.size)), Charsets.UTF_8)
    } catch (_: Exception) { "" }
}

private fun clearSession(context: android.content.Context) {
    context.getSharedPreferences(SESSION_PREFS, android.content.Context.MODE_PRIVATE)
        .edit().remove("token").apply()
}

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
    var sessionToken by remember { mutableStateOf(loadSession(context)) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var privacy by remember { mutableStateOf("private") }
    var publishAt by remember { mutableStateOf("") }
    var selectedVideo by remember { mutableStateOf<Uri?>(null) }
    var selectedName by remember { mutableStateOf("No video selected") }
    var message by remember { mutableStateOf("Log in or create an account to get started.") }
    var busy by remember { mutableStateOf(false) }
    var trending by remember { mutableStateOf(listOf<Triple<String, String, String>>()) }
    var channel by remember { mutableStateOf("") }
    var facebookPages by remember { mutableStateOf(listOf<Pair<String, String>>()) }

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

        if (sessionToken.isBlank()) {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Account Login / Signup", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email address") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password (minimum 8 characters)") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = !busy, onClick = { runTask {
                            val payload = JSONObject().put("email", email.trim()).put("password", password).toString()
                            val req = Request.Builder().url("$API_BASE/api/auth/login").post(payload.toRequestBody("application/json".toMediaType())).build()
                            val body = withContext(Dispatchers.IO) { http.newCall(req).execute().use { r ->
                                val text = r.body?.string().orEmpty()
                                if (!r.isSuccessful) error(JSONObject(text).optString("message", "Login failed (${r.code})"))
                                text
                            } }
                            sessionToken = JSONObject(body).optString("token")
                            saveSession(context, sessionToken)
                            "Login successful. Your account is ready."
                        } }, modifier = Modifier.weight(1f)) { Text("Log In") }
                        OutlinedButton(enabled = !busy, onClick = { runTask {
                            val payload = JSONObject().put("email", email.trim()).put("password", password).toString()
                            val req = Request.Builder().url("$API_BASE/api/auth/signup").post(payload.toRequestBody("application/json".toMediaType())).build()
                            val body = withContext(Dispatchers.IO) { http.newCall(req).execute().use { r ->
                                val text = r.body?.string().orEmpty()
                                if (!r.isSuccessful) error(JSONObject(text).optString("message", "Signup failed (${r.code})"))
                                text
                            } }
                            sessionToken = JSONObject(body).optString("token")
                            saveSession(context, sessionToken)
                            "Account created successfully."
                        } }, modifier = Modifier.weight(1f)) { Text("Create Account") }
                    }
                }
            }
        } else {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("1. Your Connected Accounts", style = MaterialTheme.typography.titleMedium)
                    Text("Your YouTube and Facebook connections are saved separately to your account.")
                    Button(enabled = !busy, onClick = { runTask {
                        val req = Request.Builder().url("$API_BASE/api/oauth/youtube/url").header("Authorization", "Bearer $sessionToken").get().build()
                        val body = withContext(Dispatchers.IO) { http.newCall(req).execute().use { r ->
                            val text = r.body?.string().orEmpty()
                            if (!r.isSuccessful) error(JSONObject(text).optString("message", "Google sign-in could not start"))
                            text
                        } }
                        val url = JSONObject(body).optString("url")
                        withContext(Dispatchers.Main) { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                        "Google sign-in opened. Complete it in your browser, then return to the app."
                    } }, modifier = Modifier.fillMaxWidth()) { Text("Connect YouTube with Google") }
                    Button(enabled = !busy, onClick = { runTask {
                        val req = Request.Builder().url("$API_BASE/api/oauth/facebook/url").header("Authorization", "Bearer $sessionToken").get().build()
                        val body = withContext(Dispatchers.IO) { http.newCall(req).execute().use { r ->
                            val text = r.body?.string().orEmpty()
                            if (!r.isSuccessful) error(JSONObject(text).optString("message", "Facebook sign-in could not start"))
                            text
                        } }
                        val url = JSONObject(body).optString("url")
                        withContext(Dispatchers.Main) { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                        "Facebook sign-in opened. Complete it in your browser, then return to the app."
                    } }, modifier = Modifier.fillMaxWidth()) { Text("Connect Facebook Page") }
                    Button(enabled = !busy, onClick = { runTask {
                        val req = Request.Builder().url("$API_BASE/api/facebook/pages").header("Authorization", "Bearer $sessionToken").get().build()
                        val body = withContext(Dispatchers.IO) { http.newCall(req).execute().use { r ->
                            val text = r.body?.string().orEmpty()
                            if (!r.isSuccessful) error(JSONObject(text).optString("message", "Could not load Facebook Pages"))
                            text
                        } }
                        val arr = JSONObject(body).optJSONArray("pages") ?: JSONArray()
                        facebookPages = (0 until arr.length()).map { i ->
                            val page = arr.getJSONObject(i)
                            page.optString("id") to page.optString("name")
                        }
                        if (facebookPages.isEmpty()) "No Facebook Pages found for this account."
                        else "Found ${facebookPages.size} Facebook Page(s). Select one below."
                    } }, modifier = Modifier.fillMaxWidth()) { Text("Load My Facebook Pages") }
                    facebookPages.forEach { page ->
                        OutlinedButton(enabled = !busy, onClick = { runTask {
                            val payload = JSONObject().put("pageId", page.first).toString()
                            val req = Request.Builder().url("$API_BASE/api/facebook/select-page")
                                .header("Authorization", "Bearer $sessionToken")
                                .post(payload.toRequestBody("application/json".toMediaType())).build()
                            withContext(Dispatchers.IO) { http.newCall(req).execute().use { r ->
                                val text = r.body?.string().orEmpty()
                                if (!r.isSuccessful) error(JSONObject(text).optString("message", "Page selection failed"))
                            } }
                            "Facebook Page selected: ${page.second}"
                        } }, modifier = Modifier.fillMaxWidth()) { Text("Select Page: ${page.second}") }
                    }
                    Button(enabled = !busy, onClick = { runTask {
                        val req = Request.Builder().url("$API_BASE/api/youtube/channel").header("Authorization", "Bearer $sessionToken").get().build()
                        val body = withContext(Dispatchers.IO) { http.newCall(req).execute().use { r ->
                            val text = r.body?.string().orEmpty()
                            if (!r.isSuccessful) error(JSONObject(text).optString("message", "Channel request failed"))
                            text
                        } }
                        channel = JSONObject(body).optString("title")
                        "Connected channel: $channel"
                    } }, modifier = Modifier.fillMaxWidth()) { Text("Check My Channel") }
                    if (channel.isNotBlank()) Text("Channel: $channel", color = MaterialTheme.colorScheme.primary)
                    OutlinedButton(enabled = !busy, onClick = { runTask {
                        val req = Request.Builder().url("$API_BASE/api/auth/logout").header("Authorization", "Bearer $sessionToken").post("{}".toRequestBody("application/json".toMediaType())).build()
                        withContext(Dispatchers.IO) { http.newCall(req).execute().use { } }
                        clearSession(context)
                        sessionToken = ""
                        channel = ""
                        "Logged out successfully."
                    } }, modifier = Modifier.fillMaxWidth()) { Text("Log Out") }
                }
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
                    enabled = !busy && sessionToken.isNotBlank() && selectedVideo != null,
                    onClick = { runTask {
                        val uri = selectedVideo ?: error("Choose a source video first")
                        val bytes = withContext(Dispatchers.IO) {
                            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                                ?: error("Could not read selected video")
                        }
                        if (bytes.size > 80 * 1024 * 1024) error("Source video must be under 80 MB for this free-hosting build.")
                        val multipart = MultipartBody.Builder().setType(MultipartBody.FORM)
                            .addFormDataPart("startSeconds", "0")
                            .addFormDataPart("durationSeconds", "30")
                            .addFormDataPart("video", selectedName, bytes.toRequestBody("video/mp4".toMediaType()))
                            .build()
                        val req = Request.Builder().url("$API_BASE/api/video/process")
                            .header("Authorization", "Bearer $sessionToken").post(multipart).build()
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
                if (publishAt.isNotBlank()) Text("YouTube scheduled uploads are sent as Private until the scheduled publish time.")
                Button(
                    enabled = !busy && sessionToken.isNotBlank() && selectedVideo != null && title.isNotBlank(),
                    onClick = { runTask {
                        val uri = selectedVideo ?: error("Choose a video first")
                        val bytes = withContext(Dispatchers.IO) {
                            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                                ?: error("Could not read selected video")
                        }
                        if (bytes.size > 120 * 1024 * 1024) error("Video is larger than 120 MB. Choose a smaller file.")
                        val videoBody = bytes.toRequestBody("application/octet-stream".toMediaType())
                        val multipart = MultipartBody.Builder().setType(MultipartBody.FORM)
                            .addFormDataPart("title", title)
                            .addFormDataPart("description", description)
                             .addFormDataPart("privacyStatus", if (publishAt.isNotBlank()) "private" else privacy)
                            .apply { if (publishAt.isNotBlank()) addFormDataPart("publishAt", publishAt.trim()) }
                            .addFormDataPart("video", selectedName, videoBody)
                            .build()
                        val req = Request.Builder().url("$API_BASE/api/youtube/upload")
                            .header("Authorization", "Bearer $sessionToken").post(multipart).build()
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
                    enabled = !busy && sessionToken.isNotBlank() && selectedVideo != null,
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
                            .header("Authorization", "Bearer $sessionToken").post(multipart).build()
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
