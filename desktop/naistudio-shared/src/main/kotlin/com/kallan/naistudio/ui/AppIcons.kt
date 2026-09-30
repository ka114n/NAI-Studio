package com.kallan.naistudio.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 自绘图标。
 *
 * 为什么不用现成的：本项目刻意**不引 `material-icons-extended`**（运行时依赖只有 OkHttp），
 * 而 core 图标集里没有"图片/相册"这一类图标。这里用 `ImageVector` 手写一个，
 * 形状对着 Material 的 outlined `image` 图标（圆角矩形边框 + 太阳 + 山）。
 *
 * 用法：`Icon(PictureIcon, contentDescription = ...)` —— 颜色由 `Icon` 的 tint 统一决定，
 * 所以这里各路径的颜色随便给（黑色即可，会被 tint 覆盖）。
 */
val PictureIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Picture",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        // 外框：圆角矩形（描边）
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.7f,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(5.6f, 3.6f)
            horizontalLineTo(18.4f)
            curveTo(19.5f, 3.6f, 20.4f, 4.5f, 20.4f, 5.6f)
            verticalLineTo(18.4f)
            curveTo(20.4f, 19.5f, 19.5f, 20.4f, 18.4f, 20.4f)
            horizontalLineTo(5.6f)
            curveTo(4.5f, 20.4f, 3.6f, 19.5f, 3.6f, 18.4f)
            verticalLineTo(5.6f)
            curveTo(3.6f, 4.5f, 4.5f, 3.6f, 5.6f, 3.6f)
            close()
        }
        // 太阳（实心小圆）
        path(fill = SolidColor(Color.Black)) {
            moveTo(9f, 6.8f)
            arcToRelative(1.7f, 1.7f, 0f, true, true, 0f, 3.4f)
            arcToRelative(1.7f, 1.7f, 0f, true, true, 0f, -3.4f)
            close()
        }
        // 山（折线）
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(4.4f, 17.6f)
            lineTo(9.6f, 12.4f)
            lineTo(13.2f, 16f)
            lineTo(15.4f, 13.8f)
            lineTo(19.6f, 18f)
        }
    }.build()
}

/**
 * **文件夹**（侧边栏「图库」那颗 ✓，用户 2026-09-23：「把侧边栏的图库图标变成文件夹样式」✓）。
 *
 * 形状对着 Material 的 outlined `folder` ✓：后面一片"盖子"（折角矩形）+ 前面一片"盒身" ✓。
 * 和 [PictureIcon] 一样手写 `ImageVector` ✓（本项目刻意不引 `material-icons-extended` ✓）。
 */
val FolderIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Folder",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        // 盖子：左上角那小块折角
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(3.4f, 18.2f)
            verticalLineTo(6.2f)
            curveTo(3.4f, 5.1f, 4.3f, 4.2f, 5.4f, 4.2f)
            horizontalLineTo(9.2f)
            curveTo(9.8f, 4.2f, 10.3f, 4.5f, 10.7f, 4.9f)
            lineTo(12.1f, 6.6f)
            horizontalLineTo(18.6f)
            curveTo(19.7f, 6.6f, 20.6f, 7.5f, 20.6f, 8.6f)
        }
        // 盒身：下面那个圆角矩形（口径比盖子大一圈 ⇒ 看得出"文件夹"✓）
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.7f,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(3.4f, 18.2f)
            verticalLineTo(9.4f)
            curveTo(3.4f, 8.3f, 4.3f, 7.4f, 5.4f, 7.4f)
            horizontalLineTo(18.6f)
            curveTo(19.7f, 7.4f, 20.6f, 8.3f, 20.6f, 9.4f)
            verticalLineTo(19.8f)
            curveTo(20.6f, 20.9f, 19.7f, 21.4f, 18.6f, 21.4f)
            horizontalLineTo(5.4f)
            curveTo(4.3f, 21.4f, 3.4f, 20.5f, 3.4f, 19.4f)
            close()
        }
    }.build()
}

/**
 * **柱状图**（侧边栏「统计」那颗 ✓，用户 2026-09-23：
 * 「在侧边栏下方，暗色模式上方加一个柱状图图标"统计"」✓）。
 *
 * 三根柱子 + 一条基线 ✓（柱高不等，一眼就是"图表"而不是"音量"✓）。
 */
val BarChartIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "BarChart",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        // 基线
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round,
        ) {
            moveTo(3.6f, 20.2f)
            horizontalLineTo(20.4f)
        }
        // 三根柱子（圆头，高矮错开 ✓）
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2.4f,
            strokeLineCap = StrokeCap.Round,
        ) {
            moveTo(7.4f, 20.0f)
            verticalLineTo(14.2f)
        }
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2.4f,
            strokeLineCap = StrokeCap.Round,
        ) {
            moveTo(12f, 20.0f)
            verticalLineTo(8.6f)
        }
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2.4f,
            strokeLineCap = StrokeCap.Round,
        ) {
            moveTo(16.6f, 20.0f)
            verticalLineTo(11.4f)
        }
    }.build()
}

/**
 * 时钟（提示词历史）：表盘 + 时针分针。
 * 和 [PictureIcon] 一样是手写 `ImageVector`（不引 material-icons-extended）。
 */
val ClockIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Clock",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        // 表盘
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
        ) {
            moveTo(12f, 3.2f)
            arcToRelative(8.8f, 8.8f, 0f, true, true, 0f, 17.6f)
            arcToRelative(8.8f, 8.8f, 0f, true, true, 0f, -17.6f)
            close()
        }
        // 指针：12 → 中心 → 4 点方向
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(12f, 7.2f)
            verticalLineTo(12f)
            lineTo(15.4f, 14.2f)
        }
    }.build()
}

/**
 * 翻译图标**不再用矢量路径**：
 * 生成页那个翻译按钮现在直接渲染「A文」两个字（见 `GenerateScreen.TranslateGlyph`）——
 * 字形本身就是最准确的图标，而且原来那版"卡片 + 文 + 右下角 A"用户看着不像翻译。
 * 这里保留原来的说明位置，避免以后有人又照着 path 拼一个。
 */

/** 星星/闪光（LLM 优化提示词）。 */
val SparkleIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Sparkle",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        // 主四角星
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(11f, 3.6f)
            lineTo(12.9f, 9f)
            lineTo(18.3f, 10.9f)
            lineTo(12.9f, 12.8f)
            lineTo(11f, 18.2f)
            lineTo(9.1f, 12.8f)
            lineTo(3.7f, 10.9f)
            lineTo(9.1f, 9f)
            close()
        }
        // 右上小星
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(18.2f, 14.6f)
            lineTo(19f, 16.8f)
            lineTo(21.2f, 17.6f)
            lineTo(19f, 18.4f)
            lineTo(18.2f, 20.6f)
            lineTo(17.4f, 18.4f)
            lineTo(15.2f, 17.6f)
            lineTo(17.4f, 16.8f)
            close()
        }
    }.build()
}

/**
 * 把 SVG path 字符串转成 `ImageVector` 的路径节点。
 *
 * 这两个回退箭头用的是 Material 官方 `undo` / `redo` 的成型路径 —— 手写贝塞尔太容易画歪，
 * 直接用 `PathParser` 解析原文，保证形状和系统里其他地方的同名图标一致。
 */
private fun svgPath(d: String): List<PathNode> =
    PathParser().parsePathString(d).toNodes()

/**
 * **闪电**（用户 2026-09-24：「给双端加上**积分预测**功能，显示在**生成按钮**上，
 * 显示 **[数字] 闪电图标**」✓）。
 *
 * 形状 = Material `bolt`（那颗实心闪电 ✓，一眼就知道"这是电量 / 点数"✓）。
 * 手写 `ImageVector` ✓ —— 本项目刻意不引 `material-icons-extended` ✓（那一个包 37.7MB ✓）。
 */
val BoltIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Bolt",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = svgPath(
            "M11 21h-1l1-7H7.5c-.58 0-.57-.32-.38-.66.19-.34.05-.08.07-.12C8.48 10.94 " +
                "10.42 7.54 13 3h1l-1 7h3.5c.49 0 .56.33.47.51l-.07.15C12.96 17.55 11 21 11 21z",
        ),
        fill = SolidColor(Color.Black),
    ).build()
}

/**
 * **「回退」↶**：正面提示词的撤销按钮。形状 = Material `undo`。 */
val UndoIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Undo",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = svgPath(
            "M12.5 8c-2.65 0-5.05.99-6.9 2.6L2 7v9h9l-3.62-3.62c1.39-1.16 " +
                "3.16-1.88 5.12-1.88 3.54 0 6.55 2.31 7.6 5.5l2.37-.78C21.08 11.03 17.15 8 12.5 8z",
        ),
        fill = SolidColor(Color.Black),
    ).build()
}

/**
 * **侧栏开关**：圆角方框 + 靠左一条竖线（用户 2026-09-16 给的形状）。
 *
 * 官方 `view_sidebar` 三个变体都不完全对：filled / outlined 是直角、round 那个是实心块 + 三条短杠。
 * 用户给的图是**圆角描边框 + 内部一条竖线**，所以按 [ExpandCornersIcon] 那套**描边画法**手写
 *（纯几何图形，比抠一条会「少一段就糊成实心」的填色路径稳）。
 *
 * 用途：常驻侧栏**右上角**那颗开关（收起/展开）。
 */
val SidebarToggleIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "SidebarToggle",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            // 外框：圆角矩形（描边居中，所以半径也按中线算）
            moveTo(10f, 4f)
            horizontalLineTo(17.5f)
            arcToRelative(2.5f, 2.5f, 0f, false, true, 2.5f, 2.5f)
            verticalLineTo(17.5f)
            arcToRelative(2.5f, 2.5f, 0f, false, true, -2.5f, 2.5f)
            horizontalLineTo(6.5f)
            arcToRelative(2.5f, 2.5f, 0f, false, true, -2.5f, -2.5f)
            verticalLineTo(6.5f)
            arcToRelative(2.5f, 2.5f, 0f, false, true, 2.5f, -2.5f)
            horizontalLineTo(10f)
            // 内部竖线（靠左的那条分栏线）
            moveTo(9.5f, 4.2f)
            verticalLineTo(19.8f)
        }
    }.build()
}

/**
 * 「选择/框选」（方框虚线 + 右下角一个指针）：**遮罩开关**的图标。
 *
 * 形状 = Material 官方 `highlight_alt` 的成型路径（用户 2026-09-16 截图指定）。
 * 为什么不直接用 `Icons.Filled.HighlightAlt`：它属于 **material-icons-extended**，
 * 而本项目刻意不引那个包（一个包 37.7 MB，见 `naistudio-shared/README.md`）。
 * 这里的做法和 [UndoIcon] / [RedoIcon] / [LockClosedIcon] 一样 —— 抄官方路径原文。
 *
 * ⚠️ 2026-09-18 深夜：用户口径确认 —— **虚线無指针那版只属于画布编辑器**（[CanvasSelectIcon]），
 * 遮罩这一条路要**回调成带箭头的原样**。所以本图标恢复原路径，两处不再共用。
 */
val MaskSelectIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "MaskSelect",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = svgPath(
            "M17 5h-2V3h2v2zm-2 16h2v-2.59L19.59 21 21 19.59 18.41 17H21v-2h-6v6zm4-12h2V7h-2v2z" +
                "m0 4h2v-2h-2v2zm-8 8h2v-2h-2v2zM7 5h2V3H7v2zM3 17h2v-2H3v2zm2 4v-2H3c0 1.1.9 2 2 2" +
                "zM19 3v2h2c0-1.1-.9-2-2-2zm-8 2h2V3h-2v2zM3 9h2V7H3v2zm4 12h2v-2H7v2zm-4-8h2v-2H3v2z" +
                "m0-8h2V3c-1.1 0-2 .9-2 2z",
        ),
        fill = SolidColor(Color.Black),
    ).build()
}

/**
 * 「选择/框选」：**全由虚线组成的方框**、没有右下角那颗指针 —— **只给画布编辑器**的
 * 「选择」工具按钮（`CanvasTool.SELECT`）用（用户 2026-09-18 深夜：改的只有画布编辑器那颗，
 * 遮罩仍然用带箭头的 [MaskSelectIcon]）。
 *
 * 手写的等距虚线方框：四条边各 5 段 2×2 的小方块（2 实 / 2 空的节奏，和 `highlight_alt`
 * 原本的虚线节奏一致），四角那 4 段两块边共用。
 *
 * ⚠️ 改法说明：不是"从 `highlight_alt` 里删掉指针那一小段"，而是**重排整圈虚线** ——
 * `highlight_alt` 里右下角那片本来就被指针压着（指针盖住了右边 y15–17 和底边 x15–17
 * 两段虚线），只删指针会缺一大块。
 */
val CanvasSelectIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "CanvasSelect",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = svgPath(
            // 四角（左上 / 右上 / 左下 / 右下）
            "M3 3h2v2h-2zM19 3h2v2h-2zM3 19h2v2h-2zM19 19h2v2h-2z" +
                // 上下边中段：x 7–9 / 11–13 / 15–17
                "M7 3h2v2h-2zM11 3h2v2h-2zM15 3h2v2h-2z" +
                "M7 19h2v2h-2zM11 19h2v2h-2zM15 19h2v2h-2z" +
                // 左右边中段：y 7–9 / 11–13 / 15–17
                "M3 7h2v2h-2zM3 11h2v2h-2zM3 15h2v2h-2z" +
                "M19 7h2v2h-2zM19 11h2v2h-2zM19 15h2v2h-2z",
        ),
        fill = SolidColor(Color.Black),
    ).build()
}

/** 「重做」↷：形状 = Material `redo`。 */
val RedoIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Redo",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = svgPath(
            "M18.4 10.6C16.55 8.99 14.15 8 11.5 8c-4.65 0-8.58 3.03-9.96 7.22L3.9 " +
                "16c1.05-3.19 4.05-5.5 7.6-5.5 1.95 0 3.73.72 5.12 1.88L13 16h9V7l-3.6 3.6z",
        ),
        fill = SolidColor(Color.Black),
    ).build()
}

/**
 * 「关锁」—— 提示词框右上角那把锁（用户 2026-09-16 要求）。
 *
 * 形状直接用 Material 官方 `lock` 的成型路径（和 [UndoIcon] 一个路子）。
 *
 * ## ⚠️ 这条路径有 **4** 段闭合子轮廓，少一段就画错
 *
 *  1. 外轮廓：锁梁外弧 + 锁体（带圆角）；
 *  2. 钥匙孔（那个小圆）；
 *  3. 锁体内部挖空（`M18 20H6V10h12v10z`）；
 *  4. **锁梁的内圈**（`M15.1 8H8.9V6…`）—— 挖掉它，锁梁中间才是空的。
 *
 * 第 4 段漏掉的后果**不是"缺一块"而是"多一块"**：锁梁的外弧到锁体之间没有内圈去挖，
 * 整个上半部分会被填成一坨实心的（用户 2026-09-16 报的正是这个）。
 * [LockOpenIcon] 没这个问题 —— 它是把内圈写在主轮廓里连成一笔的。
 */
val LockClosedIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "LockClosed",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = svgPath(
            "M18 8h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6v2H6c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h12" +
                "c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2zm-6 9c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2z" +
                "m6 3H6V10h12v10z" +
                // ↓ 锁梁内圈（第 4 段）。别删。
                "M15.1 8H8.9V6c0-1.71 1.39-3.1 3.1-3.1s3.1 1.39 3.1 3.1v2z",
        ),
        fill = SolidColor(Color.Black),
    ).build()
}

/**
 * 「开锁」：与 [LockClosedIcon] 同形，但锁梁左边那条腿**悬空**（没落到锁体上）。
 *
 * 注意它和关锁的写法不一样：内圈是**并进主轮廓一笔画完**的
 * （`…7 6h1.9c0-1.71…` 从外弧末端直接拐回内侧弧），所以只有 3 段闭合子路径。
 * 这也是为什么关锁曾经漏掉内圈、开锁却没事 —— 两者的路径结构本来就不同。
 */
val LockOpenIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "LockOpen",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = svgPath(
            "M18 8h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6h1.9c0-1.71 1.39-3.1 3.1-3.1s3.1 1.39 3.1 3.1v2" +
                "H6c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2z" +
                "m-6 9c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2zm6 3H6V10h12v10z",
        ),
        fill = SolidColor(Color.Black),
    ).build()
}

/**
 * 「放大」图标：**只有四个角**的方框，圆角很小、整体偏方（用户 2026-09-16 要求）。
 *
 * ## 圆角半径为什么是 3
 *
 * 图标按 16dp 渲染，viewport 是 24 —— 缩放比 1.5，所以 viewport 里的 3 相当于
 * 屏幕上的 2dp，是个"看起来还是直角、但收口不扎人"的量级。
 *
 * ⚠️ 早先用的是**纯四分之一圆、半径 6**（等于屏幕上的 4dp，正好对上文本框的圆角），
 * 用户反馈"还是方一些"。所以现在每段改成**直线 + 小圆弧 + 直线**：
 * 弧只吃中间 3 格，两侧各留 3 格直臂 —— 半径小了、观感方了，
 * 但整段的跨度仍然是 4.5 → 10.5（**位置和大小没变**，改的只是曲率）。
 *
 * 用描边画法（和 [ClockIcon] / [SparkleIcon] 一档），不抠 Material 的填色路径：
 * 纯几何图形手写更直接，也没有"抄漏一段就糊成实心"的风险（[LockClosedIcon] 栽过）。
 */
val ExpandCornersIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "ExpandCorners",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            // 左上：竖臂 → 圆角 → 横臂
            moveTo(4.5f, 10.5f)
            verticalLineTo(7.5f)
            arcToRelative(3f, 3f, 0f, false, true, 3f, -3f)
            horizontalLineTo(10.5f)
            // 右上
            moveTo(13.5f, 4.5f)
            horizontalLineTo(16.5f)
            arcToRelative(3f, 3f, 0f, false, true, 3f, 3f)
            verticalLineTo(10.5f)
            // 右下
            moveTo(19.5f, 13.5f)
            verticalLineTo(16.5f)
            arcToRelative(3f, 3f, 0f, false, true, -3f, 3f)
            horizontalLineTo(13.5f)
            // 左下
            moveTo(10.5f, 19.5f)
            horizontalLineTo(7.5f)
            arcToRelative(3f, 3f, 0f, false, true, -3f, -3f)
            verticalLineTo(13.5f)
        }
    }.build()
}

// ---------------------------------------------------------------------------
// 画布编辑器的工具图标（2026-09-16，官方画布那 8 个工具）
//
// 这一组全是**抄 Material 官方 24px 路径原文**（和 [UndoIcon] / [MaskSelectIcon] 一个路子）：
// 画笔直接复用 core 里的 `Icons.Filled.Edit`，选择复用已有的 [MaskSelectIcon]，
// 剩下 7 个（橡皮 / 油漆桶 / 套索 / 吸管 / 水滴 / 图章 / 调节）在 core 里没有，只能手写在这。
// ---------------------------------------------------------------------------

/** 橡皮：Material `ink_eraser`（斜着的橡皮 + 擦掉的一角）。 */
val EraserIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Eraser",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = svgPath(
            "M15.14 3c-.51 0-1.02.2-1.41.59L2.59 14.73c-.78.77-.78 2.04 0 2.83L5.03 20H16" +
                "l5.13-5.13c.79-.78.79-2.05 0-2.83l-4.85-4.86C16.17 3.2 15.66 3 15.14 3z" +
                "M7.42 18l-3.4-3.4 5.65-5.66 3.4 3.4L7.42 18z",
        ),
        fill = SolidColor(Color.Black),
    ).build()
}

/** 油漆桶：Material `format_color_fill`（桶 + 一滴颜料）。 */
val BucketIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Bucket",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = svgPath(
            "M16.56 8.94L7.62 0 6.21 1.41l2.38 2.38-5.15 5.15c-.59.59-.59 1.54 0 2.12" +
                "l5.5 5.5c.29.29.68.44 1.06.44s.77-.15 1.06-.44l5.5-5.5c.59-.58.59-1.53 0-2.12z" +
                "M5.21 10L10 5.21 14.79 10H5.21z" +
                "M19 11.5s-2 2.17-2 3.5c0 1.1.9 2 2 2s2-.9 2-2c0-1.33-2-3.5-2-3.5z",
        ),
        fill = SolidColor(Color.Black),
    ).build()
}

/**
 * 套索：一圈绳 + 垂下来的绳尾。
 *
 * ⚠️ 这个**没有**官方路径可抄（Material 里没有 lasso），所以是手绘的描边图形 ——
 * 和 [SidebarToggleIcon] / [ExpandCornersIcon] 一档的做法：曲线都用 `curveTo` 写死，
 * 不拼贝塞尔玄学，改起来也好改。
 */
val LassoIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Lasso",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            // 绳圈（扁椭圆，和官方的套索一样是"横着的一圈"）
            moveTo(12f, 4.4f)
            curveTo(16.7f, 4.4f, 20.3f, 6.8f, 20.3f, 9.7f)
            curveTo(20.3f, 12.6f, 16.7f, 15f, 12f, 15f)
            curveTo(7.3f, 15f, 3.7f, 12.6f, 3.7f, 9.7f)
            curveTo(3.7f, 6.8f, 7.3f, 4.4f, 12f, 4.4f)
            close()
            // 绳尾
            moveTo(8.6f, 14.4f)
            curveTo(8f, 16.5f, 8.7f, 18.3f, 10.3f, 19.2f)
            // 尾巴末端的小结
            arcToRelative(1.15f, 1.15f, 0f, true, true, 0f, -2.3f)
            arcToRelative(1.15f, 1.15f, 0f, true, true, 0f, 2.3f)
            close()
        }
    }.build()
}

/** 吸管：Material `colorize`。 */
val DropperIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Dropper",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = svgPath(
            "M17.66 5.41l.92.92-2.69 2.69-.92-.92 2.69-2.69M17.67 3c-.26 0-.51.1-.71.29" +
                "l-3.12 3.12-1.93-1.91-1.41 1.41 1.42 1.42L3 16.25V21h4.75l8.92-8.92 1.42 1.42" +
                " 1.41-1.41-1.92-1.92 3.12-3.12c.4-.4.4-1.03.01-1.42l-2.34-2.34c-.2-.19-.45-.29-.7-.29z" +
                "M6.92 19L5 17.08l8.06-8.06 1.92 1.92L6.92 19z",
        ),
        fill = SolidColor(Color.Black),
    ).build()
}

/** 模糊：Material `opacity` 的水滴（官方那支模糊工具的图标就是一滴水）。 */
val BlurIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Blur",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = svgPath(
            "M12 2.35 6.34 8C4.78 9.56 4 11.64 4 13.64s.78 4.11 2.34 5.67 3.61 2.35 5.66 2.35" +
                " 4.1-.79 5.66-2.35 2.34-3.62 2.34-5.67S19.22 9.56 17.66 8L12 2.35z",
        ),
        fill = SolidColor(Color.Black),
    ).build()
}

/** 仿制图章：Material `content_copy`（两个叠起来的方块 = 复制一块细节）。 */
val CloneIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Clone",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = svgPath(
            "M16 1H4c-1.1 0-2 .9-2 2v14h2V3h12V1zm3 4H8c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h11" +
                "c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm0 16H8V7h11v14z",
        ),
        fill = SolidColor(Color.Black),
    ).build()
}

/** 「调节」：Material `tune`（两条带滑块的横杆）—— 编辑器右边那颗设置按钮。 */
val TuneIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Tune",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = svgPath(
            "M3 17v2h6v-2H3zM3 5v2h10V5H3zm10 16v-2h8v-2h-8v-2h-2v6h2zM7 9v2H3v2h4v2h2V9H7z" +
                "m14 4v-2H11v2h10zm-6-4h2V7h4V5h-4V3h-2v6z",
        ),
        fill = SolidColor(Color.Black),
    ).build()
}

/** 画笔：Material `brush` 的形状（core 图标集里没有）。 */
val BrushIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Brush",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = svgPath(
            "M7 14c-1.66 0-3 1.34-3 3 0 1.31-1.16 2-2 2 .92 1.22 2.49 2 4 2 2.21 0 4-1.79 4-4 0-1.66-1.34-3-3-3z" +
                "M20.71 4.63l-1.34-1.34c-.39-.39-1.02-.39-1.41 0L9 12.25 11.75 15l8.96-8.96c.39-.39.39-1.02 0-1.41z",
        ),
        fill = SolidColor(Color.Black),
    ).build()
}

/**
 * **太阳**：侧栏左下角那颗明暗切换按钮，**当前是深色主题**时显示它（用户 2026-09-26：
 * 「深色模式时是白色的太阳，浅色模式时是黑色的月亮」）。
 *
 * 自绘（不引 `material-icons-extended`，见本文件开头）：一个正圆 + 八条光芒，描边画法 ——
 * 和 [ClockIcon] / [ExpandCornersIcon] 一档，纯几何图形手写最稳。
 * ⚠️ 颜色**不写死**：这里是黑色占位，真机上由 `Icon` 的 `tint` 覆盖成主题前景色（深色下白）。
 */
val SunIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Sun",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            // 圆盘（两段半圆拼一个整圆，和 PictureIcon 里的太阳一个写法）
            moveTo(12f, 8.1f)
            arcToRelative(3.9f, 3.9f, 0f, true, true, 0f, 7.8f)
            arcToRelative(3.9f, 3.9f, 0f, true, true, 0f, -7.8f)
            close()
            // 八条光芒（上 / 下 / 左 / 右 + 四个斜角）
            moveTo(12f, 2.4f); verticalLineTo(4.9f)
            moveTo(12f, 19.1f); verticalLineTo(21.6f)
            moveTo(2.4f, 12f); horizontalLineTo(4.9f)
            moveTo(19.1f, 12f); horizontalLineTo(21.6f)
            moveTo(5.2f, 5.2f); lineTo(7f, 7f)
            moveTo(17f, 17f); lineTo(18.8f, 18.8f)
            moveTo(18.8f, 5.2f); lineTo(17f, 7f)
            moveTo(7f, 17f); lineTo(5.2f, 18.8f)
        }
    }.build()
}

/**
 * **月亮**：侧栏左下角那颗明暗切换按钮，**当前是浅色主题**时显示它（用户 2026-09-26：
 * 「深色模式时是白色的太阳，浅色模式时是黑色的月亮」）。
 *
 * 形状 = Material 官方 `dark_mode` 的成型路径（和 [UndoIcon] / [MaskSelectIcon] 一样抄路径原文：
 * 月牙是一整块挖出来的形状，手写贝塞尔容易画歪）。颜色同样由 `Icon` 的 `tint` 决定。
 */
val MoonIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Moon",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = svgPath(
            "M12 3c-4.97 0-9 4.03-9 9s4.03 9 9 9 9-4.03 9-9c0-.46-.04-.92-.1-1.36-.98 1.37-2.58 " +
                "2.26-4.4 2.26-2.98 0-5.4-2.42-5.4-5.4 0-1.81.89-3.42 2.26-4.4-.44-.06-.9-.1-1.36-.1z",
        ),
        fill = SolidColor(Color.Black),
    ).build()
}

// ---------------------------------------------------------------------------
// 图层面板的「眼睛」（用户 2026-09-26 / 09-20：图层那一行左边要有 👁 显隐）
//
// 又是两个 core 图标集里没有的形状（`visibility` / `visibility_off` 属于
// material-icons-extended，本项目刻意不引 ✗）→ 照 [ClockIcon] / [SidebarToggleIcon]
// 那一档**描边画法**手写：一个杏仁形眼眶 + 一个瞳孔，闭眼就是同一只眼睛加一道斜杠 ✓。
// 纯几何图形，没有"抄漏一段就糊成实心"的风险（[LockClosedIcon] 栽过 ✓）。
// ---------------------------------------------------------------------------

/** 「眼睛」（图层**看得见**）。 */
val EyeIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Eye",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            // 眼眶：上下两条弧拼成一个杏仁形（左右两端收在 (2.6,12) / (21.4,12) ✓）
            moveTo(2.6f, 12f)
            curveTo(5.4f, 7.4f, 8.6f, 6f, 12f, 6f)
            curveTo(15.4f, 6f, 18.6f, 7.4f, 21.4f, 12f)
            curveTo(18.6f, 16.6f, 15.4f, 18f, 12f, 18f)
            curveTo(8.6f, 18f, 5.4f, 16.6f, 2.6f, 12f)
            close()
            // 瞳孔：两段半圆拼一个整圆（和 [ClockIcon] 的表盘一个写法）
            moveTo(12f, 9.1f)
            arcToRelative(2.9f, 2.9f, 0f, true, true, 0f, 5.8f)
            arcToRelative(2.9f, 2.9f, 0f, true, true, 0f, -5.8f)
            close()
        }
    }.build()
}

/**
 * **「向下合并」**（压平位图层那颗图标）—— 用户 2026-09-20：「**压平位图层做成如图的向下合并按钮**」
 * ✓（图一：**两块错开的方框 + 前面一个向下粗箭头** ✓）。
 *
 * 又是 core 图标集里没有的形状（`merge` / `merge_type` 属于 **material-icons-extended**
 * ——本项目刻意不引那个包，见本文件开头 ✗）→ 照 [ClockIcon] / [SidebarToggleIcon] / [EyeIcon]
 * 那一档**描边画法**手写 ✓。
 *
 * ## 形状怎么摆的
 *
 *  · **两块错开的方框**（描边圆角矩形，`strokeLineWidth = 1.5`）—— 像 `content_copy` 那样
 *    斜着错开：后面那块在**左上**、前面那块在**右下** ✓（"叠着"的观感 ✓）；
 *  · **一个向下的粗箭头**（`strokeLineWidth = 2.2`，比方框粗一档 ✓ = 用户说的"**粗**箭头" ✓）——
 *    竖杆从两块方框中间穿下来（**画在后面**：路径顺序在方框之后 ⇒ 压在上面 = "**前面**" ✓），
 *    箭头收在整个图形**底部的正中**（方框下沿之下 ✓），所以不看杆、光看箭头也知道"往下" ✓。
 *
 * ⚠️ 全部用**描边**画（圆头圆角 ✓）：纯几何图形手写最稳，没有"抄漏一段就糊成实心"的风险
 * （[LockClosedIcon] 栽过 ✗）。颜色同样是黑色占位 —— 真机上由 `Icon` 的 `tint` 覆盖 ✓。
 *
 * ⚠️ 这里**没有** `Modifier.border(...)` 那种东西（图标是矢量，压根不碰它 ✓）——
 * 电脑端 `Modifier.border(宽, 色, 自定义 Shape)` 会崩（`Failed to Image::makeFromBitmap` ✗），
 * 要用的地方一律走标准形状 / `drawPath` ✓。
 */
val MergeDownIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "MergeDown",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        // ---- 两块错开的方框（后面那块在左上 ✓）----
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.5f,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            // 后：x 6.0..14.4 / y 2.6..11.0（圆角 1.8）
            moveTo(7.8f, 2.6f)
            horizontalLineTo(12.6f)
            arcToRelative(1.8f, 1.8f, 0f, false, true, 1.8f, 1.8f)
            verticalLineTo(9.2f)
            arcToRelative(1.8f, 1.8f, 0f, false, true, -1.8f, 1.8f)
            horizontalLineTo(7.8f)
            arcToRelative(1.8f, 1.8f, 0f, false, true, -1.8f, -1.8f)
            verticalLineTo(4.4f)
            arcToRelative(1.8f, 1.8f, 0f, false, true, 1.8f, -1.8f)
            close()
            // 前：x 9.6..18.0 / y 5.6..14.0（同一个尺寸，往右下错开 ✓）
            moveTo(11.4f, 5.6f)
            horizontalLineTo(16.2f)
            arcToRelative(1.8f, 1.8f, 0f, false, true, 1.8f, 1.8f)
            verticalLineTo(12.2f)
            arcToRelative(1.8f, 1.8f, 0f, false, true, -1.8f, 1.8f)
            horizontalLineTo(11.4f)
            arcToRelative(1.8f, 1.8f, 0f, false, true, -1.8f, -1.8f)
            verticalLineTo(7.4f)
            arcToRelative(1.8f, 1.8f, 0f, false, true, 1.8f, -1.8f)
            close()
        }
        // ---- 前面那个向下的粗箭头（竖杆 + 箭头，画在方框之后 = 压在上面 ✓）----
        //
        // 竖杆走整个图形的中线 x = 12（两块方框的并集正好以它居中 ✓），
        // 从方框内部（y 5.0）穿到底（y 20.4）；箭头两翼 (9.2,17.2) → 尖 (12,20.4) → (14.8,17.2)。
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2.2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(12f, 5f)
            verticalLineTo(20.4f)
            moveTo(9.2f, 17.2f)
            lineTo(12f, 20.4f)
            lineTo(14.8f, 17.2f)
        }
    }.build()
}

/** 「闭眼」（图层**已隐藏**）：同一只眼睛 + 一道从左上到右下的斜杠。 */
val EyeOffIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "EyeOff",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(2.6f, 12f)
            curveTo(5.4f, 7.4f, 8.6f, 6f, 12f, 6f)
            curveTo(15.4f, 6f, 18.6f, 7.4f, 21.4f, 12f)
            curveTo(18.6f, 16.6f, 15.4f, 18f, 12f, 18f)
            curveTo(8.6f, 18f, 5.4f, 16.6f, 2.6f, 12f)
            close()
            moveTo(12f, 9.1f)
            arcToRelative(2.9f, 2.9f, 0f, true, true, 0f, 5.8f)
            arcToRelative(2.9f, 2.9f, 0f, true, true, 0f, -5.8f)
            close()
            // 斜杠（画在眼睛之上，和系统里那张"划掉的眼睛"同一个意思 ✓）
            moveTo(4.2f, 20.2f)
            lineTo(19.8f, 4.6f)
        }
    }.build()
}

// ---------------------------------------------------------------------------
// ㉓ SAI 笔刷面板补的两颗（2026-09-20 · 第 26 轮）
//
// 为什么补 ✗：网页面板那 22 颗工具里，「动线」那一类（散布 / 特效笔 / 涂抹）与「涂黑」
// 在手边这套图标里各有各的形状 ✓，而 `material-icons-core` **只有几十个**（见本文件开头 ✓）——
// 这两个形状里头一个都没有 ✗，所以照 [EraserIcon] / [BucketIcon] 那一路子**抄官方路径原文** ✓
//（**绝不引 `material-icons-extended`** ✗：那一个包 37.7 MB ✓）。
// ---------------------------------------------------------------------------

/**
 * **动线 / 拖尾**：左上一颗实心笔尖 + 一串越走越小、越走越淡的点 ✓（`blur_on` 的形状 ✓）。
 *
 * 给面板上「散布 / 特效笔 / 涂抹 / 球形笔刷」这四颗用 ✓ —— 它们都是"一颗一颗盖出来"的 ✓，
 * 和「动线」那一类正好对上 ✓（原来是 `Icons.Filled.Clear`：一个叉 ✗，看不出笔意 ✓）。
 */
val CometIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Comet",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = svgPath(
            "M14 10c-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4-1.79-4-4-4z" +
                "M14 3c-1.93 0-3.7.61-5.16 1.63l1.4 1.43C11.28 5.4 12.6 5 14 5c4.96 0 9 4.04 9 9" +
                " 0 1.4-.4 2.72-1.06 3.76l1.43 1.4C24.39 17.7 25 15.93 25 14c0-6.08-4.92-11-11-11z" +
                "M14 7c-3.87 0-7 3.13-7 7 0 1.13.31 2.19.75 3.15l1.63-1.63C9.14 15.05 9 14.54 9 14" +
                "c0-2.76 2.24-5 5-5 .54 0 1.05.14 1.52.38l1.63-1.63C16.19 7.31 15.13 7 14 7z",
        ),
        fill = SolidColor(Color.Black),
    ).build()
}

/**
 * **涂黑**：一个实心圆 ✓（= "这一笔就是把它涂死" ✓）。
 *
 * 面板上「涂黑」那一颗用 ✓；`material-icons-core` 里**没有**任何实心圆 ✗
 *（最接近的 `Favorite` 是心形 ✓），所以照 [MaskSelectIcon] 那一路子抄官方 `circle` 的路径 ✓。
 */
val FilledCircleIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "FilledCircle",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = svgPath("M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2z"),
        fill = SolidColor(Color.Black),
    ).build()
}
/**
 * **参数图标 =「三条横杠 + 每杠一个小点」的滑条**（用户 2026-09-25：
 * 「参数的图标变为**三条杠，上面有小点**，含义滑条」）。
 *
 * 为什么自己画而不是用 `Icons.Filled.Tune`：`Tune` 是"三条带把手的滑轨"，
 * 而用户点名的是**杠 + 小点**那种（像设置里的"滑条"符号）。自绘 24 视口、成本为零。
 *
 * 画法（纯填充矩形/圆，**不用 stroke** —— 描边在 22dp 下容易糊）：
 *  · 三条横杠 `y = 6.5 / 12 / 17.5`，厚度 2，`x` 从 4 到 20（第三条短一点做层次）；
 *  · 每杠上一个**小点**（半径 1.6）：上杠偏左、中杠偏右、下杠居中 —— 一眼是"滑条"。
 * ⚠️ 点的位置刻意错开 ✗ 不要对齐：对齐了看着像"三个省略号"、不像滑条。
 */
val SlidersIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Sliders",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = svgPath(
            // 三条杠
            "M4 5.5h16v2H4z" +
                "M4 11h16v2H4z" +
                "M4 16.5h13v2H4z" +
                // 三个小点（上偏左 / 中偏右 / 下居中）
                "M8 4.5a2 2 0 1 0 0 4 2 2 0 0 0 0-4z" +
                "M16 10a2 2 0 1 0 0 4 2 2 0 0 0 0-4z" +
                "M11 15.5a2 2 0 1 0 0 4 2 2 0 0 0 0-4z",
        ),
        fill = SolidColor(Color.Black),
    ).build()
}