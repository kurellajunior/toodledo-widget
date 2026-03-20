package com.kurella.toodledo.widget

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Trampoline activity: opens Toodledo app to add a task, or falls back to the website.
 * Launched via the "Add task" app shortcut (long-press on widget/app icon).
 */
class AddTaskActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appIntent = packageManager.getLaunchIntentForPackage("com.toodledo")
        if (appIntent != null) {
            appIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(appIntent)
        } else {
            startActivity(Intent(Intent.ACTION_VIEW,
                Uri.parse(ToodledoApi.WEB_TASKS_URL)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        }
        finish()
    }
}
