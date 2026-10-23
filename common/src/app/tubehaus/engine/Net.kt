package app.tubehaus.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class HttpError(val code: Int, msg: String) : IOException(msg)

/** The one HTTP client of the app. Everything goes straight from this device to YouTube. */
object Net {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val JSON = "application/json".toMediaType()

    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): String = withContext(Dispatchers.IO) {
        val b = Request.Builder().url(url)
        headers.forEach { (k, v) -> b.header(k, v) }
        client.newCall(b.build()).execute().use { r ->
            val body = r.body?.string() ?: ""
            if (!r.isSuccessful) throw HttpError(r.code, "HTTP ${r.code}")
            body
        }
    }

    suspend fun post(url: String, body: String, headers: Map<String, String> = emptyMap()): String = withContext(Dispatchers.IO) {
        val b = Request.Builder().url(url).post(body.toRequestBody(JSON))
        headers.forEach { (k, v) -> b.header(k, v) }
        client.newCall(b.build()).execute().use { r ->
            val text = r.body?.string() ?: ""
            if (!r.isSuccessful) throw HttpError(r.code, "HTTP ${r.code}")
            text
        }
    }
}
