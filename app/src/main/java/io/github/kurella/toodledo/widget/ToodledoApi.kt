package io.github.kurella.toodledo.widget

import android.util.Base64
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

sealed interface FetchResult {
    data class Success(val tasks: List<Task>) : FetchResult
    data object AuthError : FetchResult
    data object NetworkError : FetchResult
    data object ApiError : FetchResult
}

class ToodledoApi(private val tokenStore: TokenStore) {

    private val client = OkHttpClient()

    fun buildAuthUrl(): String {
        val clientId = URLEncoder.encode(BuildConfig.TOODLEDO_CLIENT_ID, "UTF-8")
        val redirectUri = URLEncoder.encode(BuildConfig.TOODLEDO_REDIRECT_URI, "UTF-8")
        return "$BASE_API_URL/account/authorize.php" +
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
            .url("$BASE_API_URL/account/token.php")
            .header("Authorization", "Basic $encoded")
            .post(body)
            .build()

        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use false
            val json = JSONObject(response.body.string())
            if (json.has("errorCode")) return@use false

            tokenStore.accessToken = json.getString("access_token")
            tokenStore.refreshToken = json.getString("refresh_token")
            // refresh 5s early to prevent edge cases between expiry check and request
            tokenStore.expiresAt = System.currentTimeMillis() / 1000 + json.getLong("expires_in") - 5
            true
        }
    }

    private fun ensureValidToken(): Boolean {
        return !tokenStore.isExpired || refreshTokens()
    }

    fun fetchTasks(): FetchResult {
        if (!ensureValidToken()) return FetchResult.AuthError

        val accessToken = URLEncoder.encode(tokenStore.accessToken, "UTF-8")
        val request = Request.Builder()
            .url("$BASE_API_URL/tasks/get.php" +
                    "?access_token=$accessToken" +
                    "&fields=priority,startdate,duedate,repeat,note" +
                    "&comp=0")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use FetchResult.ApiError
                val array = JSONArray(response.body.string())
                // First element is metadata (total count etc.), skip it
                val tasks = (1 until array.length())
                    .map { array.getJSONObject(it) }
                    .filter { it.optLong("duedate") != 0L }
                    .map { entry ->
                        Task(
                            id = entry.getLong("id"),
                            title = entry.getString("title"),
                            priority = Priority.from(entry.optInt("priority", 1)),
                            startDate = epochToDate(entry.optLong("startdate")),
                            dueDate = epochToDate(entry.optLong("duedate"))!!,
                            repeat = entry.optString("repeat"),
                            hasNote = entry.optString("note").isNotEmpty()
                        )
                    }
                FetchResult.Success(tasks)
            }
        } catch (_: java.io.IOException) {
            FetchResult.NetworkError
        } catch (_: org.json.JSONException) {
            FetchResult.ApiError
        }
    }

    fun completeTask(taskId: Long): Boolean {
        if (!ensureValidToken()) return false

        val body = FormBody.Builder()
            .add("access_token", tokenStore.accessToken ?: return false)
            .add("tasks", JSONArray().put(JSONObject()
                .put("id", taskId)
                .put("completed", System.currentTimeMillis() / 1000)
            ).toString())
            .build()

        return client.newCall(Request.Builder().url("$BASE_API_URL/tasks/edit.php").post(body).build()).execute().use { response ->
            // Toodledo returns a JSONArray on success, a JSONObject with errorCode on failure
            response.isSuccessful && JSONTokener(response.body.string()).nextValue() is JSONArray
            // TODO: return error string instead (Option?) and show error in status line
        }
    }

    fun rescheduleTask(taskId: Long, newDueDate: LocalDate, newStartDate: LocalDate?): Boolean {
        if (!ensureValidToken()) return false

        val body = FormBody.Builder()
            .add("access_token", tokenStore.accessToken ?: return false)
            .add("tasks", JSONArray().put(JSONObject()
                .put("id", taskId)
                .put("duedate", newDueDate.atStartOfDay(ZoneOffset.UTC).toEpochSecond())
                .putOpt("startdate", newStartDate?.atStartOfDay(ZoneOffset.UTC)?.toEpochSecond())
            ).toString())
            .build()

        return client.newCall(Request.Builder().url("$BASE_API_URL/tasks/edit.php").post(body).build()).execute().use { response ->
            response.isSuccessful && JSONTokener(response.body.string()).nextValue() is JSONArray
            // TODO: return error string instead (Option?) and show error in status line
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
        private const val BASE_API_URL = "https://api.toodledo.com/3"
    }
}
