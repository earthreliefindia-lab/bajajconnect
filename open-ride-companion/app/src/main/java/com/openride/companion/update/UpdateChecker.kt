package com.openride.companion.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.openride.companion.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class AppUpdateInfo(
    val tagName: String,
    val versionName: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val fileName: String,
    val fileSize: Long
)

object UpdateChecker {

    private const val TAG = "UpdateChecker"
    // Official GitHub repository for releases
    private const val GITHUB_REPO_API = "https://api.github.com/repos/earthreliefindia-lab/bajajconnect/releases/latest"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Checks the latest GitHub release and determines if a newer version is available.
     */
    suspend fun checkForUpdates(currentVersion: String = BuildConfig.APP_VERSION_NAME): AppUpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(GITHUB_REPO_API)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "OpenRideCompanion-Android")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "Update check failed with HTTP ${response.code}")
                response.close()
                return@withContext null
            }

            val responseBody = response.body?.string() ?: ""
            response.close()

            val json = JSONObject(responseBody)
            val tagName = json.optString("tag_name", "").removePrefix("v")
            val releaseTitle = json.optString("name", "New Release")
            val releaseNotes = json.optString("body", "Bug fixes and performance improvements.")

            // Look for APK asset in the release
            val assets = json.optJSONArray("assets") ?: return@withContext null
            var apkDownloadUrl: String? = null
            var apkName: String? = null
            var apkSize: Long = 0L

            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name", "")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    apkDownloadUrl = asset.optString("browser_download_url")
                    apkName = name
                    apkSize = asset.optLong("size", 0L)
                    break
                }
            }

            if (apkDownloadUrl != null && apkName != null && isNewerVersion(tagName, currentVersion)) {
                return@withContext AppUpdateInfo(
                    tagName = tagName,
                    versionName = tagName,
                    releaseTitle = releaseTitle,
                    releaseNotes = releaseNotes,
                    downloadUrl = apkDownloadUrl,
                    fileName = apkName,
                    fileSize = apkSize
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for updates", e)
        }
        null
    }

    /**
     * Downloads the APK file with progress reporting.
     */
    suspend fun downloadApk(
        context: Context,
        updateInfo: AppUpdateInfo,
        onProgress: (Float) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(updateInfo.downloadUrl)
                .header("User-Agent", "OpenRideCompanion-Android")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                response.close()
                return@withContext null
            }

            val body = response.body ?: return@withContext null
            val totalBytes = body.contentLength()

            val destDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: context.cacheDir
            val destFile = File(destDir, updateInfo.fileName)

            body.byteStream().use { input ->
                FileOutputStream(destFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesCopied = 0L
                    var read: Int

                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesCopied += read
                        if (totalBytes > 0) {
                            onProgress(bytesCopied.toFloat() / totalBytes)
                        }
                    }
                    output.flush()
                }
            }
            response.close()
            destFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download APK", e)
            null
        }
    }

    /**
     * Launches Android's official Package Installer to perform an in-place update.
     */
    fun installApk(context: Context, apkFile: File) {
        if (!apkFile.exists()) return

        // Android 8.0+ Unknown App Sources Permission Check
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(settingsIntent)
                return
            }
        }

        // Generate content URI via FileProvider
        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(installIntent)
    }

    /**
     * Compares two semantic version strings (e.g. "1.0.1" vs "1.0.0").
     */
    private fun isNewerVersion(remote: String, current: String): Boolean {
        val remoteParts = remote.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }
}
