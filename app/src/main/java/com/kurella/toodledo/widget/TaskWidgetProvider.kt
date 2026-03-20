package com.kurella.toodledo.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.util.Log
import android.view.View
import android.widget.RemoteViews

private const val TAG = "ToodledoWidget"

class TaskWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_REFRESH = "com.kurella.toodledo.widget.REFRESH"

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, TaskWidgetProvider::class.java)
            )
            manager.notifyAppWidgetViewDataChanged(ids, R.id.task_list)
        }

        /** Full update including layout changes (transparency, status line) */
        fun fullUpdate(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, TaskWidgetProvider::class.java)
            )
            if (ids.isNotEmpty()) {
                TaskWidgetProvider().onUpdate(context, manager, ids)
            }
        }

        /** Layout-only update (background, text colors) without rebinding adapter */
        fun updateLayout(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, TaskWidgetProvider::class.java)
            )
            if (ids.isEmpty()) return

            val prefs = context.getSharedPreferences("widget_settings", Context.MODE_PRIVATE)
            val transparency = prefs.getInt("transparency", 25)
            val alpha = ((100 - transparency) * 255 / 100)
            val isDark = (context.resources.configuration.uiMode
                and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val baseColor = if (isDark) 0x303030 else 0xFFFFFF
            val bgColor = (alpha shl 24) or baseColor

            for (widgetId in ids) {
                val views = RemoteViews(context.packageName, R.layout.widget_task_list)
                views.setInt(R.id.widget_background, "setBackgroundColor", bgColor)
                manager.partiallyUpdateAppWidget(widgetId, views)
            }
        }
    }

    override fun onUpdate(
        context: Context, manager: AppWidgetManager, widgetIds: IntArray
    ) {
        Log.d(TAG, "onUpdate: ${widgetIds.size} widgets")
        val tokenStore = TokenStore(context)
        val prefs = context.getSharedPreferences("widget_settings", Context.MODE_PRIVATE)
        val transparency = prefs.getInt("transparency", 25)
        // transparency 0% = fully opaque (alpha 255), 100% = fully transparent (alpha 0)
        val alpha = ((100 - transparency) * 255 / 100)
        val isDarkMode = (context.resources.configuration.uiMode
            and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val baseColor = if (isDarkMode) 0x303030 else 0xFFFFFF
        val bgColor = (alpha shl 24) or baseColor

        val textColor = if (isDarkMode) 0xFFE0E0E0.toInt() else 0xFF000000.toInt()

        for (widgetId in widgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_task_list)
            views.setInt(R.id.widget_background, "setBackgroundColor", bgColor)
            views.setTextColor(R.id.status_line, textColor)
            views.setTextColor(R.id.empty_view, textColor)

            if (!tokenStore.isLoggedIn) {
                views.setViewVisibility(R.id.status_line, View.VISIBLE)
                views.setTextViewText(R.id.status_line,
                    context.getString(R.string.not_logged_in))

                val loginIntent = Intent(context, OAuthCallbackActivity::class.java).apply {
                    action = "login"
                }
                val loginPending = PendingIntent.getActivity(
                    context, 0, loginIntent, PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.status_line, loginPending)
            } else {
                views.setViewVisibility(R.id.status_line, View.GONE)
            }

            // ListView adapter
            val serviceIntent = Intent(context, TaskWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.task_list, serviceIntent)
            views.setScrollPosition(R.id.task_list, 0)
            views.setEmptyView(R.id.task_list, R.id.empty_view)

            // Item click template
            val clickIntent = Intent(context, TaskWidgetProvider::class.java).apply {
                action = "item_click"
            }
            val clickPending = PendingIntent.getBroadcast(
                context, 0, clickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            views.setPendingIntentTemplate(R.id.task_list, clickPending)

            // Tap on empty view → refresh
            val refreshIntent = Intent(context, TaskWidgetProvider::class.java).apply {
                action = ACTION_REFRESH
            }
            val refreshPending = PendingIntent.getBroadcast(
                context, 1, refreshIntent, PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.empty_view, refreshPending)

            manager.updateAppWidget(widgetId, views)
        }

        // Trigger data refresh after adapter is bound
        manager.notifyAppWidgetViewDataChanged(widgetIds, R.id.task_list)

        // Ensure periodic refresh is scheduled
        RefreshWorker.schedule(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        when (intent.getStringExtra("action")) {
            "complete" -> {
                val taskId = intent.getLongExtra("task_id", -1)
                if (taskId != -1L) {
                    Thread {
                        try {
                            val api = ToodledoApi(TokenStore(context))
                            api.completeTask(taskId)
                        } catch (e: Exception) {
                            Log.e(TAG, "completeTask failed", e)
                        }
                        refresh(context)
                    }.start()
                }
            }
            "open" -> {
                val taskId = intent.getLongExtra("task_id", -1)
                if (taskId != -1L) {
                    val openIntent = context.packageManager
                        .getLaunchIntentForPackage("com.toodledo")
                    if (openIntent != null) {
                        openIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        context.startActivity(openIntent)
                    } else {
                        val url = "https://www.toodledo.com/tasks/index.php"
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        })
                    }
                }
            }
            "refresh" -> {
                Log.d(TAG, "Refresh from list item")
                // Full update scrolls to top; refresh only reloads data
                fullUpdate(context)
            }
        }

        if (intent.action == ACTION_REFRESH) {
            Log.d(TAG, "ACTION_REFRESH received")
            refresh(context)
        }
    }
}
