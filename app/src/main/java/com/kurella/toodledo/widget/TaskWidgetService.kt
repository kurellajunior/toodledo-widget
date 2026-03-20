package com.kurella.toodledo.widget

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.util.Log
import android.util.TypedValue
import android.widget.RemoteViews
import android.widget.RemoteViewsService

private const val TAG = "ToodledoWidget"

class TaskWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        TaskListFactory(applicationContext)
}

class TaskListFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {

    private var items: List<ListItem> = emptyList()
    private var fontScale: Float = 1.0f
    private var isDarkMode: Boolean = false

    override fun onCreate() {
        Log.d(TAG, "Factory.onCreate")
        // Don't load here — onCreate runs on the main thread (via onBind).
        // Data loading happens in onDataSetChanged on a binder thread.
    }

    override fun onDataSetChanged() {
        Log.d(TAG, "Factory.onDataSetChanged")
        loadTasks()
    }

    private fun loadTasks() {
        try {
            val prefs = context.getSharedPreferences("widget_settings", Context.MODE_PRIVATE)
            fontScale = prefs.getInt("font_size", 100) / 100f
            isDarkMode = (context.resources.configuration.uiMode
                and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

            val tokenStore = TokenStore(context)
            if (!tokenStore.isLoggedIn) {
                Log.d(TAG, "loadTasks: not logged in")
                items = emptyList()
                return
            }
            val api = ToodledoApi(tokenStore)
            val tasks = api.fetchTasks()
            if (tasks == null) {
                Log.w(TAG, "loadTasks: fetchTasks returned null (token or network error)")
                // Keep existing items on failure so widget doesn't go blank
                return
            }
            items = TaskSorter.buildList(tasks, context) + ListItem.RefreshItem
            Log.d(TAG, "loadTasks: ${tasks.size} tasks → ${items.size} items")
        } catch (e: Exception) {
            Log.e(TAG, "loadTasks failed", e)
        }
    }

    override fun getCount(): Int = items.size

    override fun getViewAt(position: Int): RemoteViews {
        if (position >= items.size) return RemoteViews(context.packageName, R.layout.widget_task_row)
        return when (val item = items[position]) {
            is ListItem.SectionHeader -> buildSectionView(item)
            is ListItem.TaskItem -> buildTaskView(item.task)
            is ListItem.RefreshItem -> buildRefreshView()
        }
    }

    private fun buildSectionView(header: ListItem.SectionHeader): RemoteViews {
        return RemoteViews(context.packageName, R.layout.widget_section_header).apply {
            setTextViewText(R.id.section_title, header.title)
            setTextViewTextSize(R.id.section_title, TypedValue.COMPLEX_UNIT_SP, 11f * fontScale)
        }
    }

    private fun buildTaskView(task: Task): RemoteViews {
        val today = java.time.LocalDate.now()
        val overdue = task.category(today) == Category.OVERDUE
        val overdueColor = 0x40F44336 // translucent red

        val textColor = if (isDarkMode) 0xFFE0E0E0.toInt() else 0xFF000000.toInt()

        val boxSizeDp = 16f * fontScale

        return RemoteViews(context.packageName, R.layout.widget_task_row).apply {
            setTextViewText(R.id.task_title, task.title)
            setTextViewTextSize(R.id.task_title, TypedValue.COMPLEX_UNIT_SP, 14f * fontScale)
            setTextViewTextSize(R.id.repeat_icon, TypedValue.COMPLEX_UNIT_SP, 18f * fontScale)
            setTextColor(R.id.task_title, textColor)
            setTextColor(R.id.repeat_icon, textColor)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setViewLayoutWidth(R.id.priority_frame, boxSizeDp, TypedValue.COMPLEX_UNIT_DIP)
                setViewLayoutHeight(R.id.priority_frame, boxSizeDp, TypedValue.COMPLEX_UNIT_DIP)
                val noteIconDp = 14f * fontScale
                setViewLayoutWidth(R.id.note_icon, noteIconDp, TypedValue.COMPLEX_UNIT_DIP)
                setViewLayoutHeight(R.id.note_icon, noteIconDp, TypedValue.COMPLEX_UNIT_DIP)
            }
            setInt(R.id.priority_box, "setBackgroundColor", task.priority.color)

            if (overdue) {
                setInt(R.id.task_row, "setBackgroundColor", overdueColor)
            } else {
                setInt(R.id.task_row, "setBackgroundColor", 0x00000000)
            }

            if (task.hasNote) {
                setViewVisibility(R.id.note_icon, android.view.View.VISIBLE)
                setInt(R.id.note_icon, "setColorFilter", textColor)
            } else {
                setViewVisibility(R.id.note_icon, android.view.View.GONE)
            }

            if (task.isRepeating) {
                setViewVisibility(R.id.repeat_icon, android.view.View.VISIBLE)
            } else {
                setViewVisibility(R.id.repeat_icon, android.view.View.GONE)
            }

            // Fill intent: tap on checkbox → complete task
            val completeIntent = Intent().apply {
                putExtra("task_id", task.id)
                putExtra("action", "complete")
            }
            setOnClickFillInIntent(R.id.priority_frame, completeIntent)

            // Fill intent: tap on title → open in Toodledo
            val openIntent = Intent().apply {
                putExtra("task_id", task.id)
                putExtra("action", "open")
            }
            setOnClickFillInIntent(R.id.task_title, openIntent)
        }
    }

    private fun buildRefreshView(): RemoteViews {
        return RemoteViews(context.packageName, R.layout.widget_section_header).apply {
            setTextViewText(R.id.section_title, context.getString(R.string.refresh))
            setTextViewTextSize(R.id.section_title, TypedValue.COMPLEX_UNIT_SP, 11f * fontScale)
            setInt(R.id.section_title, "setGravity", android.view.Gravity.CENTER)
            val fillIntent = Intent().apply { putExtra("action", "refresh") }
            setOnClickFillInIntent(R.id.section_title, fillIntent)
        }
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 2 // task row + section header (refresh reuses section header layout)
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = false
    override fun onDestroy() {}
}
