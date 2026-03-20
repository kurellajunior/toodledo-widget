package io.github.kurella.toodledo.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import android.widget.Toast
import io.github.kurella.toodledo.widget.R

private const val TAG = "ToodledoWidget"
const val TOODLEDO_PACKAGE = "com.toodledo"

const val PREFS_NAME = "widget_settings"
const val PREF_TRANSPARENCY = "transparency"
const val PREF_FONT_SIZE = "font_size"
private const val DEFAULT_TRANSPARENCY = 25
const val DEFAULT_FONT_SIZE = 100

internal const val LIGHT_BASE = 0xFFFFFF
internal const val DARK_BASE = 0x303030
internal const val LIGHT_SECTION_BASE = 0x424242
internal const val DARK_SECTION_BASE = 0x505050
internal const val LIGHT_TEXT = 0xFF000000.toInt()
internal const val DARK_TEXT = 0xFFE0E0E0.toInt()

/** Opens Toodledo app if installed, otherwise falls back to web. */
fun openToodledo(context: Context) {
    val appIntent = context.packageManager.getLaunchIntentForPackage(TOODLEDO_PACKAGE)
    if (appIntent != null) {
        appIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(appIntent)
    } else {
        context.startActivity(Intent(Intent.ACTION_VIEW,
            Uri.parse(ToodledoApi.WEB_TASKS_URL)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
    }
}

class TaskWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_REFRESH = "io.github.kurella.toodledo.widget.REFRESH"

        private fun widgetIds(context: Context): Pair<AppWidgetManager, IntArray> {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, TaskWidgetProvider::class.java)
            )
            return manager to ids
        }

        fun refresh(context: Context) {
            val (manager, ids) = widgetIds(context)
            // TODO migrate to RemoteCollectionItems when minSdk >= 31 (API 35 replacement)
            @Suppress("DEPRECATION")
            manager.notifyAppWidgetViewDataChanged(ids, R.id.task_list)
        }

        /** Full update including layout changes (transparency, status line). */
        fun fullUpdate(context: Context) {
            val (manager, ids) = widgetIds(context)
            if (ids.isNotEmpty()) {
                TaskWidgetProvider().onUpdate(context, manager, ids)
            }
        }

        /** Layout-only update (background color) without rebinding adapter. */
        fun updateLayout(context: Context) {
            val (manager, ids) = widgetIds(context)
            if (ids.isEmpty()) return

            val bgColor = resolveBackgroundColor(context)
            for (widgetId in ids) {
                val views = RemoteViews(context.packageName, R.layout.widget_task_list)
                views.setInt(R.id.widget_background, "setBackgroundColor", bgColor)
                manager.partiallyUpdateAppWidget(widgetId, views)
            }
        }

        /** Resolves the widget background color from theme + transparency setting. */
        fun resolveBackgroundColor(context: Context): Int {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val transparency = prefs.getInt(PREF_TRANSPARENCY, DEFAULT_TRANSPARENCY)
            return buildColor(context, transparency)
        }

        /** Builds an ARGB color from theme base color and transparency percentage. */
        fun buildColor(context: Context, transparencyPercent: Int): Int {
            val alpha = (100 - transparencyPercent) * 255 / 100
            val baseColor = if (isDarkMode(context)) DARK_BASE else LIGHT_BASE
            return (alpha shl 24) or baseColor
        }

        /** Section header color: contrasting background with half the user's transparency. */
        fun sectionColor(context: Context): Int {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val transparency = prefs.getInt(PREF_TRANSPARENCY, DEFAULT_TRANSPARENCY)
            val alpha = (100 - transparency / 2) * 255 / 100
            val baseColor = if (isDarkMode(context)) DARK_SECTION_BASE else LIGHT_SECTION_BASE
            return (alpha shl 24) or baseColor
        }

        fun isDarkMode(context: Context): Boolean =
            (context.resources.configuration.uiMode
                and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        fun textColor(context: Context): Int =
            if (isDarkMode(context)) DARK_TEXT else LIGHT_TEXT
    }

    override fun onUpdate(
        context: Context, manager: AppWidgetManager, widgetIds: IntArray
    ) {
        Log.d(TAG, "onUpdate: ${widgetIds.size} widgets")
        val tokenStore = TokenStore(context)
        val bgColor = resolveBackgroundColor(context)
        val textColor = textColor(context)

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

            val serviceIntent = Intent(context, TaskWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            // TODO migrate to RemoteCollectionItems when minSdk >= 31 (API 35 replacement)
            @Suppress("DEPRECATION")
            views.setRemoteAdapter(R.id.task_list, serviceIntent)
            views.setScrollPosition(R.id.task_list, 0)
            views.setEmptyView(R.id.task_list, R.id.empty_view)

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

        // TODO migrate to RemoteCollectionItems when minSdk >= 31 (API 35 replacement)
        @Suppress("DEPRECATION")
        manager.notifyAppWidgetViewDataChanged(widgetIds, R.id.task_list)
        RefreshWorker.schedule(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        when (intent.getStringExtra("action")) {
            "complete" -> {
                val taskId = intent.getLongExtra("task_id", -1)
                if (taskId != -1L) {
                    val appContext = context.applicationContext
                    Thread {
                        val success = try {
                            ToodledoApi(TokenStore(appContext)).completeTask(taskId)
                        } catch (e: Exception) {
                            Log.e(TAG, "completeTask failed", e)
                            false
                        }
                        if (success) {
                            refresh(appContext)
                        } else {
                            Handler(Looper.getMainLooper()).post {
                                Toast.makeText(appContext,
                                    R.string.error_offline, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }.start()
                }
            }
            "open" -> openToodledo(context)
            "refresh" -> {
                Log.d(TAG, "Refresh from list item")
                fullUpdate(context)
            }
        }

        if (intent.action == ACTION_REFRESH) {
            Log.d(TAG, "ACTION_REFRESH received")
            refresh(context)
        }
    }

}
