package io.github.kurella.toodledo.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews

/**
 * 1x1 widget that opens the Toodledo "Add Task" screen.
 *
 * If the Toodledo app is installed, sends an ACTION_SEND intent which
 * opens the task creation dialog. Otherwise falls back to the web UI.
 */
class AddTaskWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        widgetIds: IntArray
    ) {
        for (id in widgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_add_task)
            views.setOnClickPendingIntent(R.id.add_task_icon, addTaskPendingIntent(context))
            manager.updateAppWidget(id, views)
        }
    }

    private fun addTaskPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, AddTaskWidgetProvider::class.java).apply {
            action = "add_task"
        }
        return PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == "add_task") {
            addTask(context)
        }
    }

    companion object {
        /** Opens Toodledo task creation (app via SEND intent, or web fallback). */
        fun addTask(context: Context) {
            val appInstalled = context.packageManager
                .getLaunchIntentForPackage(TOODLEDO_PACKAGE) != null
            if (appInstalled) {
                context.startActivity(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, " ")
                    setPackage(TOODLEDO_PACKAGE)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
            } else {
                context.startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://www.toodledo.com/tasks/add.php")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
            }
        }
    }
}
