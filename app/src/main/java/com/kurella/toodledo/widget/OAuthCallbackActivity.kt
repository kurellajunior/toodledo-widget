package com.kurella.toodledo.widget

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

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
        val authUrl = api.buildAuthUrl()
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(authUrl)))
        finish()
    }

    private fun handleCallback(uri: Uri) {
        val code = uri.getQueryParameter("code")
        if (code != null) {
            Thread {
                val api = ToodledoApi(TokenStore(this))
                val success = api.exchangeCode(code)
                if (success) {
                    TaskWidgetProvider.fullUpdate(this)
                }
                runOnUiThread { goHome() }
            }.start()
        } else {
            finish()
        }
    }

    private fun goHome() {
        // Navigate to home screen so user sees the widget, not Chrome's back stack
        startActivity(Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
        finish()
    }
}
