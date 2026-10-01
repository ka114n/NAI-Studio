package com.kallan.naistudio.services

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

object AndroidApplicationUpdater {
    @Suppress("DEPRECATION")
    fun install(context: Context, file: File) {
        require(file.canonicalFile.parentFile == File(context.cacheDir, "updates").canonicalFile) { "Unexpected APK path" }
        val pm = context.packageManager
        val archive = pm.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNATURES) ?: error("Invalid APK")
        val installed = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
        require(archive.packageName == context.packageName) { "Update package identity mismatch" }
        val candidateSignatures = archive.signatures?.map { it.toCharsString() }?.toSet().orEmpty()
        val installedSignatures = installed.signatures?.map { it.toCharsString() }?.toSet().orEmpty()
        require(candidateSignatures.isNotEmpty() && candidateSignatures == installedSignatures) { "APK signing certificate differs; cannot update this installation" }
        require(archive.versionCode > installed.versionCode) { "APK is not newer than the installed version" }
        if (!pm.canRequestPackageInstalls()) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
