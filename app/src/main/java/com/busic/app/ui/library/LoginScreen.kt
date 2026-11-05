package com.busic.app.ui.library

import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.busic.app.BusicApp
import com.busic.app.data.QrState
import com.busic.app.ui.PageTitle
import com.busic.app.ui.quietClick
import com.busic.app.ui.theme.BgBase
import com.busic.app.ui.theme.Brand
import com.busic.app.ui.theme.TextSecondary
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private enum class LoginMode { Sms, Qr }

private const val LOGIN_PAGE = "https://passport.bilibili.com/login"
private const val MOBILE_UA =
    "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

@Composable
fun LoginScreen(onSuccess: () -> Unit) {
    var mode by remember { mutableStateOf(LoginMode.Sms) }
    Column(
        Modifier
            .fillMaxSize()
            .background(BgBase),
    ) {
        PageTitle("登录")
        Row(
            Modifier.padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            ModeTab("验证码", mode == LoginMode.Sms) { mode = LoginMode.Sms }
            ModeTab("扫码", mode == LoginMode.Qr) { mode = LoginMode.Qr }
        }
        when (mode) {
            LoginMode.Sms -> SmsLogin(onSuccess)
            LoginMode.Qr -> QrLogin(onSuccess)
        }
    }
}

@Composable
private fun ModeTab(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        color = if (selected) Color.White else TextSecondary,
        fontSize = 16.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
            .quietClick(onClick)
            .padding(vertical = 8.dp),
    )
}

@Composable
private fun QrLogin(onSuccess: () -> Unit) {
    val api = BusicApp.get().api
    var generation by remember { mutableIntStateOf(0) }
    var url by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("正在获取二维码") }
    var canRefresh by remember { mutableStateOf(false) }

    LaunchedEffect(generation) {
        canRefresh = false
        url = null
        status = "正在获取二维码"
        val ticket = runCatching { api.qrTicket() }.getOrElse {
            status = it.message ?: "二维码获取失败"
            canRefresh = true
            return@LaunchedEffect
        }
        url = ticket.url
        status = "请用哔哩哔哩 App 扫码"
        while (isActive) {
            delay(1500)
            when (runCatching { api.qrPoll(ticket.key) }.getOrNull()) {
                QrState.Success -> {
                    onSuccess()
                    return@LaunchedEffect
                }
                QrState.Scanned -> status = "已扫码，请在手机上确认"
                QrState.Expired -> {
                    status = "二维码已失效"
                    canRefresh = true
                    return@LaunchedEffect
                }
                else -> Unit
            }
        }
    }

    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .padding(top = 36.dp)
                .size(240.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            val link = url
            if (link != null) {
                Image(
                    bitmap = remember(link) { qrImage(link) },
                    contentDescription = "登录二维码",
                    modifier = Modifier.size(210.dp),
                )
            }
        }
        Text(
            text = status,
            color = TextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 24.dp, start = 32.dp, end = 32.dp),
        )
        if (canRefresh) {
            Text(
                text = "点击刷新",
                color = Brand,
                fontSize = 15.sp,
                modifier = Modifier
                    .padding(top = 16.dp)
                    .quietClick { generation++ }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun SmsLogin(onSuccess: () -> Unit) {
    val api = BusicApp.get().api
    var seen by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(600)
            val header = webCookies()
            val sess = header.substringAfter("SESSDATA=", "").substringBefore(";").trim()
            if (sess.isBlank() || sess == seen) continue
            seen = sess
            api.adoptWebCookies(header)
            if (runCatching { api.nav() }.getOrNull() != null) {
                onSuccess()
                return@LaunchedEffect
            }
        }
    }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.userAgentString = MOBILE_UA
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                webViewClient = WebViewClient()
                loadUrl(LOGIN_PAGE)
            }
        },
        onRelease = { it.destroy() },
    )
}

private fun webCookies(): String {
    val manager = CookieManager.getInstance()
    return listOf(
        "https://passport.bilibili.com",
        "https://www.bilibili.com",
        "https://api.bilibili.com",
    ).mapNotNull { manager.getCookie(it) }.joinToString("; ")
}

private fun qrImage(text: String): ImageBitmap {
    val size = 256
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
    val pixels = IntArray(size * size)
    for (y in 0 until size) {
        val row = y * size
        for (x in 0 until size) {
            pixels[row + x] = if (matrix.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
    }
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
    return bitmap.asImageBitmap()
}
