package io.github.kurella.toodledo.widget

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import io.github.kurella.toodledo.widget.R
import java.time.LocalDate

private const val TAG = "ToodledoWidget"

private const val OVERDUE_BACKGROUND = 0x40F44336
private const val TRANSPARENT = 0x00000000

private const val BASE_TEXT_SP = 14f
private const val BASE_SECTION_SP = 11f
private const val BASE_BOX_DP = 16f
private const val BASE_ICON_DP = 14f
private const val SECTION_TEXT_COLOR = 0xFFFFFFFF.toInt()

class TaskWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        TaskListFactory(applicationContext)
}

class TaskListFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {

    private var items: List<ListItem> = emptyList()
    private var fontScale: Float = 1.0f
    private var textColor: Int = LIGHT_TEXT
    private var sectionBackground: Int = (0xFF shl 24) or LIGHT_SECTION_BASE
    private val sectionTextColor: Int = SECTION_TEXT_COLOR

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
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        fontScale = prefs.getInt(PREF_FONT_SIZE, DEFAULT_FONT_SIZE) / 100f
        textColor = TaskWidgetProvider.textColor(context)
        sectionBackground = TaskWidgetProvider.sectionColor(context)

        val tokenStore = TokenStore(context)
        if (!tokenStore.isLoggedIn) {
            Log.d(TAG, "loadTasks: not logged in")
            items = emptyList()
            writeStatus(prefs, WidgetStatus.INITIAL)
            TaskWidgetProvider.updateStatusLine(context)
            return
        }

        val api = ToodledoApi(tokenStore)
        when (val result = api.fetchTasks()) {
            is FetchResult.Success -> {
                val taskItems = TaskSorter.buildList(result.tasks, context)
                items = if (taskItems.isEmpty()) {
                    listOf(ListItem.EmptyItem, ListItem.RefreshItem)
                } else {
                    taskItems + ListItem.RefreshItem
                }
                writeStatus(prefs, WidgetStatus.LOADED)
                Log.d(TAG, "loadTasks: ${result.tasks.size} tasks → ${items.size} items")
            }
            is FetchResult.AuthError -> {
                writeStatus(prefs, WidgetStatus.LOGGED_OUT)
                Log.w(TAG, "loadTasks: auth error")
            }
            is FetchResult.NetworkError -> {
                writeStatus(prefs, WidgetStatus.OFFLINE)
                Log.w(TAG, "loadTasks: network error")
            }
            is FetchResult.ApiError -> {
                writeStatus(prefs, WidgetStatus.API_ERROR)
                Log.w(TAG, "loadTasks: API error")
            }
        }
        TaskWidgetProvider.updateStatusLine(context)
    }

    private fun writeStatus(prefs: android.content.SharedPreferences, status: WidgetStatus) {
        prefs.edit().putString(PREF_WIDGET_STATUS, status.name).apply()
    }

    override fun getCount(): Int = items.size

    override fun getViewAt(position: Int): RemoteViews {
        if (position >= items.size) return RemoteViews(context.packageName, R.layout.widget_task_row)
        return when (val item = items[position]) {
            is ListItem.SectionHeader -> buildSectionView(item)
            is ListItem.TaskItem -> buildTaskView(item.task)
            is ListItem.RefreshItem -> buildRefreshView()
            is ListItem.EmptyItem -> buildEmptyView()
        }
    }

    private fun buildSectionView(header: ListItem.SectionHeader): RemoteViews {
        return RemoteViews(context.packageName, R.layout.widget_section_header).apply {
            setTextViewText(R.id.section_title, header.title)
            setTextViewTextSize(R.id.section_title, TypedValue.COMPLEX_UNIT_SP, BASE_SECTION_SP * fontScale)
            setInt(R.id.section_title, "setBackgroundColor", sectionBackground)
            setTextColor(R.id.section_title, sectionTextColor)
        }
    }

    private fun buildTaskView(task: Task): RemoteViews {
        val today = LocalDate.now()
        val overdue = task.category(today) == Category.OVERDUE

        return RemoteViews(context.packageName, R.layout.widget_task_row).apply {
            setTextViewText(R.id.task_title, task.title)
            setTextViewTextSize(R.id.task_title, TypedValue.COMPLEX_UNIT_SP, BASE_TEXT_SP * fontScale)
            setTextColor(R.id.task_title, textColor)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                scaleSquare(R.id.priority_frame, BASE_BOX_DP * fontScale)
                scaleSquare(R.id.note_icon, BASE_ICON_DP * fontScale)
                scaleSquare(R.id.repeat_icon, BASE_ICON_DP * fontScale)
            }

            setInt(R.id.priority_box, "setBackgroundColor", task.priority.color)
            setInt(R.id.task_row, "setBackgroundColor",
                if (overdue) OVERDUE_BACKGROUND else TRANSPARENT)

            setIndicator(R.id.note_icon, task.hasNote)
            setIndicator(R.id.repeat_icon, task.isRepeating)

            setOnClickFillInIntent(R.id.priority_frame, Intent().apply {
                putExtra("task_id", task.id)
                putExtra("action", "complete")
            })
            setOnClickFillInIntent(R.id.task_title, Intent().apply {
                putExtra("task_id", task.id)
                putExtra("action", "open")
            })
        }
    }

    /** Show/hide an indicator icon and tint it to match the theme text color. */
    private fun RemoteViews.setIndicator(viewId: Int, visible: Boolean) {
        setViewVisibility(viewId, if (visible) View.VISIBLE else View.GONE)
        if (visible) setInt(viewId, "setColorFilter", textColor)
    }

    /** Set width and height to the same dp value (requires API 31+). */
    @SuppressLint("NewApi")
    private fun RemoteViews.scaleSquare(viewId: Int, dp: Float) {
        setViewLayoutWidth(viewId, dp, TypedValue.COMPLEX_UNIT_DIP)
        setViewLayoutHeight(viewId, dp, TypedValue.COMPLEX_UNIT_DIP)
    }

    private fun buildRefreshView(): RemoteViews {
        return RemoteViews(context.packageName, R.layout.widget_section_header).apply {
            setTextViewText(R.id.section_title, context.getString(R.string.refresh))
            setTextViewTextSize(R.id.section_title, TypedValue.COMPLEX_UNIT_SP, BASE_SECTION_SP * fontScale)
            setInt(R.id.section_title, "setGravity", Gravity.CENTER)
            setInt(R.id.section_title, "setBackgroundColor", sectionBackground)
            setTextColor(R.id.section_title, sectionTextColor)
            setOnClickFillInIntent(R.id.section_title, Intent().apply {
                putExtra("action", "refresh")
            })
        }
    }

    private fun buildEmptyView(): RemoteViews {
        return RemoteViews(context.packageName, R.layout.widget_section_header).apply {
            setTextViewText(R.id.section_title, context.getString(R.string.no_tasks))
            setTextViewTextSize(R.id.section_title, TypedValue.COMPLEX_UNIT_SP, BASE_TEXT_SP * fontScale)
            setInt(R.id.section_title, "setGravity", Gravity.CENTER)
            setInt(R.id.section_title, "setBackgroundColor", TRANSPARENT)
            setTextColor(R.id.section_title, textColor)
        }
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 2  // widget_task_row + widget_section_header
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = false
    override fun onDestroy() {}
}
