package com.kurella.toodledo.widget

import android.util.Base64
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ToodledoApi(private val tokenStore: TokenStore) {

    private val client = OkHttpClient()
    private val baseUrl = "https://api.toodledo.com/3"

    fun buildAuthUrl(): String {
        val clientId = BuildConfig.TOODLEDO_CLIENT_ID
        val redirectUri = BuildConfig.TOODLEDO_REDIRECT_URI
        return "$baseUrl/account/authorize.php" +
            "?response_type=code" +
            "&client_id=$clientId" +
            "&state=auth" +
            "&scope=basic tasks write" +
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
        val rt = tokenStore.refreshToken ?: return false
        val body = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", rt)
            .build()
        return executeTokenRequest(body)
    }

    private fun executeTokenRequest(body: FormBody): Boolean {
        val credentials = "${BuildConfig.TOODLEDO_CLIENT_ID}:${BuildConfig.TOODLEDO_CLIENT_SECRET}"
        val encoded = Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)

        val request = Request.Builder()
            .url("$baseUrl/account/token.php")
            .header("Authorization", "Basic $encoded")
            .post(body)
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) return false

        val json = JSONObject(response.body!!.string())
        if (json.has("errorCode")) return false

        tokenStore.accessToken = json.getString("access_token")
        tokenStore.refreshToken = json.getString("refresh_token")
        tokenStore.expiresAt = System.currentTimeMillis() / 1000 + json.getLong("expires_in")
        return true
    }

    private fun ensureValidToken(): Boolean {
        if (!tokenStore.isExpired) return true
        return refreshTokens()
    }

    fun fetchTasks(): List<Task>? {
        if (!ensureValidToken()) return null

        val request = Request.Builder()
            .url("$baseUrl/tasks/get.php" +
                "?access_token=${tokenStore.accessToken}" +
                "&fields=priority,startdate,duedate,repeat" +
                "&comp=0")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) return null

        val array = JSONArray(response.body!!.string())
        // First element is metadata (total count etc.), skip it
        val tasks = mutableListOf<Task>()
        for (i in 1 until array.length()) {
            val obj = array.getJSONObject(i)
            val dueEpoch = obj.optLong("duedate", 0)
            if (dueEpoch == 0L) continue

            tasks += Task(
                id = obj.getLong("id"),
                title = obj.getString("title"),
                priority = Priority.from(obj.optInt("priority", 1)),
                startDate = epochToDate(obj.optLong("startdate", 0)),
                dueDate = epochToDate(dueEpoch)!!,
                repeat = obj.optString("repeat", "")
            )
        }
        return tasks
    }

    fun completeTask(taskId: Long): Boolean {
        if (!ensureValidToken()) return false

        val now = System.currentTimeMillis() / 1000
        val body = FormBody.Builder()
            .add("access_token", tokenStore.accessToken!!)
            .add("tasks", "[{\"id\":$taskId,\"completed\":$now}]")
            .build()

        val request = Request.Builder()
            .url("$baseUrl/tasks/edit.php")
            .post(body)
            .build()

        val response = client.newCall(request).execute()
        return response.isSuccessful
    }

    private fun epochToDate(epoch: Long): LocalDate? {
        if (epoch == 0L) return null
        return Instant.ofEpochSecond(epoch)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
    }
}
