
package com.example.volt

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

object TelegramClient {

    private const val CHANNEL_URL =
        "https://t.me/s/pat_cherkasyoblenergo"

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS)
        .build()

    @Throws(IOException::class)
    fun fetchChannelHtml(): String {
        val request = Request.Builder()
            .url(CHANNEL_URL)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                        "AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36"
            )
            .header("Accept-Language", "uk-UA,uk;q=0.9,en;q=0.8")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException(
                    "Telegram повернув HTTP ${response.code}"
                )
            }

            return response.body?.string()
                ?: throw IOException("Telegram повернув порожню відповідь")
        }
    }
}
