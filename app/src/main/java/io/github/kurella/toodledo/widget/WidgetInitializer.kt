package io.github.kurella.toodledo.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent

class WidgetInitializer : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            // Re-run full widget setup (rebinds RemoteAdapter + triggers data load)
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, TaskWidgetProvider::class.java)
            )
            if (ids.isNotEmpty()) {
                val provider = TaskWidgetProvider()
                provider.onUpdate(context, manager, ids)
            }
            RefreshWorker.schedule(context)
        }
    }
}
