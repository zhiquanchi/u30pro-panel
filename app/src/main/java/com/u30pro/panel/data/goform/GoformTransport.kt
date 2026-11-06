package com.u30pro.panel.data.goform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** In-memory cookie jar; JSESSIONID is scoped to /goform by the device. */
class MemoryCookieJar : CookieJar {

    private val cookies = mutableListOf<Cookie>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        for (cookie in cookies) {
            this.cookies.removeAll {
                it.name == cookie.name && it.domain == cookie.domain && it.path == cookie.path
            }
            this.cookies.add(cookie)
        }
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> =
        cookies.filter { it.matches(url) }
}

/**
 * Low-level HTTP for the stock goform endpoints. All calls are serialized by
 * the repository's mutex; this class only handles transport + envelope errors.
 */
class GoformTransport(private val http: OkHttpClient) {

    @Volatile
    var origin: String = "http://192.168.0.1"
        private set

    fun setOrigin(gateway: String, port: Int) {
        val host = gateway
            .trim()
            .removePrefix("http://")
            .removePrefix("https://")
            .trimEnd('/')
        origin = if (port == 80) "http://$host" else "http://$host:$port"
    }

    suspend fun get(params: Map<String, String>): JsonObject = withContext(Dispatchers.IO) {
        val base = (origin + GET_CMD).toHttpUrlOrNull()
            ?: throw GoformException(GoformException.Kind.NOT_REACHABLE, "bad gateway")
        val url = base.newBuilder()
            .apply { params.forEach { (key, value) -> addQueryParameter(key, value) } }
            .build()
        val request = Request.Builder()
            .url(url)
            .get()
            .headers(commonHeaders())
            .build()
        execute(request)
    }

    suspend fun post(params: Map<String, String>): JsonObject = withContext(Dispatchers.IO) {
        val url = (origin + SET_CMD).toHttpUrlOrNull()
            ?: throw GoformException(GoformException.Kind.NOT_REACHABLE, "bad gateway")
        val body = encodeForm(params).toRequestBody(FORM_CONTENT_TYPE.toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(body)
            .headers(commonHeaders())
            .build()
        execute(request)
    }

    private fun commonHeaders(): okhttp3.Headers = okhttp3.Headers.Builder()
        // Mandatory. Without these the device answers {"Error":"none secure connection"}.
        .add("X-Requested-With", "XMLHttpRequest")
        .add("Referer", "$origin/")
        .add("Accept", "application/json, text/plain, */*")
        .add("User-Agent", "Mozilla/5.0 (Linux; Android) U30ProPanel/0.1")
        .build()

    private fun execute(request: Request): JsonObject {
        val response = try {
            http.newCall(request).execute()
        } catch (io: IOException) {
            throw GoformException(
                GoformException.Kind.NOT_REACHABLE,
                "cannot reach $origin: ${io.message}",
                io,
            )
        }
        return response.use {
            val text = try {
                it.body?.string().orEmpty()
            } catch (io: IOException) {
                throw GoformException(
                    GoformException.Kind.NOT_REACHABLE,
                    "cannot read response: ${io.message}",
                    io,
                )
            }
            if (!it.isSuccessful) {
                throw GoformException(
                    GoformException.Kind.NOT_REACHABLE,
                    "HTTP ${it.code} from $origin",
                )
            }
            if (text.isBlank()) {
                throw GoformException(GoformException.Kind.PARSE, "empty response")
            }
            if (text.trimStart().startsWith("<")) {
                throw GoformException(GoformException.Kind.NOT_LOGGED_IN, "got html, session lost")
            }
            val json = parseJsonObject(text)
                ?: throw GoformException(
                    GoformException.Kind.PARSE,
                    "not json: ${text.take(120)}",
                )
            val error = json["Error"]?.jsonPrimitive?.contentOrNull
            if (!error.isNullOrBlank()) {
                val kind = if (error.contains("secure", ignoreCase = true)) {
                    GoformException.Kind.SECURE_HEADER
                } else {
                    GoformException.Kind.DEVICE
                }
                throw GoformException(kind, error)
            }
            json
        }
    }

    companion object {
        const val GET_CMD = "/goform/goform_get_cmd_process"
        const val SET_CMD = "/goform/goform_set_cmd_process"
        private const val FORM_CONTENT_TYPE = "application/x-www-form-urlencoded; charset=UTF-8"

        fun defaultClient(socketFactory: javax.net.SocketFactory, dns: okhttp3.Dns): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(6, TimeUnit.SECONDS)
                .readTimeout(12, TimeUnit.SECONDS)
                .writeTimeout(12, TimeUnit.SECONDS)
                .callTimeout(20, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .socketFactory(socketFactory)
                .dns(dns)
                .cookieJar(MemoryCookieJar())
                .build()

        /** Percent-encode the form by hand to keep the AD token last. */
        fun encodeForm(params: Map<String, String>): String =
            params.entries.joinToString("&") { (key, value) ->
                "${encode(key)}=${encode(value)}"
            }

        private fun encode(value: String): String =
            java.net.URLEncoder.encode(value, "UTF-8")
    }
}