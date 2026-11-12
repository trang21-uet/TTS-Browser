package com.trangnx.ttsbrowser.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import java.io.File

/** Hands a downloaded APK to Android's package installer. The install itself is the system's. */
class ApkInstaller(private val context: Context) {

    enum class Result {
        /** The installer is on screen. */
        Started,

        /** "Install unknown apps" is off for this app; its settings page was opened instead. */
        NeedsPermission,
        Failed,
    }

    fun install(apk: File): Result {
        if (!context.packageManager.canRequestPackageInstalls()) {
            return if (launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri()))) {
                Result.NeedsPermission
            } else {
                Result.Failed
            }
        }
        return try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, APK_MIME_TYPE)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (launch(intent)) Result.Started else Result.Failed
        } catch (_: IllegalArgumentException) {
            Result.Failed
        }
    }

    // Started from the application context, so the task flag is required.
    private fun launch(intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }

    private companion object {
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    }
}
