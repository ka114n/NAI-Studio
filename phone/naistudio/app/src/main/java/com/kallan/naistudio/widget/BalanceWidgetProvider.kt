package com.kallan.naistudio.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.kallan.naistudio.MainActivity
import com.kallan.naistudio.R
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth

/**
 * `‹` / `›` 切月份那条广播的 action（自己发、自己收 —— 小组件里没有别的通信渠道）。
 *
 * ⚠️ 必须是**顶层常量**（不能塞进 companion）：`monthStepIntent` 是**顶层扩展以外的
 *    普通私有函数**，让它和接收器都能直接看到这个常量。
 */
internal const val ACTION_MONTH_STEP = "com.kallan.naistudio.widget.MONTH_STEP"

/**
 * **桌面小组件**（用户 2026-09-26）。
 *
 * ## 两个样式 = **两个独立的 Provider**，不是一个按尺寸变形的
 *
 * 用户原话：「**应该直接做两个样式，一个余额，一个统计加余额**」。
 *
 * ⚠️ 早前那一版是**一个** Provider、靠读到的尺寸 (>100dp) 决定用哪套布局 —— 那是**错的**：
 *    · 小组件选择器里**只会出现一条**，用户挑不了"我要哪个"；
 *    · 到底显示成哪样，取决于启动器报上来的那串 dp，**不由用户决定**（拉大拉小就变脸）；
 *    · 想只要余额的人，得"把它拉小"才会变成余额 —— 完全不是"选样式"的意思。
 *
 * 现在拆成两个类、各自一份元数据 XML、各自在 Manifest 里注册一个 `<receiver>`：
 *    · [BalanceOnlyWidgetProvider] —— **1×2，只有余额**；
 *    · [BalanceStatsWidgetProvider] —— **2×3，余额 + 统计**。
 *    选择器里就是两条，各挑各的，**互不影响**、也不会因为拉伸而变形。
 *
 * ## 小组件跑在别的进程里 —— 这条决定了全部写法
 *
 * 它在 **`com.android.systemui` / 启动器**进程，**不是本 App 进程** ⇒
 *  · 读不到 `AppState` 里任何内存态（`account`、`usage` 全是内存的）；
 *  · 只能用 `RemoteViews`（不是 Compose）；
 *  · 能拿到的只有**落盘的 SharedPreferences**（见 [WidgetBridge]）。
 *
 * ⚠️ **不声明自动刷新**（`updatePeriodMillis="0"`）：那个最短 30 分钟、系统还随时忽略。
 *    刷新时机 = App 里余额 / 统计一变就推一次（见 `AppState.pushWidgetUpdate`）。
 *    代价是"不开 App 数字就不变"，所以带统计那档**把快照时间也显示出来**。
 */

/** **1×2：只有余额**（用户点名的那一档）。 */
class BalanceOnlyWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val data = WidgetBridge.read(context)
        appWidgetIds.forEach { id ->
            val views = runCatching {
                RemoteViews(context.packageName, R.layout.widget_balance_mini).apply {
                    setTextViewText(R.id.widget_balance_value, data.balanceText)
                    applyBalanceBar(context, this, data)
                    setOnClickPendingIntent(R.id.widget_root, widgetLaunchIntent(context, requestCode = 1))
                }
            }.onFailure {
                android.util.Log.e("BalanceWidget", "余额小组件布局构建失败", it)
            }.getOrNull() ?: return@forEach

            appWidgetManager.updateAppWidget(id, views)
        }
    }

    /**
     * 1×2 那几条"有就显示、没有就不画"的内容（用户 2026-09-26 参考图）：
     * **档位徽章 / V5 剩余百分比 / 右边那颗 `V`**。
     *
     * ## 为什么是 `setViewVisibility` 而不是在布局里写死
     *
     * 这三个数都是**快照**，可能压根没有（没配 token、没刷新成功、或不是 Opus+V5 档）。
     * 布局里给了占位，这里按"有没有值"开/关：
     *  · 档名空 ⇒ **徽章整颗不画**（画一颗写着 `—` 的绿药丸比不画更糟）；
     *  · 百分比没有 ⇒ 分段字、百分比、那颗 `V` **一起不画**（口径同 App 顶栏：
     *    字和条同生同灭，见 `AppSettings.v5PercentSnapshot`）。
     *
     * ⚠️ `RemoteViews.setViewVisibility` 只影响**这一次推过去**的那一份快照，
     *    不会残留在宿主那边 —— 所以两个分支都必须显式给值（这是"设"不是"改"）。
     */
    private fun applyBalanceBar(context: Context, views: RemoteViews, data: WidgetData) {
        val tier = data.tierText
        views.setViewVisibility(R.id.widget_tier, if (tier.isBlank()) View.GONE else View.VISIBLE)
        if (tier.isNotBlank()) views.setTextViewText(R.id.widget_tier, tier)

        val shown = data.v5PercentText
        val percentVisibility = if (shown.isBlank()) View.GONE else View.VISIBLE
        views.setViewVisibility(R.id.widget_v5_caption, percentVisibility)
        views.setViewVisibility(R.id.widget_v5_percent, percentVisibility)
        views.setViewVisibility(R.id.widget_v5_badge, percentVisibility)
        if (shown.isNotBlank()) {
            views.setTextViewText(
                R.id.widget_v5_percent,
                context.getString(R.string.widget_v5_percent_format, shown),
            )
        }
    }
}

/** **2×3：余额 + 统计**（今日 / 本月张数、消耗点数、快照时间）。 */
class BalanceStatsWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val data = WidgetBridge.read(context)
        appWidgetIds.forEach { id ->
            val views = runCatching {
                RemoteViews(context.packageName, R.layout.widget_balance_full).apply {
                    setTextViewText(R.id.widget_balance_value, data.balanceText)
                    // 快照时间：让"这是几点的数"一眼可见（小组件不联网，见类注释）
                    setTextViewText(R.id.widget_stamp, data.stampText)
                    // 月份标题 + 星期表头（日历式排版）
                    setTextViewText(R.id.widget_month_label, data.monthLabel)
                    buildWeekHeader(context, R.id.widget_week_header, this)
                    // **日历式热点图**（按星期排列 + 方格）
                    buildHeatmap(context, data, R.id.widget_heatmap, this)
                    // ‹ › 切月份（RemoteViews 不支持滑动，只能点 —— 见 WidgetMonthReceiver）
                    setOnClickPendingIntent(R.id.widget_month_prev, monthStepIntent(context, -1))
                    setOnClickPendingIntent(R.id.widget_month_next, monthStepIntent(context, +1))
                    setOnClickPendingIntent(R.id.widget_root, widgetLaunchIntent(context, requestCode = 2))
                }
            }.onFailure {
                android.util.Log.e("BalanceWidget", "统计小组件布局构建失败（检查是否用了白名单外的控件）", it)
            }.getOrNull() ?: return@forEach

            appWidgetManager.updateAppWidget(id, views)
        }
    }

    /**
     * 星期表头（一 二 三 四 五 六 日）—— 每格一个 TextView，7 列。
     *
     * ⚠️⚠️ **必须先 `removeAllViews`**（用户 2026-09-26 报的 bug：
     *    「切换月份时会**多添加一行星期**」）。
     *
     * 原因：`RemoteViews.addView` 是**追加**语义，而这个 `containerId` 指向的容器
     * 在**启动器那边是同一个实例** —— 每次 `updateAppWidget` 推过去的是一份新
     * `RemoteViews`，但宿主把它**应用到已有视图树**上时，`addView` 只是往里加，
     * 不会替换掉上一轮加的那 7 个 ⇒ 切一次月份就多一行星期。
     *
     * 同理 [buildHeatmap] 也先清空（否则日历格子也会一直累加）。
     */
    private fun buildWeekHeader(context: Context, containerId: Int, views: RemoteViews) {
        views.removeAllViews(containerId)
        context.getString(R.string.widget_week_header).split(' ').forEach { label ->
            val cell = RemoteViews(context.packageName, R.layout.widget_week_label)
            cell.setTextViewText(R.id.week_label, label)
            views.addView(containerId, cell)
        }
    }

    /**
     * `‹` / `›` 的点击意图 —— 发给 [WidgetMonthReceiver]（**不是**打开 App）。
     *
     * ⚠️ requestCode 用 `step` 区分（-1 / +1）：两个箭头若用同一个 requestCode，
     *    系统会把它们当成同一个 PendingIntent，点哪个都是同一个 step。
     */
    private fun monthStepIntent(context: Context, step: Int): PendingIntent {
        val intent = Intent(context, WidgetMonthReceiver::class.java).apply {
            action = ACTION_MONTH_STEP
            putExtra(WidgetMonthReceiver.EXTRA_STEP, step)
        }
        return PendingIntent.getBroadcast(
            context,
            if (step < 0) 101 else 102,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * 尺寸变了（用户拉伸小组件）⇒ 重画。
     *
     * ⚠️ 用户 2026-09-26：「**然后变为可调样式**」—— 元数据里 `resizeMode` 已经放开拉伸，
     *    但**光放开不够**：`RemoteViews` 是"推过去就不管了"的一份快照，
     *    用户拉大之后不会自己重排。必须在这个回调里**重新塞一遍内容**
     *    （热点图尤其明显：拉宽了格子还是原来那 7 列的宽度，右边空一大块）。
     */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle,
    ) {
        onUpdate(context, appWidgetManager, intArrayOf(appWidgetId))
    }

    /**
     * **当月热点图**（7 列 × 最多 6 行，越绿 = 那天出图越多）。
     *
     * 口径**和电脑线那张完全一致**（`UsageStatsScreen` 的 `DayGrid` / `levelColor`）：
     *  · 从 1 号排到月末、**按 7 列折行**（不按星期对齐）；
     *  · 等级只由**张数**决定：0 / 1 / 2-3 / 4-7 / ≥8 ⇒ 五档；
     *  · 五档绿**写死、不跟主题走**（它是"数据"不是"界面"）。
     *
     * ## 为什么在代码里拼、而不是写在布局 XML 里
     *
     * 格子数**随月份变**（28~31 个）⇒ 布局 XML 里没法预置。
     * `RemoteViews` 的做法是：布局里放一个**空的 `GridLayout`**，
     * 再用 [RemoteViews.addView] **一个个把格子塞进去**。
     * ⚠️ 每个格子本身也得是 `RemoteViews`（不能是普通 `View`）——
     *    所以下面每个格子都是"inflate 一个 1 格的小布局再 setInt 背景色"，
     *    **不是** `new View(context)`（那样一放进去就整块空白）。
     *
     * ⚠️ 每次 `updateAppWidget` 都是**全新一份 RemoteViews**（`removeAllViews` 之后重加），
     *    所以**不会**出现"上个月的格子还留着"。
     */
    /**
     * **日历式热点图**（用户 2026-09-26：「像日历一样**按星期排列**，格子用**方格**」）。
     *
     * ## 和上一版的差别
     *
     *  · 上一版是"1 号 → 月末直接 7 列折行"，**不按星期** —— 一列没有"周几"的含义；
     *  · 现在**按星期对齐**：左上角是**周一**，1 号落在它实际的那个星期几上，
     *    前面补空格子（不画）—— 这才是"日历"。
     *
     * ## 为什么在代码里拼、而不是写在布局 XML 里
     *
     * 格子数随月份变（28~31 个）+ 前面还要补 0~6 个空格 ⇒ 布局 XML 里没法预置。
     * 做法：布局里放一个**空的 `GridLayout`**，再用 [RemoteViews.addView] 一个个塞进去。
     * ⚠️ 每个格子本身也得是 `RemoteViews`（不能是普通 View）——
     *    所以每个格子都是"inflate 一格小布局再 setInt 背景色"。
     *
     * ⚠️ **每次都是全新一份 RemoteViews**（父布局是新 inflate 的），
     *    所以**不会**出现"上个月的格子还留着"。
     */
    private fun buildHeatmap(context: Context, data: WidgetData, containerId: Int, views: RemoteViews) {
        // ⚠️ **先清空**：`addView` 是追加语义，不清的话切月份时新旧格子会叠在一起
        //    （用户报的「多添加一行星期」就是同一类问题，见 `buildWeekHeader`）。
        views.removeAllViews(containerId)

        // 前面补的空格子：1 号是周 N ⇒ 前面有 (N-1) 个位置是上个月的
        // ⚠️ 用**透明**格子占位（不是不加）—— 不加的话 1 号会被挤到左上角，日历就错位了。
        //    占位格**不写号数**（那不是这个月的日子）。
        repeat(data.monthFirstWeekday - 1) {
            val blank = RemoteViews(context.packageName, R.layout.widget_heatmap_cell)
            blank.setInt(R.id.heat_cell, "setBackgroundColor", android.graphics.Color.TRANSPARENT)
            blank.setTextViewText(R.id.heat_cell, "")
            views.addView(containerId, blank)
        }

        // 本月每一天：**方格 + 号数**（底色 = 那天出图张数对应的档位）
        // ⚠️ 这是 1.1.116 的口径（用户 2026-09-26 选定回退到这一版）：
        //    格子宽度用 `columnWeight` 铺满整行，高度固定 —— 见 cell 布局的说明。
        data.monthLevels.forEachIndexed { index, level ->
            val cell = RemoteViews(context.packageName, R.layout.widget_heatmap_cell)
            cell.setInt(R.id.heat_cell, "setBackgroundColor", heatColor(context, level))
            cell.setTextViewText(R.id.heat_cell, (index + 1).toString())
            cell.setTextColor(
                R.id.heat_cell,
                if (level >= 3) {
                    android.graphics.Color.WHITE
                } else {
                    // 浅底（0~2 档）用深字 —— 那几档底色很亮，白字会看不见
                    android.graphics.Color.parseColor("#14301C")
                },
            )
            views.addView(containerId, cell)
        }
    }

    /**
     * 五档绿 —— **和电脑线逐字一致**（`UsageStatsScreen.levelColor`）。
     *
     * ⚠️ 用 `Context.getColor(R.color…)`（资源里那一份），**不是**在代码里写死 int：
     *    两份颜色只留一处定义（`values/colors_widget.xml`），改了不会漏。
     */
    private fun heatColor(context: Context, level: Int): Int = context.getColor(
        when (level) {
            0 -> R.color.heat_0
            1 -> R.color.heat_1
            2 -> R.color.heat_2
            3 -> R.color.heat_3
            else -> R.color.heat_4
        },
    )
}

/**
 * 点小组件 = 打开 App（不做"后台静默刷新"那种点了没反馈的事）。
 *
 * ⚠️ `FLAG_IMMUTABLE` 是 Android 12+ 的**硬要求**（可变 PendingIntent 会直接抛）。
 * ⚠️ `requestCode` 两个 Provider **各用一个**（1 / 2）：同一 App 里 requestCode 相同、
 *    Intent 又等价时，系统会把两个 PendingIntent 当成同一个 —— 两个小组件就会共用一份，
 *    将来要给它们绑不同动作时会互相踩。
 */
internal fun widgetLaunchIntent(context: Context, requestCode: Int = 1): PendingIntent {
    val intent = Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_MAIN
        addCategory(Intent.CATEGORY_LAUNCHER)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    return PendingIntent.getActivity(
        context,
        requestCode,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

/**
 * **切月份**（用户 2026-09-26：「可滑动切换月份」）。
 *
 * ## 为什么是点箭头、不是真的"滑动"
 *
 * Android 的小组件走 `RemoteViews`，**只支持点击**（`setOnClickPendingIntent`），
 * **不接收滑动/拖拽手势**（手势归启动器管）—— 平台硬限制，Google 自家的日历小组件
 * 也是点箭头切月。所以这里做成布局里那对 `‹ ›`。
 *
 * ## 月份状态存哪
 *
 * **存 SharedPreferences**（`nai_widget` / `offset`），不存内存：
 * 小组件跑在启动器进程，广播进来的是**另一个进程的新实例**，
 * 内存里那个字段根本读不到。偏移量相对**当前月**（0 = 本月，-1 = 上个月）。
 *
 * ⚠️ 只允许往**过去**翻（`offset <= 0`）：未来的月份一定没有记录，
 *    翻过去是一片空格子，没有意义（App 里的统计页也是这么挡的）。
 */
class WidgetMonthReceiver : android.content.BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val step = intent.getIntExtra(EXTRA_STEP, 0)
        val prefs = context.getSharedPreferences(PREFS_WIDGET, Context.MODE_PRIVATE)
        val current = prefs.getInt(KEY_MONTH_OFFSET, 0)
        // 往未来翻挡在 0（本月）；往过去不设下限（想看多久看多久，没记录就是空日历）
        val next = (current + step).coerceAtMost(0)
        prefs.edit().putInt(KEY_MONTH_OFFSET, next).apply()

        // 存完就推一次刷新（两个 Provider 都推，各自读最新的 offset 重画）
        WidgetBridge.updateAll(context)
    }

    companion object {
        const val EXTRA_STEP = "step"
        /** 与 `WidgetBridge.PREFS_WIDGET` 一致（存储契约，不能改）。 */
        const val PREFS_WIDGET = "nai_widget"
        /** 相对当前月的偏移（0 = 本月，-1 = 上个月）。 */
        const val KEY_MONTH_OFFSET = "month_offset"
    }
}

/**
 * 小组件要显示的那几个数（**已格式化成可直接上屏的字符串**）。
 *
 * ⚠️ `data class` 是 **public**（不是 internal）：[WidgetBridge.read] 是 public 的
 *    （`AppState` 在共用树里要用它，跨源集看不到 internal —— 见 [WidgetBridge] 的说明），
 *    public 函数不能返回 internal 类型。
 */
data class WidgetData(
    /** 余额（`"—"` = 从来没拿到过快照；**不编 0**，0 会被读成"用光了"）。 */
    val balanceText: String,
    val todayImages: Int,
    val monthImages: Int,
    val monthAnlas: Int,
    /** 快照时间（`"11:54"`；没有快照就是空串，那一行就不显示内容）。 */
    val stampText: String,
    /**
     * **当月每一天的等级 0..4**（就是这个月热点图的格子，从 1 号排到月末）。
     *
     * ⚠️ 长度 = 当月天数（28~31），**和电脑线 `usageMonthCells` 同一个口径**：
     *    等级只看**那天的出图张数**（0 / 1 / 2-3 / 4-7 / ≥8 ⇒ 五档），
     *    点数与 tag 字数**不参与上色**（一个格子只能表达一个量）。
     */
    val monthLevels: List<Int>,
    /**
     * **这个月 1 号是星期几**（1=周一 … 7=周日）—— 日历排版要在前面补几个空格子。
     *
     * 用户 2026-09-26：「统计像日历一样**按星期排列**」⇒ 要按"周一在最左"对齐，
     * 1 号之前那几天留空（不画格子）。
     */
    val monthFirstWeekday: Int = 1,
    /** 正在看的是哪个月（`"2026-09"` 或 `"2026 年 9 月"`），显示在标题上。 */
    val monthLabel: String = "",
    /**
     * **会员档名**（`"Paper"` / `"Opus"` / …）—— 1×2 那颗绿色小徽章上写的就是它。
     *
     * ⚠️ 空串 = **从来没拿到过快照**（没配 token / 没刷新成功）⇒ 徽章整颗不画，
     *    **不编一个档名出来**（编错了比不画更误导：档位决定了能用哪些模型）。
     */
    val tierText: String = "",
    /**
     * **V5 剩余百分比**（`"54.0"`，**保留一位小数字符串**，和参考图那个 `54.0%` 一致）。
     *
     * ⚠️ 空串 = 不适用（不是 Opus 档 / 快照时没在用 V5 模型）⇒ 分段字、百分比、
     *    右下角那颗 `V` **一起不画** —— 口径与 App 顶栏胶囊逐字一致
     *    （见 `AppState.snapshotBalance` 与 `AppSettings.v5PercentSnapshot`）。
     */
    val v5PercentText: String = "",
)

/**
 * 小组件与 App 之间唯一的那条路（**读写 SharedPreferences**）。
 *
 * ⚠️ 这里**刻意不碰 `Storage` / `AppSettings` 那份共用代码**：
 *    那套要在同进程里跑、还带 Keystore 解密的初始化。小组件只需要**两三个字段**，
 *    直接按同一个键名把 JSON 拆开读，少一层依赖、也不会因为共用树里某个初始化没跑起来而炸。
 *    **键名（`nai_store` / `app_settings`）是存储契约**，和 `AndroidPlatform.PREFS_STORE`
 *    与 `Storage._K_SETTINGS` 严格一致 —— 改那两个时必须同步改这里。
 *
 * ⚠️ `object` 是 **public**（不是 internal）：`AppState` 在共用树 `naistudio-shared` 里，
 *    虽然和这个类一起编进同一个 app 模块，但**跨源集**看不到 internal 的可见性，
 *    它会 "Unresolved reference"。所以 `AppState` 那边是**反射**调用的（见 `pushWidgetUpdate`）。
 */
object WidgetBridge {

    /** 与 `AndroidPlatform.PREFS_STORE` 一致（存储契约，不能改）。 */
    private const val PREFS_STORE = "nai_store"

    /** 与 `Storage._K_SETTINGS` 一致（存储契约，不能改）。 */
    private const val KEY_SETTINGS = "app_settings"

    /**
     * App 侧调它来推刷新（见 `AppState.pushWidgetUpdate`）。
     *
     * ⚠️ **两个 Provider 都要推**（用户 2026-09-26：「应该直接做两个样式」）——
     *    只推其中一个的话，另一个永远停在添加时那一刻的数字。
     */
    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        refresh(context, manager, BalanceOnlyWidgetProvider::class.java)
        refresh(context, manager, BalanceStatsWidgetProvider::class.java)
    }

    /** 推某一个 Provider 的所有实例（没有实例就什么都不做 —— 别白跑一遍读 JSON）。 */
    private fun refresh(
        context: Context,
        manager: AppWidgetManager,
        provider: Class<out AppWidgetProvider>,
    ) {
        val ids = manager.getAppWidgetIds(ComponentName(context, provider))
        if (ids.isEmpty()) return
        val instance = provider.getDeclaredConstructor().newInstance()
        instance.onUpdate(context, manager, ids)
    }

    /** 读一次要显示的四个数（任何一项读不到都回落到"安全值"，**绝不抛**）。 */
    fun read(context: Context): WidgetData {
        val json = runCatching {
            context.getSharedPreferences(PREFS_STORE, Context.MODE_PRIVATE)
                .getString(KEY_SETTINGS, null)
                ?.let { JSONObject(it) }
        }.getOrNull()

        // ---- 余额（快照；没有就是"—"）----
        val balance = json?.let { root ->
            if (root.has("balanceSnapshot") && !root.isNull("balanceSnapshot")) {
                root.optInt("balanceSnapshot")
            } else {
                null
            }
        }
        val stampAt = json?.optLong("balanceSnapshotAt", 0L) ?: 0L

        // ---- 档名 / V5 剩余百分比（1×2 那条胶囊上的两样，用户 2026-09-26 参考图）----
        val tierSnapshot = json?.optString("tierSnapshot", "").orEmpty()
        val v5Percent = json?.let { root ->
            if (root.has("v5PercentSnapshot") && !root.isNull("v5PercentSnapshot")) {
                root.optInt("v5PercentSnapshot")
            } else {
                null
            }
        }

        // ---- 统计（今日 / 本月）----
        // ⚠️ 格式与 `UsageStats.encode` 严格一致：`"2026-09-23:12,345,678;…"`
        //    （日期:张数,点数,tag字数）。解析失败就当没有统计（全 0），**不抛**。
        val usage = json?.optString("usageStats", "").orEmpty()

        // 看的是哪个月：0 = 本月，-1 = 上个月…（由 ‹ › 存在 `nai_widget` 里，见 WidgetMonthReceiver）
        val monthOffset = runCatching {
            context.getSharedPreferences(WidgetMonthReceiver.PREFS_WIDGET, Context.MODE_PRIVATE)
                .getInt(WidgetMonthReceiver.KEY_MONTH_OFFSET, 0)
        }.getOrDefault(0)

        val todayKey = LocalDate.now().toString()
        val thisMonth = YearMonth.now().plusMonths(monthOffset.toLong())
        val monthPrefix = thisMonth.toString() // "2026-09"

        // 先把这一整月**按天**读出来（热点图要逐天的张数，不只是汇总）
        val imagesByDate = HashMap<String, Int>()
        var monthImages = 0
        var monthAnlas = 0
        usage.split(';').forEach { entry ->
            val piece = entry.trim()
            if (piece.isEmpty()) return@forEach
            val colon = piece.indexOf(':')
            if (colon <= 0) return@forEach
            val date = piece.substring(0, colon)
            val nums = piece.substring(colon + 1).split(',')
            if (nums.size < 3) return@forEach
            val images = nums[0].trim().toIntOrNull() ?: return@forEach
            val anlas = nums[1].trim().toIntOrNull() ?: return@forEach
            imagesByDate[date] = (imagesByDate[date] ?: 0) + images
            if (date.startsWith(monthPrefix)) {
                monthImages += images
                monthAnlas += anlas
            }
        }
        val todayImages = imagesByDate[todayKey] ?: 0

        // 热点图：1 号 → 月末，一天一格（**和电脑线 `usageMonthCells` 同一个口径**）
        val monthLevels = (1..thisMonth.lengthOfMonth()).map { day ->
            heatLevel(imagesByDate[thisMonth.atDay(day).toString()] ?: 0)
        }

        return WidgetData(
            balanceText = balance?.toString() ?: "—",
            todayImages = todayImages,
            monthImages = monthImages,
            monthAnlas = monthAnlas,
            stampText = if (stampAt > 0L) formatTime(stampAt) else "",
            monthLevels = monthLevels,
            monthFirstWeekday = thisMonth.atDay(1).dayOfWeek.value,
            monthLabel = "%d-%02d".format(thisMonth.year, thisMonth.monthValue),
            tierText = tierSnapshot,
            // 保留一位小数（参考图上写的是 `54.0%` 而不是 `54%`）——
            // 快照存的是整数百分比，这里只负责**显示口径**。
            v5PercentText = v5Percent?.let { "%.1f".format(it.toDouble()) } ?: "",
        )
    }

    /**
     * 张数 → 热度等级 0..4 —— **和电脑线 `usageLevel` 逐字一致**：
     * 0 张 = 0 级、1 张 = 1 级、2–3 张 = 2 级、4–7 张 = 3 级、≥8 张 = 4 级。
     */
    private fun heatLevel(images: Int): Int = when {
        images <= 0 -> 0
        images == 1 -> 1
        images <= 3 -> 2
        images <= 7 -> 3
        else -> 4
    }

    /** 毫秒时间戳 → `"11:54"`（只到分钟：小组件上到秒没有意义）。 */
    private fun formatTime(millis: Long): String {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = millis }
        return "%02d:%02d".format(
            cal.get(java.util.Calendar.HOUR_OF_DAY),
            cal.get(java.util.Calendar.MINUTE),
        )
    }
}
