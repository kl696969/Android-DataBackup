package com.xayah.databackup.util

import android.annotation.SuppressLint
import android.content.Context
import com.xayah.databackup.DataBackupApplication
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.nio.file.Paths
import kotlin.io.path.pathString

fun Context.filesPath(): String = filesDir.path

fun Context.binPath(): String = "${filesPath()}/bin"

fun Context.binArchivePath(): String = "${filesPath()}/bin.zip"

fun Context.extensionPath(): String = "${filesPath()}/extension"

fun Context.extensionArchivePath(): String = "${filesPath()}/extension.zip"

fun Context.iconPath(): String = "${filesPath()}/icon"

@SuppressLint("SdCardPath")
object PathUtil {
    fun getParentPath(path: String): String = Paths.get(path).parent.pathString

    fun getFileName(path: String): String = Paths.get(path).fileName.pathString

    // Paths for internal usage.
    fun getIconPath(context: Context, packageName: String): String = "${context.iconPath()}/${packageName}.png"

    // Exclude dirs while running `tree` command.
    fun getExcludeDirs(): List<String> = listOf("tree", "icon", "databases", "log")

    fun getTmpApkPath(context: Context, packageName: String): String = "${context.filesPath()}/tmp/apks/$packageName"
    fun getTmpConfigPath(context: Context, name: String, timestamp: Long): String = "${context.filesPath()}/tmp/config/$name/$timestamp"
    fun getTmpConfigFilePath(context: Context, name: String, timestamp: Long): String = "${getTmpConfigPath(context, name, timestamp)}/PackageRestoreEntire"
    fun getTmpMountPath(context: Context, name: String): String = "${context.filesPath()}/tmp/mount/$name"

    // Paths for backup save dir.
    fun getBackupSavePath(): String = runBlocking { DataBackupApplication.application.readBackupSavePath().first() }
    private fun getBackupArchivesSavePath(): String = "${getBackupSavePath()}/archives"
    fun getBackupPackagesSavePath(): String = "${getBackupArchivesSavePath()}/packages"
    fun getBackupMediumSavePath(): String = "${getBackupArchivesSavePath()}/medium"
    private fun getTreeSavePath(): String = "${getBackupSavePath()}/tree"
    fun getTreeSavePath(timestamp: Long): String = "${getTreeSavePath()}/tree_${timestamp}"
    fun getIconSavePath(): String = "${getBackupSavePath()}/icon"
    fun getIconNoMediaSavePath(): String = "${getIconSavePath()}/.nomedia"
    private fun getLogSavePath(): String = "${getBackupSavePath()}/log"
    fun getLogSavePath(timestamp: Long): String = "${getLogSavePath()}/log_${timestamp}"

    // Paths for restore save dir.
    private fun getRestoreSavePath(): String = runBlocking { DataBackupApplication.application.readRestoreSavePath().first() }
    fun getRestoreArchivesSavePath(): String = "${getRestoreSavePath()}/archives"
    fun getRestorePackagesSavePath(): String = "${getRestoreArchivesSavePath()}/packages"
    fun getRestoreMediumSavePath(): String = "${getRestoreArchivesSavePath()}/medium"
    fun getRestoreIconSavePath(): String = "${getRestoreSavePath()}/icon"

    // Paths for processing.
    fun getPackageUserPath(userId: Int): String = "/data/user/${userId}"
    fun getPackageUserDePath(userId: Int): String = "/data/user_de/${userId}"
    fun getPackageDataPath(userId: Int): String = "/data/media/${userId}/Android/data"
    fun getPackageObbPath(userId: Int): String = "/data/media/${userId}/Android/obb"
    fun getPackageMediaPath(userId: Int): String = "/data/media/${userId}/Android/media"
}
