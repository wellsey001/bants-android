package com.wellsey.bants.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

val BantsJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
}

class ApiException(val status: Int, message: String, val code: String? = null) : Exception(message)

/** Thin HTTP client for the Bants server's JSON API. */
class Api(private val session: Session) {
    val json: Json = BantsJson

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .pingInterval(25, TimeUnit.SECONDS)
        .build()

    /** Adds the sign-in token to every request. Also used by the image loader. */
    val authedHttp: OkHttpClient = http.newBuilder()
        .addInterceptor(Interceptor { chain ->
            val token = session.token
            val req = chain.request()
            chain.proceed(
                if (token != null && req.header("Authorization") == null) {
                    req.newBuilder().header("Authorization", "Bearer $token").build()
                } else req,
            )
        })
        .build()

    suspend fun raw(
        method: String,
        path: String,
        body: RequestBody? = null,
        headers: Map<String, String> = emptyMap(),
    ): String = withContext(Dispatchers.IO) {
        val url = (session.serverUrl + path).toHttpUrlOrNull()
            ?: throw ApiException(0, "That server address doesn't look right")
        val builder = Request.Builder().url(url)
        headers.forEach { (k, v) -> builder.header(k, v) }
        val needsBody = method == "POST" || method == "PATCH" || method == "PUT"
        builder.method(method, body ?: if (needsBody) "{}".toRequestBody(JSON_TYPE) else null)

        val response = try {
            authedHttp.newCall(builder.build()).execute()
        } catch (e: IOException) {
            throw ApiException(0, "Can't reach the server. Check your connection.")
        }
        response.use { res ->
            val text = res.body?.string().orEmpty()
            if (!res.isSuccessful) {
                val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
                throw ApiException(
                    res.code,
                    obj?.get("error")?.jsonPrimitive?.contentOrNull ?: "Server error (${res.code})",
                    obj?.get("code")?.jsonPrimitive?.contentOrNull,
                )
            }
            text
        }
    }

    fun body(obj: JsonObject): RequestBody = obj.toString().toRequestBody(JSON_TYPE)

    suspend inline fun <reified T> get(path: String): T =
        json.decodeFromString<T>(raw("GET", path))

    suspend inline fun <reified T> post(path: String, obj: JsonObject = JsonObject(emptyMap())): T =
        json.decodeFromString<T>(raw("POST", path, body(obj)))

    suspend inline fun <reified T> patch(path: String, obj: JsonObject): T =
        json.decodeFromString<T>(raw("PATCH", path, body(obj)))

    suspend inline fun <reified T> delete(path: String, obj: JsonObject? = null): T =
        json.decodeFromString<T>(raw("DELETE", path, obj?.let { body(it) }))

    companion object {
        val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
