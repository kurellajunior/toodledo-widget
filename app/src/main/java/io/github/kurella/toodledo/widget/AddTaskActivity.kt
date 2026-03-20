package io.github.kurella.toodledo.widget

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Trampoline activity: opens Toodledo app to add a task, or falls back to the website.
 * Launched via the "Add task" app shortcut (long-press on widget/app icon).
 */
class AddTaskActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openToodledo(this)
        finish()
    }
}
