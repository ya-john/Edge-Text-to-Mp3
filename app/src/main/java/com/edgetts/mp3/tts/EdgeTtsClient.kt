package com.edgetts.mp3.tts

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class Prosody(
    /** Speaking rate change in percent, e.g. -25 or +40. */
    val ratePercent: Int = 0,
    /** Pitch change in hertz, e.g. -10 or +20. */
    val pitchHz: Int = 0,
) {
    val rateString get() = (if (ratePercent >= 0) "+" else "") + "$ratePercent%"
    val pitchString get() = (if (pitchHz >= 0) "+" else "") + "${pitchHz}Hz"
}

class TtsException(message: String, val retryable: Boolean = true, cause: Throwable? = null) :
    IOException(message, cause)

/**
 * Minimal client for the websocket API behind Microsoft Edge's "Read aloud".
 * One call synthesises one chunk of plain text into MP3 bytes (24 kHz, 48 kbps, mono CBR).
 */
class EdgeTtsClient(private val http: OkHttpClient = defaultHttpClient()) {

    companion object {
        const val CHROMIUM_FULL_VERSION = "143.0.3650.75"
        const val SEC_MS_GEC_VERSION = "2-$CHROMIUM_FULL_VERSION"
        private const val CHROMIUM_MAJOR = "143"
        private const val WSS_URL =
            "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1"
        const val OUTPUT_FORMAT = "audio-24khz-48kbitrate-mono-mp3"
        const val BITRATE_BPS = 48_000

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS) // stalls are handled by the idle watchdog
            .pingInterval(15, TimeUnit.SECONDS)
            .build()

        private fun timestamp(): String =
            SimpleDateFormat("EEE MMM dd yyyy HH:mm:ss 'GMT+0000 (Coordinated Universal Time)'", Locale.US)
                .apply { timeZone = TimeZone.getTimeZone("UTC") }
                .format(Date())

        private fun randomHex() = UUID.randomUUID().toString().replace("-", "")
    }

    /**
     * Edge streams audio at roughly real-time pace, so a long chunk can legitimately take minutes.
     * Instead of a short fixed timeout, fail when nothing arrives for [idleTimeoutMs].
     */
    suspend fun synthesize(
        text: String,
        voice: Voice,
        prosody: Prosody,
        idleTimeoutMs: Long = 30_000,
        maxTotalMs: Long = 15 * 60_000,
    ): ByteArray = withTimeout(maxTotalMs) {
        coroutineScope {
            val lastActivity = AtomicLong(System.currentTimeMillis())
            val watchdog = launch {
                while (true) {
                    delay(2_000)
                    if (System.currentTimeMillis() - lastActivity.get() > idleTimeoutMs) {
                        throw TtsException("Edge stopped sending audio (stalled for ${idleTimeoutMs / 1000}s)")
                    }
                }
            }
            try {
                synthesizeOnce(text, voice, prosody, lastActivity)
            } finally {
                watchdog.cancel()
            }
        }
    }

    private suspend fun synthesizeOnce(text: String, voice: Voice, prosody: Prosody, lastActivity: AtomicLong): ByteArray =
        suspendCancellableCoroutine { cont ->
            val url = "$WSS_URL?TrustedClientToken=${SecMsGec.TRUSTED_CLIENT_TOKEN}" +
                "&ConnectionId=${randomHex()}" +
                "&Sec-MS-GEC=${SecMsGec.generate()}" +
                "&Sec-MS-GEC-Version=$SEC_MS_GEC_VERSION"

            val request = Request.Builder()
                .url(url)
                .header("Pragma", "no-cache")
                .header("Cache-Control", "no-cache")
                .header("Origin", "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold")
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                        "Chrome/$CHROMIUM_MAJOR.0.0.0 Safari/537.36 Edg/$CHROMIUM_MAJOR.0.0.0",
                )
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Cookie", "muid=${randomHex().uppercase()};")
                .build()

            val audio = ByteArrayOutputStream()
            val listener = object : WebSocketListener() {
                @Volatile var done = false

                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send(
                        "X-Timestamp:${timestamp()}\r\n" +
                            "Content-Type:application/json; charset=utf-8\r\n" +
                            "Path:speech.config\r\n\r\n" +
                            "{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":{" +
                            "\"sentenceBoundaryEnabled\":\"false\",\"wordBoundaryEnabled\":\"false\"}," +
                            "\"outputFormat\":\"$OUTPUT_FORMAT\"}}}}\r\n",
                    )
                    webSocket.send(
                        "X-RequestId:${randomHex()}\r\n" +
                            "Content-Type:application/ssml+xml\r\n" +
                            "X-Timestamp:${timestamp()}Z\r\n" + // trailing Z mirrors Edge itself
                            "Path:ssml\r\n\r\n" +
                            ssml(text, voice, prosody),
                    )
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    lastActivity.set(System.currentTimeMillis())
                    if (text.contains("Path:turn.end")) {
                        done = true
                        webSocket.close(1000, null)
                        if (cont.isActive) {
                            if (audio.size() == 0) cont.resumeWithException(TtsException("No audio received for chunk"))
                            else cont.resume(audio.toByteArray())
                        }
                    }
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    lastActivity.set(System.currentTimeMillis())
                    if (bytes.size < 2) return
                    val headerLen = ((bytes[0].toInt() and 0xFF) shl 8) or (bytes[1].toInt() and 0xFF)
                    if (bytes.size < 2 + headerLen) return
                    val header = bytes.substring(2, 2 + headerLen).utf8()
                    if (!header.contains("Path:audio")) return
                    val body = bytes.substring(2 + headerLen)
                    if (body.size > 0) synchronized(audio) { audio.write(body.toByteArray()) }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (done || !cont.isActive) return
                    if (response?.code == 403) {
                        adjustClockSkew(response)
                        cont.resumeWithException(TtsException("Edge rejected the request (403). Retrying with corrected clock.", true, t))
                    } else {
                        val code = response?.code?.let { " (HTTP $it)" } ?: ""
                        cont.resumeWithException(TtsException("Connection failed$code: ${t.message}", true, t))
                    }
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(1000, null)
                    onClosed(webSocket, code, reason)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (!done && cont.isActive) {
                        cont.resumeWithException(TtsException("Server closed the connection early ($code $reason)"))
                    }
                }
            }

            val ws = http.newWebSocket(request, listener)
            cont.invokeOnCancellation { ws.cancel() }
        }

    private fun adjustClockSkew(response: Response) {
        val date = response.header("Date") ?: return
        runCatching {
            val server = ZonedDateTime.parse(date, DateTimeFormatter.RFC_1123_DATE_TIME).toEpochSecond()
            val client = System.currentTimeMillis() / 1000 + SecMsGec.clockSkewSeconds
            SecMsGec.clockSkewSeconds += server - client
        }
    }

    private fun ssml(text: String, voice: Voice, prosody: Prosody): String =
        "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='en-US'>" +
            "<voice name='${voice.ssmlName}'>" +
            "<prosody pitch='${prosody.pitchString}' rate='${prosody.rateString}' volume='+0%'>" +
            TextChunker.escapeXml(text) +
            "</prosody></voice></speak>"
}
