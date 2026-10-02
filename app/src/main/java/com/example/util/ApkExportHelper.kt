package com.example.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import com.example.BuildConfig
import java.io.File

object ApkExportHelper {

    private const val TAG = "ApkExportHelper"
    val APK_FILE_NAME: String
        get() = "BBC_POS_v${BuildConfig.VERSION_NAME}.apk"

    /**
     * Gets or creates a local accessible copy of the running app's APK in cache.
     * Uses applicationInfo.sourceDir (the active running APK binary).
     */
    fun getLocalApkFile(context: Context): File? {
        try {
            val sourceApk = File(context.applicationInfo.sourceDir)
            if (!sourceApk.exists()) {
                Log.e(TAG, "sourceDir APK does not exist: ${context.applicationInfo.sourceDir}")
                return null
            }

            val targetFile = File(context.cacheDir, APK_FILE_NAME)
            sourceApk.copyTo(targetFile, overwrite = true)
            Log.d(TAG, "Copied active sourceDir APK (${sourceApk.length()} bytes) to cache: ${targetFile.absolutePath}")
            return targetFile
        } catch (e: Exception) {
            Log.e(TAG, "Error getting local APK file", e)
            return null
        }
    }

    /**
     * Saves the current app APK into the device's public Downloads directory.
     * Returns Pair<Success, Message>.
     */
    fun saveApkToDownloads(context: Context): Pair<Boolean, String> {
        return try {
            val sourceApk = getLocalApkFile(context)
            if (sourceApk == null || !sourceApk.exists()) {
                return Pair(false, "App binary not found on device.")
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, APK_FILE_NAME)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.android.package-archive")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }

                // Remove existing if any with same name
                try {
                    resolver.delete(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        "${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
                        arrayOf(APK_FILE_NAME)
                    )
                } catch (e: Exception) {
                    // Ignore deletion error
                }

                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { outStream ->
                        sourceApk.inputStream().use { inStream ->
                            inStream.copyTo(outStream)
                        }
                    }
                    Pair(true, "Successfully saved $APK_FILE_NAME to your device Downloads folder!")
                } else {
                    Pair(true, "Saved to app cache ($APK_FILE_NAME).")
                }
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                val destFile = File(downloadsDir, APK_FILE_NAME)
                sourceApk.copyTo(destFile, overwrite = true)
                Pair(true, "Successfully saved to ${destFile.absolutePath}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving APK to downloads", e)
            Pair(false, "Could not save to Downloads: ${e.localizedMessage}")
        }
    }

    /**
     * Triggers the Android package installer or file viewer to install/open the APK.
     */
    fun installOrOpenApk(context: Context): Boolean {
        return try {
            val apkFile = getLocalApkFile(context) ?: return false
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error launching APK install", e)
            false
        }
    }

    /**
     * Shares the actual .apk file binary via Intent.ACTION_SEND with FileProvider.
     */
    fun shareApkFile(context: Context): Boolean {
        return try {
            val apkFile = getLocalApkFile(context) ?: return false
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_STREAM, apkUri)
                putExtra(Intent.EXTRA_SUBJECT, "BBC POS v${BuildConfig.VERSION_NAME} APK")
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Here is the complete BBC POS v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE}) APK installer file for your Android tablet / phone."
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share BBC POS v${BuildConfig.VERSION_NAME} APK"))
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error sharing APK", e)
            false
        }
    }

    /**
     * Opens the direct web server download link in the browser.
     */
    fun openBrowserDownloadLink(context: Context): Boolean {
        return try {
            val downloadUrl = "https://ais-dev-ck4b7t6mx62asbysbv5aas-917087314118.asia-southeast1.run.app/BBC_POS_v${BuildConfig.VERSION_NAME}.apk"
            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(downloadUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error opening browser download link", e)
            false
        }
    }
}
