package com.kurella.toodledo.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private val prefs by lazy {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val transparencyBar = findViewById<SeekBar>(R.id.transparency_bar)
        val transparencyLabel = findViewById<TextView>(R.id.transparency_label)
        val fontSizeBar = findViewById<SeekBar>(R.id.font_size_bar)
        val fontSizeLabel = findViewById<TextView>(R.id.font_size_label)
        val loginButton = findViewById<Button>(R.id.login_button)

        // Transparency: 0-100% in 5% steps, default 25%
        transparencyBar.progress = prefs.getInt(PREF_TRANSPARENCY, 25) / 5
        transparencyLabel.text = "${transparencyBar.progress * 5}%"
        transparencyBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, value: Int, fromUser: Boolean) {
                transparencyLabel.text = "${value * 5}%"
            }
            override fun onStartTrackingTouch(bar: SeekBar) {}
            override fun onStopTrackingTouch(bar: SeekBar) {
                prefs.edit().putInt(PREF_TRANSPARENCY, bar.progress * 5).apply()
                TaskWidgetProvider.updateLayout(this@SettingsActivity)
            }
        })

        // Font size: 50-200% in 5% steps, default 100%
        fontSizeBar.progress = (prefs.getInt(PREF_FONT_SIZE, 100) - 50) / 5
        fontSizeLabel.text = "${fontSizeBar.progress * 5 + 50}%"
        fontSizeBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, value: Int, fromUser: Boolean) {
                fontSizeLabel.text = "${value * 5 + 50}%"
            }
            override fun onStartTrackingTouch(bar: SeekBar) {}
            override fun onStopTrackingTouch(bar: SeekBar) {
                prefs.edit().putInt(PREF_FONT_SIZE, bar.progress * 5 + 50).apply()
                TaskWidgetProvider.fullUpdate(this@SettingsActivity)
            }
        })

        // Login / Logout
        val tokenStore = TokenStore(this)
        updateLoginButton(loginButton, tokenStore)
        loginButton.setOnClickListener {
            if (tokenStore.isLoggedIn) {
                tokenStore.clear()
                updateLoginButton(loginButton, tokenStore)
                TaskWidgetProvider.fullUpdate(this)
            } else {
                val api = ToodledoApi(tokenStore)
                startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(api.buildAuthUrl())))
            }
        }

        // If opened as widget configure activity, always confirm so widget isn't removed
        val widgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            setResult(RESULT_OK, Intent().apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            })
            // Trigger full widget update when configure finishes
            TaskWidgetProvider.fullUpdate(this)
        }

        // Schedule periodic refresh
        RefreshWorker.schedule(this)
    }

    override fun onResume() {
        super.onResume()
        val loginButton = findViewById<Button>(R.id.login_button)
        updateLoginButton(loginButton, TokenStore(this))
    }

    private fun updateLoginButton(button: Button, tokenStore: TokenStore) {
        val hint = findViewById<TextView>(R.id.login_hint)
        if (tokenStore.isLoggedIn) {
            button.text = getString(R.string.logout)
            hint.text = getString(R.string.logout_hint)
        } else {
            button.text = getString(R.string.login)
            hint.text = getString(R.string.login_hint)
        }
    }
}
