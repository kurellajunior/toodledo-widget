package io.github.kurella.toodledo.widget

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import io.github.kurella.toodledo.widget.R

class OAuthCallbackActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        when {
            intent?.action == "login" -> startLogin()
            intent?.data?.scheme == "toodledowidget" -> handleCallback(intent.data!!)
            else -> finish()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.let { handleCallback(it) }
    }

    private fun startLogin() {
        val api = ToodledoApi(TokenStore(this))
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(api.buildAuthUrl())))
        finish()
    }

    private fun handleCallback(uri: Uri) {
        val code = uri.getQueryParameter("code")
        if (code != null) {
            val appContext = applicationContext
            Thread {
                val success = try {
                    ToodledoApi(TokenStore(appContext)).exchangeCode(code)
                } catch (e: Exception) {
                    Log.e("ToodledoWidget", "OAuth exchange failed", e)
                    false
                }
                if (success) {
                    TaskWidgetProvider.fullUpdate(appContext)
                } else {
                    runOnUiThread {
                        Toast.makeText(appContext, R.string.error_auth, Toast.LENGTH_LONG).show()
                    }
                }
                runOnUiThread { goHome() }
            }.start()
        } else {
            finish()
        }
    }

    /** Navigate to home screen so user sees the widget, not Chrome's back stack. */
    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
        finish()
    }
}
