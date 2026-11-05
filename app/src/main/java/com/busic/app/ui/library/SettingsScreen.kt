package com.busic.app.ui.library

import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.Process
import android.os.storage.StorageManager
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.imageLoader
import com.busic.app.ui.ConfirmDialog
import com.busic.app.ui.PageTitle
import com.busic.app.ui.quietClick
import com.busic.app.ui.theme.BgBase
import com.busic.app.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(onAbout: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bytes by remember { mutableLongStateOf(-1L) }
    var tick by remember { mutableIntStateOf(0) }
    var confirmClear by remember { mutableStateOf(false) }
    LaunchedEffect(tick) {
        bytes = withContext(Dispatchers.IO) { cacheBytes(context) }
    }
    val sizeText = if (bytes < 0L) "" else Formatter.formatFileSize(context, bytes)
    Column(
        Modifier
            .fillMaxSize()
            .background(BgBase),
    ) {
        PageTitle("设置")
        SettingRow("清除缓存", sizeText) { confirmClear = true }
        SettingRow("关于应用", onClick = onAbout)
    }
    if (confirmClear) {
        ConfirmDialog(
            title = "清除缓存",
            body = "确定清除缓存？登录状态会保留。",
            confirm = "清除",
            onConfirm = {
                confirmClear = false
                scope.launch {
                    clearCache(context)
                    tick++
                    Toast.makeText(context, "缓存已清除", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { confirmClear = false },
        )
    }
}

@Composable
fun AboutScreen() {
    val context = LocalContext.current
    val version = rememberVersion(context)
    Column(
        Modifier
            .fillMaxSize()
            .background(BgBase),
    ) {
        PageTitle("关于")
        Text(
            text = "哔哩哔哩音乐",
            color = Color.White,
            fontSize = 18.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        Text(
            text = "版本 $version",
            color = TextSecondary,
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Text(
            text = "用哔哩哔哩的视频音频来听歌",
            color = TextSecondary,
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun SettingRow(title: String, value: String = "", onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .quietClick(onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 16.sp,
            modifier = Modifier.weight(1f),
        )
        if (value.isNotEmpty()) {
            Text(text = value, color = TextSecondary, fontSize = 14.sp)
        }
    }
}

@Composable
private fun rememberVersion(context: Context): String {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    return info.versionName ?: "0.1.0"
}

private fun cacheBytes(context: Context): Long {
    return try {
        val manager = context.getSystemService(StorageStatsManager::class.java)
        val uuid = context.applicationInfo.storageUuid ?: StorageManager.UUID_DEFAULT
        manager.queryStatsForUid(uuid, Process.myUid()).cacheBytes
    } catch (_: Exception) {
        folderBytes(context.cacheDir) +
            folderBytes(context.codeCacheDir) +
            folderBytes(context.externalCacheDir)
    }
}

private fun folderBytes(dir: java.io.File?): Long {
    if (dir == null || !dir.exists()) return 0L
    return dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
}

private suspend fun clearCache(context: Context) = withContext(Dispatchers.IO) {
    val loader = context.imageLoader
    loader.memoryCache?.clear()
    loader.diskCache?.clear()
    context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
    context.externalCacheDir?.listFiles()?.forEach { it.deleteRecursively() }
}
