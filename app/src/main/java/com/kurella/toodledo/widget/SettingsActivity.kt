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

        val loginButton = findViewById<Button>(R.id.login_button)

        bindSeekBar(
            barId = R.id.transparency_bar, labelId = R.id.transparency_label,
            prefKey = PREF_TRANSPARENCY, default = 25, offset = 0, step = 5
        ) { TaskWidgetProvider.updateLayout(this) }

        bindSeekBar(
            barId = R.id.font_size_bar, labelId = R.id.font_size_label,
            prefKey = PREF_FONT_SIZE, default = 100, offset = 50, step = 5
        ) { TaskWidgetProvider.fullUpdate(this) }

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

        val widgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            setResult(RESULT_OK, Intent().apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            })
            TaskWidgetProvider.fullUpdate(this)
        }

        RefreshWorker.schedule(this)
    }

    override fun onResume() {
        super.onResume()
        val loginButton = findViewById<Button>(R.id.login_button)
        updateLoginButton(loginButton, TokenStore(this))
    }

    private fun bindSeekBar(
        barId: Int, labelId: Int,
        prefKey: String, default: Int, offset: Int, step: Int,
        onCommit: () -> Unit
    ) {
        val bar = findViewById<SeekBar>(barId)
        val label = findViewById<TextView>(labelId)
        fun displayValue(progress: Int) = "${progress * step + offset}%"

        bar.progress = (prefs.getInt(prefKey, default) - offset) / step
        label.text = displayValue(bar.progress)
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, value: Int, fromUser: Boolean) {
                label.text = displayValue(value)
            }
            override fun onStartTrackingTouch(bar: SeekBar) {}
            override fun onStopTrackingTouch(bar: SeekBar) {
                prefs.edit().putInt(prefKey, bar.progress * step + offset).apply()
                onCommit()
            }
        })
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
