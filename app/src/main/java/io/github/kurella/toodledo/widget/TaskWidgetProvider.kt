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
import android.widget.RemoteViews
import android.widget.Toast
import io.github.kurella.toodledo.widget.R

internal const val TAG = "ToodledoWidget"
const val TOODLEDO_PACKAGE = "com.toodledo"

const val PREFS_NAME = "widget_settings"
const val PREF_TRANSPARENCY = "transparency"
const val PREF_FONT_SIZE = "font_size"
internal const val PREF_PENDING_COMPLETE = "pending_complete"
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

        private fun buildViews(context: Context, widgetId: Int): RemoteViews {
            val bgColor = resolveBackgroundColor(context)

            return RemoteViews(context.packageName, R.layout.widget_task_list).apply {
                setInt(R.id.widget_background, "setBackgroundColor", bgColor)

                val serviceIntent = Intent(context, TaskWidgetService::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                    data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
                }
                @Suppress("DEPRECATION")  // TODO migrate to RemoteCollectionItems when minSdk >= 31
                setRemoteAdapter(R.id.task_list, serviceIntent)
                setScrollPosition(R.id.task_list, 0)

                val clickIntent = Intent(context, TaskWidgetProvider::class.java).apply {
                    action = "item_click"
                }
                val clickPending = PendingIntent.getBroadcast(
                    context, 0, clickIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
                setPendingIntentTemplate(R.id.task_list, clickPending)
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
        for (widgetId in widgetIds) {
            manager.updateAppWidget(widgetId, buildViews(context, widgetId))
        }

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
                    val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    // Show checkmark immediately
                    prefs.edit().putLong(PREF_PENDING_COMPLETE, taskId).apply()
                    refresh(appContext)
                    // API call in background
                    Thread {
                        val success = try {
                            ToodledoApi(TokenStore(appContext)).completeTask(taskId)
                        } catch (e: Exception) {
                            Log.e(TAG, "completeTask failed", e)
                            false
                        }
                        prefs.edit().remove(PREF_PENDING_COMPLETE).apply()
                        if (!success) {
                            Handler(Looper.getMainLooper()).post {
                                Toast.makeText(appContext,
                                    R.string.error_offline, Toast.LENGTH_SHORT).show()
                            }
                        }
                        refresh(appContext)
                    }.start()
                }
            }
            "open" -> openToodledo(context)
            "settings" -> context.startActivity(
                Intent(context, SettingsActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            )
            "refresh" -> {
                Log.d(TAG, "Refresh from list item")
                refresh(context)
            }
        }

        if (intent.action == ACTION_REFRESH) {
            Log.d(TAG, "ACTION_REFRESH received")
            refresh(context)
        }
    }

}
