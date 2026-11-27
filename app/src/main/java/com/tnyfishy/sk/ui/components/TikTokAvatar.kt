package com.tnyfishy.sk.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import com.tnyfishy.sk.AppConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

object AvatarCache {
    private val memCache = object : LruCache<String, ImageBitmap>(80) {}

    fun getMemory(url: String): ImageBitmap? = memCache.get(url)

    suspend fun load(context: Context, url: String): ImageBitmap? {
        if (url.isBlank() || !url.startsWith("http", ignoreCase = true)) return null
        memCache.get(url)?.let { return it }

        return withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "avatars").apply { mkdirs() }
            val file = File(dir, hashKey(url) + ".img")

            val bytes = if (file.exists() && file.length() > 128) {
                runCatching { file.readBytes() }.getOrNull()
            } else {
                downloadBytes(url)?.also { data ->
                    runCatching { file.writeBytes(data) }
                }
            } ?: return@withContext null

            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            var sample = 1
            while (opts.outWidth / sample > 256 || opts.outHeight / sample > 256) {
                sample *= 2
            }
            val decoded = BitmapFactory.decodeByteArray(
                bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }
            )?.asImageBitmap()

            if (decoded != null) {
                memCache.put(url, decoded)
            }
            decoded
        }
    }

    private fun downloadBytes(url: String): ByteArray? = runCatching {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 6000
            readTimeout = 6000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", AppConstants.DESKTOP_CHROME_UA)
            setRequestProperty("Referer", "https://www.tiktok.com/")
        }
        try {
            if (conn.responseCode in 200..299) conn.inputStream.readBytes() else null
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    private fun hashKey(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return digest.take(12).joinToString("") { "%02x".format(it) }
    }
}

@Composable
fun TikTokAvatar(
    avatarUrl: String,
    fallbackLabel: String,
    isGroup: Boolean,
    shape: Shape,
    size: Dp,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    context: Context = LocalContext.current,
    fallbackContent: (@Composable () -> Unit)? = null
) {
    var bitmap by remember(avatarUrl) { mutableStateOf(AvatarCache.getMemory(avatarUrl)) }
    val motion = MaterialTheme.motionScheme

    LaunchedEffect(avatarUrl, context) {
        if (bitmap == null && avatarUrl.isNotBlank()) {
            bitmap = AvatarCache.load(context, avatarUrl)
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(containerColor),
        contentAlignment = Alignment.Center
    ) {
        Crossfade(
            targetState = bitmap,
            animationSpec = motion.defaultEffectsSpec(),
            label = "avatar_fade"
        ) { img ->
            if (img != null) {
                Image(
                    bitmap = img,
                    contentDescription = fallbackLabel,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (fallbackContent != null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    fallbackContent()
                }
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (isGroup) {
                        Icon(
                            imageVector = Icons.Rounded.Group,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(size * 0.48f)
                        )
                    } else {
                        val initial = fallbackLabel.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
                        Text(
                            text = initial,
                            style = MaterialTheme.typography.titleMediumEmphasized,
                            color = contentColor
                        )
                    }
                }
            }
        }
    }
}
