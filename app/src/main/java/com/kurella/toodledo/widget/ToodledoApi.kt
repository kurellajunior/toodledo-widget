package com.kurella.toodledo.widget

import android.util.Base64
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private const val BASE_URL = "https://api.toodledo.com/3"
private const val TASK_FIELDS = "priority,startdate,duedate,repeat,note"

class ToodledoApi(private val tokenStore: TokenStore) {

    private val client = OkHttpClient()

    fun buildAuthUrl(): String {
        val clientId = URLEncoder.encode(BuildConfig.TOODLEDO_CLIENT_ID, "UTF-8")
        val redirectUri = URLEncoder.encode(BuildConfig.TOODLEDO_REDIRECT_URI, "UTF-8")
        return "$BASE_URL/account/authorize.php" +
            "?response_type=code" +
            "&client_id=$clientId" +
            "&state=auth" +
            "&scope=basic+tasks+write" +
            "&redirect_uri=$redirectUri"
    }

    fun exchangeCode(code: String): Boolean {
        val body = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", BuildConfig.TOODLEDO_REDIRECT_URI)
            .build()
        return executeTokenRequest(body)
    }

    fun refreshTokens(): Boolean {
        val token = tokenStore.refreshToken ?: return false
        val body = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", token)
            .build()
        return executeTokenRequest(body)
    }

    private fun executeTokenRequest(body: FormBody): Boolean {
        val credentials = "${BuildConfig.TOODLEDO_CLIENT_ID}:${BuildConfig.TOODLEDO_CLIENT_SECRET}"
        val encoded = Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)

        val request = Request.Builder()
            .url("$BASE_URL/account/token.php")
            .header("Authorization", "Basic $encoded")
            .post(body)
            .build()

        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use false
            val json = JSONObject(response.body?.string() ?: return@use false)
            if (json.has("errorCode")) return@use false

            tokenStore.accessToken = json.getString("access_token")
            tokenStore.refreshToken = json.getString("refresh_token")
            tokenStore.expiresAt = System.currentTimeMillis() / 1000 + json.getLong("expires_in")
            true
        }
    }

    private fun ensureValidToken(): Boolean {
        if (!tokenStore.isExpired) return true
        return refreshTokens()
    }

    fun fetchTasks(): List<Task>? {
        if (!ensureValidToken()) return null

        val accessToken = URLEncoder.encode(tokenStore.accessToken, "UTF-8")
        val request = Request.Builder()
            .url("$BASE_URL/tasks/get.php" +
                "?access_token=$accessToken" +
                "&fields=$TASK_FIELDS" +
                "&comp=0")
            .build()

        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val array = JSONArray(response.body?.string() ?: return@use null)
            // First element is metadata (total count etc.), skip it
            (1 until array.length())
                .map { array.getJSONObject(it) }
                .filter { it.optLong("duedate", 0) != 0L }
                .map { entry ->
                    Task(
                        id = entry.getLong("id"),
                        title = entry.getString("title"),
                        priority = Priority.from(entry.optInt("priority", 1)),
                        startDate = epochToDate(entry.optLong("startdate", 0)),
                        dueDate = epochToDate(entry.optLong("duedate", 0))!!,
                        repeat = entry.optString("repeat", ""),
                        hasNote = entry.optString("note", "").isNotEmpty()
                    )
                }
        }
    }

    fun completeTask(taskId: Long): Boolean {
        if (!ensureValidToken()) return false

        val now = System.currentTimeMillis() / 1000
        val body = FormBody.Builder()
            .add("access_token", tokenStore.accessToken ?: return false)
            .add("tasks", "[{\"id\":$taskId,\"completed\":$now}]")
            .build()

        val request = Request.Builder()
            .url("$BASE_URL/tasks/edit.php")
            .post(body)
            .build()

        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use false
            val responseBody = response.body?.string() ?: return@use false
            !JSONObject(responseBody).has("errorCode")
        }
    }

    private fun epochToDate(epoch: Long): LocalDate? {
        if (epoch == 0L) return null
        return Instant.ofEpochSecond(epoch)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
    }

    companion object {
        const val WEB_TASKS_URL = "https://www.toodledo.com/tasks/index.php"
    }
}
