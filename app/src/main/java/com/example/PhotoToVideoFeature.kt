package com.example

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
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
import java.io.File
import java.util.concurrent.TimeUnit

@Composable
fun PhotoToVideoFeature(sessionToken: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var photoName by remember { mutableStateOf("No photo selected") }
    var duration by remember { mutableFloatStateOf(10f) }
    var style by remember { mutableStateOf("both") }
    var working by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Free photo-to-video: camera motion only. AI face/talking animation is not included.") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            photoUri = uri
            photoName = "Photo selected"
        }
    }
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Photo to Video • FREE", style = MaterialTheme.typography.titleMedium)
            Text("Ek photo se vertical MP4 banayein. Natural camera movement, cinematic zoom aur dono ka mix.", style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(enabled = !working, onClick = { picker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                Text("Choose Photo")
            }
            Text(photoName, style = MaterialTheme.typography.bodySmall)
            Text("Video duration: ${duration.toInt()} seconds")
            Slider(value = duration, onValueChange = { duration = it }, valueRange = 10f..60f, steps = 49, enabled = !working)
            Text("Motion style")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("natural" to "Natural", "cinematic" to "Cinematic", "both" to "Both").forEach { item ->
                    FilterChip(selected = style == item.first, onClick = { style = item.first }, enabled = !working, label = { Text(item.second) })
                }
            }
            Button(enabled = !working && sessionToken.isNotBlank() && photoUri != null, modifier = Modifier.fillMaxWidth(), onClick = {
                scope.launch {
                    working = true
                    status = "Creating your free video… please wait."
                    try {
                        val uri = photoUri ?: error("Choose a photo first.")
                        val bytes = withContext(Dispatchers.IO) {
                            context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Could not read photo.")
                        }
                        if (bytes.size > 15 * 1024 * 1024) error("Photo must be under 15 MB.")
                        val client = OkHttpClient.Builder().connectTimeout(120, TimeUnit.SECONDS).readTimeout(10, TimeUnit.MINUTES).writeTimeout(10, TimeUnit.MINUTES).build()
                        val multipart = MultipartBody.Builder().setType(MultipartBody.FORM)
                            .addFormDataPart("durationSeconds", duration.toInt().toString())
                            .addFormDataPart("style", style)
                            .addFormDataPart("photo", "photo.jpg", bytes.toRequestBody("image/jpeg".toMediaType()))
                            .build()
                        val request = Request.Builder().url("https://mahadev-ai-shorts-maker-api.onrender.com/api/photo/video")
                            .header("Authorization", "Bearer $sessionToken").post(multipart).build()
                        val result = withContext(Dispatchers.IO) {
                            client.newCall(request).execute().use { response ->
                                if (!response.isSuccessful) {
                                    val detail = response.body?.string().orEmpty()
                                    val msg = runCatching { org.json.JSONObject(detail).optString("message") }.getOrNull()
                                    error(msg?.takeIf { it.isNotBlank() } ?: "Video generation failed (${response.code}).")
                                }
                                response.body?.bytes() ?: error("Empty video response.")
                            }
                        }
                        val filename = "Mahadev_Photo_Video_${System.currentTimeMillis()}.mp4"
                        withContext(Dispatchers.IO) {
                            if (Build.VERSION.SDK_INT >= 29) {
                                val values = ContentValues().apply {
                                    put(MediaStore.Video.Media.DISPLAY_NAME, filename)
                                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                                    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/MahadevShorts")
                                    put(MediaStore.Video.Media.IS_PENDING, 1)
                                }
                                val outUri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: error("Could not create output file.")
                                context.contentResolver.openOutputStream(outUri)?.use { it.write(result) } ?: error("Could not save video.")
                                values.clear()
                                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                                context.contentResolver.update(outUri, values, null, null)
                            } else {
                                val folder = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "MahadevShorts")
                                folder.mkdirs()
                                File(folder, filename).writeBytes(result)
                            }
                        }
                        status = "Done! ${duration.toInt()} sec MP4 saved in Movies/MahadevShorts (${result.size} bytes). Camera motion only; no AI face animation."
                    } catch (e: Exception) {
                        status = "Error: ${e.message ?: "Could not create video"}"
                    } finally {
                        working = false
                    }
                }
            }) { Text(if (working) "Creating…" else "Generate Free Photo Video") }
            if (working) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}
