package com.kallan.naistudio.desktop.platform

import com.kallan.naistudio.platform.logInfo
import dev.datlag.kcef.KCEF
import dev.datlag.kcef.KCEFBrowser
import dev.datlag.kcef.KCEFClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.cef.CefSettings
import org.cef.browser.CefBrowser
import org.cef.browser.CefRendering
import org.cef.handler.CefDisplayHandlerAdapter
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.io.File
import java.util.Base64
import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.WindowConstants

/**
 * **网页笔刷窗口**（第 ㉕ 批 ✓；用户 2026-09-20 口径：「**直接搬，不修**」）。
 *
 * 干的事只有一件：把 `brush-lab.html` 这一页**原样**用 **Chromium（CEF）**打开，
 * 装在一个**普通 Swing 窗口**里 ✓。现有那套 Kotlin 笔刷管线**一个字都不碰** ✓。
 *
 * ## 为什么是 Swing 窗口，而不是"嵌进 Compose 界面里"
 *
 * 这是**实测**出来的结论，不是口味问题 —— 见 `tools/webview-spike/`：
 *  · 试过 `io.github.kevinnzou:compose-webview-multiplatform`（CMP 的 WebView 封装 ✓）：
 *    KCEF 默认把 `windowless_rendering_enabled` 打开 ✓，而那个库拿
 *    `browser.getWindowlessFrameRate()` 当"是不是离屏渲染"的判据 ✗ —— 结果它走了 else 分支：
 *    **只给 `uiComponent` 设了个尺寸、根本没加进任何容器** ⇒ 窗口里**什么都没有** ✗✗
 *    （实测抓屏：Compose 窗口里一片空白，连它自己的背景都没画出来 ✗）。
 *  · 改成**自己装**：`client.createBrowser(url, CefRendering.DEFAULT, false).uiComponent`
 *    加进 `JFrame` 的 contentPane ✓ —— **同一台机器上一次就画出来了** ✓
 *    （截图 `tools/webview-spike/spike-window.png`：整页 SAI 面板 + 画布 + 引擎真落笔 ✓）。
 *
 * 附带好处：`CefRendering.DEFAULT` = **有窗口渲染**（`CefBrowserWr` ✓）⇒ 网页拿到的是**真的
 * Windows 消息**（不是我们转发出来的合成事件 ✓）⇒ 数位板的**笔压**才有机会是真的 ✓
 *（离屏渲染那条路要我们自己喂鼠标事件，压感根本没地方传 ✗）。
 *
 * ## 内核从哪来
 *
 * KCEF 首次运行会把 CEF 运行时（**约 570 MB**，含 `libcef.dll` 262 MB）下到 `installDir` ✓。
 * 交付时那一份**必须随镜像一起发**（`<镜像>\app\kcef-bundle\` ✓），否则用户第一次点要等下载 ✓。
 * 见 `docs/45-交付-桌面镜像加新依赖.md`。
 */
object WebBrushWindow {

    /**
     * CEF 运行时落点 = `<App 数据根>\kcef-bundle`（= `%APPDATA%\NAI Studio\kcef-bundle` ✓）。
     *
     * ⚠️ **刻意放在用户数据目录，不放进镜像的 `app\`** ✗✗ —— 这一份**约 570 MB**（`libcef.dll`
     * 一个就 262 MB ✓）。放镜像里的话：桌面那个绿色版目录直接从 **109 MB 涨到 ~690 MB** ✗，
     * 而用户桌面上**有两个镜像**（`NAI Studio\` 与 `dist\NAI Studio\`）⇒ 要**存两份** ✗✗。
     * 放数据目录：两个镜像**共用一份** ✓、镜像体积不变 ✓、将来换版本也不用动 exe ✓。
     *
     * 代价要说清：**头一次点要联网下载**（窗口上有百分比进度 ✓，KCEF 自己会下 ✓）。
     * 交付时我们已经把这一份**放到位上**了（见 `docs/45` 的交付记录 ✓），所以用户点开是秒开 ✓。
     */
    private val installDir: File
        get() = File(appDir(), "kcef-bundle")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var initialized = false

    @Volatile
    private var initFailed: String? = null

    private var frame: JFrame? = null
    private var client: KCEFClient? = null
    private var browser: KCEFBrowser? = null

    /** 这一笔"采用"要回传的动作 ✓（由 AppState 提供 ✓）。 */
    private var adopt: ((ByteArray) -> Boolean)? = null

    /**
     * **框选回来的动作**（`BOX:x,y,w,h`，画布原始像素 ✓）—— 由入口挂上，
     * 里面去走软件已有的 `maskFocusRect` + `FocusedInpaint.plan` 那条生图链 ✓。
     */
    var boxHandler: ((String) -> Unit)? = null

    /** 网页文档尺寸（= 当前漫画页的像素尺寸 ✓，**1:1 采用、中间不重采样** ✓）。 */
    private var docWidth = 1024
    private var docHeight = 1024

    // -----------------------------------------------------------------------
    // 第 ㉛ 批：把网页**当成软件里那张画布**
    //
    //  ① `embedded`：嵌进 Compose 窗口（做法 A ✓）还是独立顶层窗口（做法 B = ㉕ 验过的 ✓）；
    //  ② `pendingLayerPng`：开画前要装进网页画布的那一层（PNG 字节 ✓）—— 浏览器还没好时先存着 ✓；
    //  ③ `installed`：这一份"页面 + 装层 + 自动写回"装好了没有（避免重复注入 ✓）；
    //  ④ `autoAdopt`：抬笔自动写回（默认开 ✓，见 `autoAdoptJs` 的"防丢笔"说明 ✓）。
    // -----------------------------------------------------------------------

    /** 嵌进 Compose 窗口时，浏览器要加进**我们返回的那块面板**（做法 A ✓）。 */
    private var hostPanel: JPanel? = null

    /** 用户当前选的是"嵌进窗口"（true ✓）还是"独立窗口"（false = 兜底 B ✓）。 */
    @Volatile
    private var embedded = true

    /** 开画前要装进网页画布的那一层（PNG 字节 ✓）；null = 空画布 ✓。 */
    @Volatile
    private var pendingLayerPng: ByteArray? = null

    /** 这一份页面装好（装层 + 自动写回都注入了）没有 ✓。 */
    @Volatile
    private var installed = false

    /** 抬笔自动写回开关（第 ㉛ 批 ✓）。 */
    @Volatile
    var autoAdopt: Boolean = true

    /** 上一次自动写回送出去的图（判"这一笔有没有真的写回"用 ✓，诊断 ✓）。 */
    @Volatile
    var lastAutoAdoptBytes: Int = 0
        private set

    /** 装层 / 自动写回的结果（诊断 + 探针判据 ✓）。 */
    @Volatile
    var lastLayerReport: String? = null
        private set

    /**
     * 两张网页，随便切 ✓（窗口工具条上一颗下拉 ✓）。
     *
     * ⚠️ **默认是「简单版」** ✓（用户 2026-09-20：「**简单版的网页绘画手感就很好**」✓）——
     * `brush-lab-simple.html`：**单层、1000×700 固定、没有图层/缩放/旋转** ✓，
     * 笔是直接盖在那唯一一张 canvas 上的 ✓（没有合成、没有小块叠加、没有"抬笔才合进图层"✗）；
     * 完整版 `brush-lab.html` 有图层 / 缩放 / 选区 / 油漆桶 …（功能多，但显示链更长 ✓）。
     */
    private const val PAGE_SIMPLE = "brush-lab-simple.html"
    private const val PAGE_FULL = "brush-lab.html"

    /** 当前在放哪一张 ✓ —— **默认完整版** ✓（用户 2026-09-20：「我要之前的画布体验，可随意拖动、
     *  放大缩小，做好的 ui 也可以直接控制」✓ ⇒ 完整版才有缩放/平移 + 那套做好的 SAI 面板 ✓）。 */
    @Volatile
    private var pageName: String = PAGE_FULL

    /**
     * **预热**（用户口径：「加载快、无感嵌入」✓）。
     *
     * App **启动时**就在后台把 CEF 内核初始化好 ✓（这一步原来是"点开画布时"才做 ⇒ 那一下要等 ✗）。
     * 只 `ensureKcef()`，**不建浏览器、不加载页面** ⇒ 不占内存也不闪任何东西 ✓；
     * 于是用户第一次看到画布区时，内核已经就绪，页面加载是"秒出" ✓。
     */
    fun prewarm() {
        if (initialized || initFailed != null) return
        scope.launch { ensureKcef(null) }
    }

    /**
     * 打开（或把已经开着的那个窗口提到前面 ✓）。
     *
     * @param pageWidth/pageHeight 当前漫画页的像素尺寸 ✓ —— 网页画布会**按它建**（`setupDoc` ✓），
     *   这样"采用"回来是**1:1** 的 ✓（不缩放、不重采样 ✓）。
     * @param onAdopt 收到网页那张 PNG 之后干什么 ✓（返回 true = 收下了 ✓，窗口上会如实显示 ✓）。
     */
    fun open(pageWidth: Int, pageHeight: Int, onAdopt: (ByteArray) -> Boolean) {
        adopt = onAdopt
        docWidth = pageWidth.coerceIn(16, 8192)
        docHeight = pageHeight.coerceIn(16, 8192)
        embedded = false   // ㉕ 这条路 = **独立窗口**（兜底 B ✓）

        val existing = frame
        if (existing != null && existing.isDisplayable) {
            SwingUtilities.invokeLater {
                existing.isVisible = true
                existing.state = JFrame.NORMAL
                existing.toFront()
                existing.requestFocus()
            }
            return
        }
        // ⚠️ UI 线程不许阻塞 ✗：CEF 首次初始化要下载几百 MB ✓ —— 全部丢到 IO 线程 ✓。
        scope.launch { prepareAndShow() }
    }

    // -----------------------------------------------------------------------
    // 第 ㉛ 批：**嵌进 Compose 窗口**那条路（做法 A ✓）
    // -----------------------------------------------------------------------

    /**
     * 取一块**可以嵌进 Compose 界面**的 AWT 面板 ✓（做法 A ✓ —— 见 `WebBrushCanvasEmbed` ✓）。
     *
     * 返回的 `JPanel` 立刻就能加进 `SwingPanel` ✓；Chromium 内核 + 页面加载好之后
     * 才把 `browser.uiComponent` 加进去 ✓（内核没就绪时面板上写一行进度 ✓，不让用户"看着空白"✗）。
     *
     * @param onAdopt 收到网页那张 PNG 之后干什么 ✓
     * @param layerPng 开画前要装进网页画布的**当前那一层**（PNG 字节 ✓；null = 空画布 ✓）
     */
    fun embeddedPanel(
        pageWidth: Int,
        pageHeight: Int,
        layerPng: ByteArray?,
        onAdopt: (ByteArray) -> Boolean,
    ): JPanel {
        adopt = onAdopt
        docWidth = pageWidth.coerceIn(16, 8192)
        docHeight = pageHeight.coerceIn(16, 8192)
        pendingLayerPng = layerPng
        embedded = true
        val existing = hostPanel
        if (existing != null) return existing
        val panel = JPanel(BorderLayout())
        val status = JLabel("正在准备 Chromium 内核（首次要下载约 570 MB，只需一次）…")
        panel.add(status, BorderLayout.CENTER)
        hostPanel = panel
        scope.launch { prepareEmbedded(panel, status) }
        return panel
    }

    /**
     * 装层的 JS ✓（**一个字节都不改这两张网页** ✗ —— 只用它们自己已有的 `DOC` / `DOC.ctx` ✓）。
     *
     * 干三件事：
     *  1. 把网页画布**改成当前漫画页的像素尺寸** ✓ —— `DOC.w/DOC.h` + `cv.width/height` ✓。
     *     ⚠️ **必须能改**：`docXY()`（`:746-751`）是按 `DOC.w` 和画布的 `getBoundingClientRect()`
     *     算坐标的 ✓ ⇒ 改了尺寸之后**坐标自动还是对的** ✓（这也是"1:1 采用"的前提 ✓）。
     *     不去动 CSS ✗：屏幕上照样按页里原本的样式显示，缩放关系由 `docXY` 那一比负责 ✓。
     *  2. 把**当前那一层**画上去 ✓（`im.src` 用的是 **data URL** ✓ ——
     *     不用 `file://`：CEF 默认对 `file://` 页面的本地图片加载有同源限制 ✗，
     *     而 data URL 一定过得去 ✓，代价是字符串大一点 ✓）。
     *  3. 把结果从 console 送出来 ✓（`LAYER:…` / `LAYER-FAIL:…` ✓），探针与日志都读它 ✓。
     */
    private fun installLayerJs(png: ByteArray?): String {
        val w = docWidth
        val h = docHeight
        val dataUrl = if (png == null) "" else "data:image/png;base64," + Base64.getEncoder().encodeToString(png)
        return """
            (function(){
              try{
                DOC.w = $w; DOC.h = $h;
                DOC.cv.width = $w; DOC.cv.height = $h;
                var c = DOC.ctx || DOC.cv.getContext('2d');
                c.setTransform(1,0,0,1,0,0);
                c.globalCompositeOperation = 'source-over';
                c.globalAlpha = 1;
                c.clearRect(0,0,DOC.w,DOC.h);
                var src = '$dataUrl';
                if (!src){
                  console.log('LAYER:empty ' + DOC.w + 'x' + DOC.h);
                  return;
                }
                var im = new Image();
                im.onload = function(){
                  try{
                    c.drawImage(im, 0, 0, DOC.w, DOC.h);
                    var d = c.getImageData(0,0,DOC.w,DOC.h).data;
                    var n = 0;
                    for (var i = 3; i < d.length; i += 4){ if (d[i] !== 0) n++; }
                    console.log('LAYER:ok ' + DOC.w + 'x' + DOC.h + ' inkPx=' + n);
                  }catch(e){ console.log('LAYER-FAIL:draw ' + e.message); }
                };
                im.onerror = function(){ console.log('LAYER-FAIL:image'); };
                im.src = src;
              }catch(e){ console.log('LAYER-FAIL:' + e.message); }
            })();
        """.trimIndent()
    }

    /**
     * **抬笔自动写回** ✓（第 ㉛ 批 —— 用户不许丢笔 ✗）。
     *
     * 做法：只在网页里装一个**只看它自己已有变量**的定时器 ✓（`drawing` / `dabCount` ✓，
     * 见 `:731 drawing`、`:730 dabCount` ✓）—— 检测到"刚才在画、现在不画了、而且新落了笔尖"
     * 就**自己把那张图导出来** ✓，走的是和「采用」按钮**同一段导出 JS** ✓（`adoptJs` ✓）。
     *
     * 为什么不是"改网页、在 `endStroke` 里挂钩子" ✗：那就**动了网页源码** ✓，
     * 而用户口径是"**把网页做上去**"、页面本身不改 ✓（也是这两页能随时替换的前提 ✓）。
     *
     * 防丢笔（三条一起上 ✓）：
     *  1. 每次抬笔 350ms 内写回一次 ✓；
     *  2. **关窗 / 退出这个模式**时再兜一次（`flush()` ✓）；
     *  3. 写回失败**不吞**（如实 `console` + 日志 ✓）。
     */
    private fun autoAdoptJs(): String = """
        (function(){
          if (window.__naiAutoAdopt){ return; }
          window.__naiAutoAdopt = true;
          var lastSeen = (typeof dabCount !== 'undefined') ? dabCount : 0;
          function exportNow(){
            try{
              var url = null;
              if (typeof compCv !== 'undefined' && compCv){ url = compCv.toDataURL('image/png'); }
              else if (typeof DOC !== 'undefined' && DOC && DOC.cv){
                var out = document.createElement('canvas');
                out.width = DOC.w; out.height = DOC.h;
                var g = out.getContext('2d');
                g.setTransform(1,0,0,1,0,0);
                g.globalCompositeOperation = 'source-over';
                g.globalAlpha = 1;
                g.fillStyle = '#ffffff'; g.fillRect(0,0,DOC.w,DOC.h);
                g.drawImage(DOC.cv, 0, 0);
                url = out.toDataURL('image/png');
              }
              if (url){ console.log('PNG:' + url); }
              else { console.log('ADOPT-FAIL:no-canvas'); }
            }catch(e){ console.log('ADOPT-FAIL:' + e.message); }
          }
          window.__naiExportNow = exportNow;
          /* ⚠️ 判据只看「**抬笔了** 且 `dabCount` 比上次见到的多」✗ ——
             第一版还要求"上一次轮询看到过 drawing===true" ✗，那是个**真的会丢笔**的 bug ✓：
             一笔要是**在两轮轮询之间就画完**（快速一划 ✓，实测的合成一笔就是这样 ✓），
             轮询从来没看到过 `drawing===true` ⇒ 永远不写回 ✗✗（实测 `PROBE_AUTO_BYTES=0` ✓）。
             现在改成"不在画 + 笔尖数变了" ⇒ 快笔慢笔都能写回 ✓，而且不会在笔画中途导出 ✓。 */
          setInterval(function(){
            try{
              var d = (typeof dabCount !== 'undefined') ? dabCount : -1;
              var now = (typeof drawing !== 'undefined') ? drawing : false;
              if (!now && d !== lastSeen && d > 0){ lastSeen = d; exportNow(); }
            }catch(e){}
          }, 350);
        })();
    """.trimIndent()

    /** 装层 + 自动写回（页面加载完之后注入一次 ✓）。 */
    private fun installIntoPage() {
        val b = browser ?: return
        val url = htmlUrl(pageName) ?: ""
        b.executeJavaScript(installLayerJs(pendingLayerPng), url, 0)
        if (autoAdopt) b.executeJavaScript(autoAdoptJs(), url, 0)
        installed = true
    }

    /** 浏览器没就绪时先把这一层存着 ✓（就绪之后 `installIntoPage` 会装上去 ✓）。 */
    fun setLayer(png: ByteArray?) {
        pendingLayerPng = png
        if (installed) installIntoPage()
    }

    /**
     * **强制立刻写回一次** ✓（防丢笔的兜底 ✓ —— 关窗 / 退出"网页画布"模式时调 ✓）。
     *
     * 走的是网页里那个 `__naiExportNow`（和自动写回同一段导出逻辑 ✓）；没装过就退回
     * `requestAdopt()`（= ㉕ 那颗「采用」按钮走的路 ✓）。
     */
    fun flush() {
        val b = browser ?: return
        val url = htmlUrl(pageName) ?: ""
        b.executeJavaScript("try{ if(window.__naiExportNow){ window.__naiExportNow(); } }catch(e){ console.log('ADOPT-FAIL:'+e.message); }", url, 0)
    }

    /** 探针用：这一份装好了没有 + 上一次装层的结果 ✓。 */
    internal fun installState(): Pair<Boolean, String?> = installed to lastLayerReport

    /**
     * **探针用：在网页画布上合成一笔** ✓（第 ㉛ 批）。
     *
     * ⚠️ 关键是"**走网页自己的监听器**"✗ —— 用 `cv.dispatchEvent(new PointerEvent(...))`
     * 造 `pointerdown` → 若干 `pointermove` → `pointerup` ✓，于是网页那三段
     * （`:773` / `:744` / `:770`）**原封不动**地被走到 ✓（**网页源码一个字节都没改** ✓）。
     * 坐标用**客户区坐标** ✓：`docXY()`（`:746-751`）会按 `getBoundingClientRect()` 的比值
     * 换算成文档坐标 ✓，所以这里给屏幕坐标、给多少都行 ✓。
     *
     * 之所以要合成而不是"等着用户来画"：整条链**没有真人就验不了** ✗，
     * 这条探针是把"装层 → 画 → 自动写回"跑成**可复现数字**的唯一办法 ✓。
     */
    internal fun paintForSelfTest() {
        val b = browser ?: run {
            println("PROBE_PAINT=no-browser")
            return
        }
        val url = htmlUrl(pageName) ?: ""
        b.executeJavaScript(paintProbeJs(), url, 0)
    }

    /** 合成一笔的 JS（见 [paintForSelfTest] ✓）。 */
    private fun paintProbeJs(): String = """
        (function(){
          try{
            var r = cv.getBoundingClientRect();
            var w = r.width, h = r.height;
            function ev(type, x, y){
              var e = new PointerEvent(type, {
                bubbles: true, cancelable: true, composed: true,
                clientX: r.left + x, clientY: r.top + y,
                pointerId: 1, pointerType: 'pen', isPrimary: true,
                buttons: (type === 'pointerup') ? 0 : 1, pressure: 0.8
              });
              Object.defineProperty(e, 'pressure', { get: function(){ return 0.8; } });
              cv.dispatchEvent(e);
            }
            var y0 = h * 0.35;
            ev('pointerdown', w * 0.15, y0);
            for (var i = 1; i <= 24; i++){
              ev('pointermove', w * (0.15 + 0.7 * i / 24), y0 + Math.sin(i / 3) * h * 0.12);
            }
            ev('pointerup', w * 0.85, y0);
            console.log('PAINT:synthetic dabs=' + dabCount);
          }catch(e){ console.log('PAINT-FAIL:' + e.message); }
        })();
    """.trimIndent()

    // -----------------------------------------------------------------------
    // 建窗
    // -----------------------------------------------------------------------

    private suspend fun prepareAndShow() {
        // ⚠️ 第 ㉛ 批：窗口壳先出来（带进度那行字 ✓），内核再初始化 ✓ —— 原来这两步合在一起，
        //    拆开是因为**做法 A**（嵌进 Compose 窗口）不需要这个 `JFrame` ✗，但**共用**同一个内核 ✓。
        val (f, p) = createWindowShell()
        withContext(Dispatchers.Main) { f.isVisible = true }
        if (!ensureKcef(p)) return
        createBrowserIntoWindow()
    }

    /** 内核初始化（两条路共用 ✓）—— 已成功过就直接 true ✓（返回值 = 可以继续建浏览器 ✓）。 */
    private suspend fun ensureKcef(statusLabel: JLabel? = null): Boolean {
        val failed = initFailed
        if (failed != null) {
            logInfo("WebBrush", "[WebBrush] init 之前失败过：$failed")
            return false
        }
        if (initialized) return true
        try {
            KCEF.init(
                builder = {
                    installDir(installDir)
                    progress {
                        onDownloading { percent ->
                            // ⚠️ 这个回调**不是**挂起上下文 ⇒ 不能 `withContext` ✗ ——
                            // 直接 `invokeLater` 丢给 UI 线程写那行字 ✓。
                            SwingUtilities.invokeLater {
                                statusLabel?.text = "正在下载 Chromium 内核… ${percent.toInt()}%"
                            }
                        }
                        onInitialized { }
                    }
                },
                onError = { error ->
                    initFailed = error?.message ?: "未知错误"
                    logInfo("WebBrush", "[WebBrush] KCEF 初始化失败：$error")
                    error?.printStackTrace()
                },
                onRestartRequired = {
                    logInfo("WebBrush", "[WebBrush] KCEF 要求重启才能加载刚下载的内核")
                },
            )
            initialized = true
            logInfo("WebBrush", "[WebBrush] KCEF 初始化完成 installDir=${installDir.absolutePath}")
        } catch (t: Throwable) {
            initFailed = t.message ?: t.javaClass.simpleName
            logInfo("WebBrush", "[WebBrush] KCEF 初始化抛异常：$t")
            SwingUtilities.invokeLater { statusLabel?.text = "Chromium 内核没起来：$initFailed" }
            return false
        }
        return true
    }

    /**
     * **做法 A**（嵌进 Compose 窗口 ✓）：内核 + 页面就绪之后，把 `browser.uiComponent`
     * 加进调用方给的那块 `JPanel` ✓。
     *
     * ⚠️ 这条路**只能靠用户实机确认能不能看见** ✗ —— 见 `WebBrushCanvasEmbed` 头上那段说明 ✓。
     */
    private suspend fun prepareEmbedded(panel: JPanel, status: JLabel) {
        status.text = "正在准备 Chromium 内核（首次要下载约 570 MB，只需一次）…"
        if (!ensureKcef(status)) return
        if (browser != null) {
            withContext(Dispatchers.Main) { attachBrowserTo(panel, status) }
            return
        }
        val url = htmlUrl(pageName)
        if (url == null) {
            logInfo("WebBrush", "[WebBrush] 找不到 $pageName（既不在镜像里也不在 docs 里）")
            withContext(Dispatchers.Main) { status.text = "找不到网页文件：$pageName" }
            return
        }
        val c = KCEF.newClientOrNullBlocking()
        if (c == null) {
            logInfo("WebBrush", "[WebBrush] newClient 返回 null（内核没就绪？）")
            withContext(Dispatchers.Main) { status.text = "Chromium 内核没就绪" }
            return
        }
        c.addDisplayHandler(consoleHandler())
        val b = c.createBrowser(url, CefRendering.DEFAULT, false)
        client = c
        browser = b
        withContext(Dispatchers.Main) { attachBrowserTo(panel, status) }
        scope.launch {
            // ★ 反复隐藏页面 UI（用户 2026-09-20：「**加载时不要出现 UI，直接就是画布**」✓）——
            //   从加载那一刻起每 300ms 注一次、持续 15 秒：页面自己后面建的 DOM 也藏得住 ✓，
            //   "先闪一下 UI" 也压得住 ✓。
            repeat(50) {
                b.executeJavaScript(hideUiJs(), url, 0)
                kotlinx.coroutines.delay(300)
            }
        }
        scope.launch {
            kotlinx.coroutines.delay(4000)
            b.executeJavaScript(readyProbeJs(), url, 0)
            b.executeJavaScript(hideUiJs(), url, 0)
            // 第 ㉛ 批：页面就绪之后**立刻装层 + 装上自动写回** ✓（不让用户画到空画布上 ✗）
            kotlinx.coroutines.delay(1500)
            installIntoPage()
        }
    }

    /** 把浏览器组件挂进嵌入口那块面板（只挂一次 ✓）。 */
    private fun attachBrowserTo(panel: JPanel, status: JLabel) {
        val b = browser ?: return
        if (b.uiComponent.parent === panel) return
        panel.removeAll()
        panel.add(b.uiComponent, BorderLayout.CENTER)
        panel.revalidate()
        panel.repaint()
        status.text = " "
    }

    /** console 回收（两条路共用 ✓）：`PNG:` 就是"把图拿回来"的全部协议 ✓。 */
    private fun consoleHandler(): CefDisplayHandlerAdapter = object : CefDisplayHandlerAdapter() {
        override fun onConsoleMessage(
            b: CefBrowser?,
            level: CefSettings.LogSeverity?,
            message: String?,
            source: String?,
            line: Int,
        ): Boolean {
            val m = message ?: return false
            when {
                m.startsWith(PNG_PREFIX) -> handlePng(m.removePrefix(PNG_PREFIX))
                m.startsWith("LAYER:") || m.startsWith("LAYER-FAIL:") -> {
                    lastLayerReport = m
                    logInfo("WebBrush", "[WebBrush] $m")
                }
                m.startsWith("BOX:") -> {
                    // 框选结果（**画布原始像素** ✓）：`BOX:x,y,w,h` ⇒ 交给软件那条生图链 ✓
                    val box = m.removePrefix("BOX:")
                    logInfo("WebBrush", "[WebBrush] 框选 = $box")
                    boxHandler?.invoke(box)
                }
                else -> logInfo("WebBrush", "[WebBrush] console: $m")
            }
            return false
        }
    }

    /** 先出一个窗口（带工具条 ✓），内核没就绪之前它显示进度 ✓ —— 不让用户"点了没反应" ✗。 */
    private fun createWindowShell(): Pair<JFrame, JLabel> {
        val status = JLabel(" ")
        lateinit var f: JFrame
        SwingUtilities.invokeAndWait {
            f = JFrame("网页笔刷 · NAI Studio（原本是 docs/brush-lab.html）")
            f.defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
            f.layout = BorderLayout()
            val bar = JPanel(FlowLayout(FlowLayout.LEFT, 8, 4))
            val adoptBtn = JButton("采用到绘画层")
            val reloadBtn = JButton("重新加载")
            // 两张网页随便切 ✓（默认简单版 —— 用户点名它的手感好 ✓）。
            val pageBox = javax.swing.JComboBox(arrayOf("简单版（单层 1000×700·手感即网页那套）", "完整版（图层/缩放/选区）"))
            adoptBtn.toolTipText = "把网页画布（合成结果）整张收进当前的漫画页，作为一个绘画层"
            bar.add(javax.swing.JLabel("网页："))
            bar.add(pageBox)
            bar.add(adoptBtn)
            bar.add(reloadBtn)
            bar.add(status)
            f.add(bar, BorderLayout.NORTH)
            adoptBtn.addActionListener { requestAdopt() }
            reloadBtn.addActionListener { reload() }
            pageBox.addActionListener {
                val next = if (pageBox.selectedIndex == 0) PAGE_SIMPLE else PAGE_FULL
                if (next != pageName) {
                    pageName = next
                    logInfo("WebBrush", "[WebBrush] 切网页 → $next")
                    reload()
                }
            }
            f.addWindowListener(
                object : java.awt.event.WindowAdapter() {
                    override fun windowClosing(e: java.awt.event.WindowEvent) {
                        // 关窗 = 只收这一份浏览器（CEF 内核留着 ✓，下次开窗就快了 ✓）。
                        closeBrowser()
                        f.isVisible = false
                    }
                },
            )
            f.setSize(1500, 1000)
            f.setLocationRelativeTo(null)
            frame = f
        }
        return f to status
    }

    private suspend fun createBrowserIntoWindow() {
        val f = frame ?: return
        if (browser != null) {
            withContext(Dispatchers.Main) { f.isVisible = true; f.toFront() }
            return
        }
        val url = htmlUrl(pageName)
        if (url == null) {
            logInfo("WebBrush", "[WebBrush] 找不到 $pageName（既不在镜像里也不在 docs 里）")
            return
        }
        val c = KCEF.newClientOrNullBlocking()
        if (c == null) {
            logInfo("WebBrush", "[WebBrush] newClient 返回 null（内核没就绪？）")
            return
        }
        c.addDisplayHandler(consoleHandler())
        val b = c.createBrowser(url, CefRendering.DEFAULT, false)
        client = c
        browser = b
        withContext(Dispatchers.Main) {
            f.add(b.uiComponent, BorderLayout.CENTER)
            f.revalidate()
            f.isVisible = true
            f.toFront()
        }
        // 等页面加载完再动手 ✓（两个页面各自的"就绪"探针不同，见下面的 JS ✓）。
        scope.launch {
            kotlinx.coroutines.delay(4000)
            b.executeJavaScript(readyProbeJs(), url, 0)
            // 第 ㉛ 批：装层 + 自动写回（独立窗口这条路也照装 ✓ —— 从哪条路进来都一样 ✓）
            kotlinx.coroutines.delay(1500)
            installIntoPage()
        }
    }

    /**
     * 页面就绪探针 ✓（**不改这两张网页的任何一个字** ✗ —— 只用它们自己已有的东西 ✓）。
     *
     *  · **完整版**：有 `setupDoc(w,h)` ✓ ⇒ 把画布按当前页尺寸建（采用回来 1:1 ✓），
     *    然后报 `DOCW×DOCH` ✓；
     *  · **简单版**：**没有** `setupDoc` ✗、画布是页里写死的 1000×700 ✓ ⇒ 什么都不动 ✓，
     *    只报它自己的 `DOC.w×DOC.h` ✓（用户点名它的手感好 ✓，就别去动它的尺寸 ✗）。
     */
    /**
     * **"页面里只许有画布"的注入 JS**（用户口径：「加载时不要出现 UI，直接就是画布」✓）。
     *
     * 做法：把 `body` 的**所有顶层子节点**（以及它们的祖先链）藏起来，只留画布所在的那条链 ✓，
     * 再把画布**铺满视口、白底** ✓。用 `display:none` 而不是删节点 ✓（删了会把页面自己的
     * 引用搞脏 ✗，而且它后面还会重建 ✗）。
     *
     * ⚠️ **一次注入不够** ✗ —— 页面自己后面还会建 DOM（面板、HUD、提示行…）⇒
     * Kotlin 侧每 300ms 再注一次（见 [createBrowserIntoWindow] 里那个循环 ✓），
     * 所以"先闪一下 UI"也压得住 ✓。
     */
    private fun hideUiJs(): String = """
        (function(){
          try{
            var cv = document.getElementById('cv');
            if (!cv) return 'HIDE:no-canvas';
            // 只保留"从 body 到 canvas 的那条祖先链"，其余顶层子节点一律藏起来 ✓
            var chain = [];
            var n = cv;
            while (n && n !== document.body){ chain.push(n); n = n.parentElement; }
            var kids = document.body.children;
            for (var i = 0; i < kids.length; i++){
              var k = kids[i];
              if (chain.indexOf(k) < 0) k.style.display = 'none';
            }
            for (var j = 0; j < chain.length; j++){
              var e = chain[j];
              if (e === cv) continue;
              e.style.display = 'block';
              e.style.position = 'static';
              e.style.margin = '0'; e.style.padding = '0';
              e.style.width = '100%'; e.style.height = '100%';
              e.style.overflow = 'hidden';
              e.style.background = '#fff';
            }
            document.documentElement.style.background = '#4A4A4A';
            document.body.style.margin = '0';
            document.body.style.padding = '0';
            document.body.style.background = '#4A4A4A';
            document.body.style.overflow = 'hidden';
            cv.style.display = 'block';
            // ★ 用户：「画布的背景换成灰色」✓ —— 画布**元素**的底色 = 灰（= 纸外面的那圈背景 ✓），
            //   纸本身仍然是网页自己画的白（`flushView` 里是先铺白再叠合成 ✓）⇒ 灰底 + 白纸 ✓。
            cv.style.background = '#4A4A4A';
            return 'HIDE:ok';
          }catch(e){ return 'HIDE-FAIL:'+e.message; }
        })();
    """.trimIndent()

    /**
     * **框选模式**（用户 2026-09-20：「我的框选生图怎么实现」✓）。
     *
     * 为什么框选必须**在网页里**做 ✗（不能在软件侧盖一层）：画布是 `SwingPanel` 里的**重组件**，
     * 它会把鼠标事件吃掉 ⇒ Compose 盖在上面的东西既收不到事件、也画不到它前面 ✓（同一个原因
     * 让我们当初只能做独立窗口）。
     *
     * 做法：注入一小段监听（**不改网页文件**）——拖框时画半透明矩形，抬笔把
     * **画布原始像素**坐标报回来：`BOX:x,y,w,h` ✓（用页面自己的 `docXY()` ⇒ 缩放/平移自动对 ✓）。
     * 坐标出去之后：生成那一步用**软件已有的** `maskFocusRect` + `FocusedInpaint.plan` 链 ✓。
     */
    fun setBoxSelect(on: Boolean) {
        val b = browser ?: return
        b.executeJavaScript(boxSelectJs(on), htmlUrl(pageName) ?: "", 0)
        logInfo("WebBrush", "[WebBrush] 框选模式 on=$on")
    }

    private fun boxSelectJs(on: Boolean): String = """
        (function(){
          try{
            var cv = document.getElementById('cv');
            if (!cv) return 'BOX-FAIL:no-canvas';
            if (${if (on) "false" else "true"}){            // 关掉：摘监听 + 抹掉临时框
              if (window.__naiBox){ window.__naiBox = null; }
              if (window.__naiBoxDraw){ window.__naiBoxDraw(); }
              console.log('BOX:off');
              return 'BOX:off';
            }
            if (window.__naiBox) return 'BOX:already';
            var box = { x0:0, y0:0, x1:0, y1:0, down:false };
            window.__naiBox = box;
            var ctx2 = cv.getContext('2d');
            var snap = null;
            var toDoc = function(e){
              var r = cv.getBoundingClientRect();
              var k = (r.width || (typeof DOC !== 'undefined' ? DOC.w : cv.width)) /
                      (typeof DOC !== 'undefined' ? DOC.w : cv.width);
              return [ (e.clientX - r.left) / k, (e.clientY - r.top) / k ];
            };
            var draw = function(){
              if (snap) ctx2.putImageData(snap, 0, 0);
              if (!box.down) return;
              ctx2.save();
              ctx2.globalCompositeOperation = 'source-over';
              ctx2.strokeStyle = '#1E88E5'; ctx2.lineWidth = Math.max(1, 2 / (cv.getBoundingClientRect().width / ((typeof DOC!=='undefined'?DOC.w:cv.width) || 1)));
              ctx2.setLineDash([6, 4]);
              ctx2.strokeRect(Math.min(box.x0,box.x1), Math.min(box.y0,box.y1),
                              Math.abs(box.x1-box.x0), Math.abs(box.y1-box.y0));
              ctx2.restore();
            };
            window.__naiBoxDraw = function(){ if (snap){ ctx2.putImageData(snap, 0, 0); } snap = null; };
            var down = function(e){
              var p = toDoc(e); box.x0 = p[0]; box.y0 = p[1]; box.x1 = p[0]; box.y1 = p[1];
              box.down = true;
              snap = ctx2.getImageData(0, 0, cv.width, cv.height);
              e.preventDefault();
            };
            var move = function(e){ if (!box.down) return; var p = toDoc(e); box.x1 = p[0]; box.y1 = p[1]; draw(); e.preventDefault(); };
            var up = function(e){
              if (!box.down) return; box.down = false;
              var x = Math.min(box.x0,box.x1), y = Math.min(box.y0,box.y1);
              var w = Math.abs(box.x1-box.x0), h = Math.abs(box.y1-box.y0);
              console.log('BOX:' + Math.round(x) + ',' + Math.round(y) + ',' + Math.round(w) + ',' + Math.round(h));
              e.preventDefault();
            };
            cv.addEventListener('pointerdown', down, true);
            cv.addEventListener('pointermove', move, true);
            cv.addEventListener('pointerup', up, true);
            console.log('BOX:on');
            return 'BOX:on';
          }catch(e){ return 'BOX-FAIL:'+e.message; }
        })();
    """.trimIndent()

    private fun readyProbeJs(): String = """
        (function(){
          try{
            if (typeof setupDoc === 'function'){
              setupDoc($docWidth,$docHeight);
              console.log('READY:full '+DOCW+'x'+DOCH+' dpr='+window.devicePixelRatio);
            } else if (typeof DOC !== 'undefined' && DOC && DOC.w){
              console.log('READY:simple '+DOC.w+'x'+DOC.h+' dpr='+window.devicePixelRatio);
            } else {
              console.log('READY:unknown dpr='+window.devicePixelRatio);
            }
            /* ---- ㉛：**只留那张白画布，页面上所有按钮/面板全删** ✓ ----
               用户口径：「画布区只留下白色的画布，按钮全删」✓。
               ⚠️ **不改网页文件一个字** ✗ —— 只是把 DOM 里除 canvas 之外的东西摘掉 ✓；
                 画布自己的 `ctx` / `DOC` / 事件监听都挂在 canvas 上，摘别的元素不影响它 ✓
                 （`note()` 拿的是载入时缓存的节点 ✓ 脱开 DOM 也照样能写 ✓）。 */
            var cv = document.getElementById('cv');
            /* ⚠️⚠️ 用户 2026-09-20 口径：「**加载时不要出现 UI，直接就是画布**」✓
               ⇒ 页面那套面板/按钮**一律不许露面** ✗；这里只留一条参数探针（诊断用 ✓）。
               真正"反复隐藏"的是 Kotlin 侧那个循环（见 [hideUiJs] ✓）——它每 300ms 注入一次，
               所以**页面后面再建什么 DOM 也藏得住** ✓，也不会有"先闪一下 UI"✗。 */
            try{
              console.log('PARAMS: page=$pageName size=' + (typeof P !== 'undefined' ? P.size : '?') +
                ' spacing=' + (typeof P !== 'undefined' ? P.Spacing : '?') +
                ' scatter=' + (typeof P !== 'undefined' ? P.Scattering : '?') +
                ' count=' + (typeof P !== 'undefined' ? P.Count : '?') +
                ' stabilize=' + (typeof P !== 'undefined' ? P.stabilize : '?'));
            }catch(e){ console.log('PARAMS-FAIL:'+e.message); }
            if (cv){
              console.log('CLEAN:skipped canvas='+cv.width+'x'+cv.height);
            } else {
              console.log('CLEAN:no-canvas');
            }
          }catch(e){ console.log('READY-FAIL:'+e.message); }
        })();
    """.trimIndent()

    /**
     * 「采用」用的导出 JS ✓ —— 两张网页**各自的**那张"最终图"：
     *  · 完整版：`compCv`（合成后的画布 ✓）；
     *  · 简单版：它只有一张 canvas（`DOC.cv` ✓）—— 照它自己 `savePng()` 的口径
     *    垫一层白底再导出 ✓（不然采用回来是"透明底 + 墨迹"✗，和网页上看到的不一样 ✗）。
     */
    private fun adoptJs(): String = """
        (function(){
          try{
            var url = null;
            if (typeof compCv !== 'undefined' && compCv){
              url = compCv.toDataURL('image/png');
            } else if (typeof DOC !== 'undefined' && DOC && DOC.cv){
              var out = document.createElement('canvas');
              out.width = DOC.w; out.height = DOC.h;
              var g = out.getContext('2d');
              g.setTransform(1,0,0,1,0,0);
              g.globalCompositeOperation = 'source-over';
              g.globalAlpha = 1;
              g.fillStyle = '#ffffff'; g.fillRect(0,0,DOC.w,DOC.h);
              g.drawImage(DOC.cv, 0, 0);
              url = out.toDataURL('image/png');
            }
            if (url){ console.log('PNG:' + url); }
            else { console.log('ADOPT-FAIL:no-canvas'); }
          }catch(e){ console.log('ADOPT-FAIL:'+e.message); }
        })();
    """.trimIndent()

    private fun closeBrowser() {
        runCatching { client?.dispose() }
        client = null
        browser = null
    }

    // -----------------------------------------------------------------------
    // 「采用」/ 重新加载
    // -----------------------------------------------------------------------

    /** 网页那边把结果打成 `PNG:<dataURL>` 从 console 送出来 ✓（这也是"只读它一张图"的全部协议 ✓）。 */
    private const val PNG_PREFIX = "PNG:"

    /**
     * **自检入口**：和窗口上那颗「采用到绘画层」**走的是同一个函数** ✓。
     *
     * 为什么留着它：这条链（按钮 → 网页导出 PNG → console 送出来 → base64 解 → 回传）
     * **没有窗口就验不了** ✗，而人又不可能每次都手点一遍 ✓ —— 探针
     * `com.kallan.naistudio.desktop.WebBrushProbeKt` 就是用它把整条链跑通的 ✓
     *（实测：inited → 页面加载 → `READY:1024x1024` → 采用 → png 字节数 ✓）。
     */
    internal fun adoptForSelfTest() = requestAdopt()

    private fun requestAdopt() {
        val b = browser
        if (b == null) {
            logInfo("WebBrush", "[WebBrush] 还没开好网页，先等等")
            return
        }
        // ⚠️ `executeJavaScript` **不给返回值** ✗ ⇒ 结果从 `console.log('PNG:…')` 送回来 ✓
        //（显示器那边收，见 [handlePng] ✓）。这也是"把图拿回来"的**全部协议** ✓。
        b.executeJavaScript(adoptJs(), htmlUrl(pageName) ?: "", 0)
    }

    private fun handlePng(dataUrl: String) {
        val comma = dataUrl.indexOf(',')
        if (comma <= 0) {
            logInfo("WebBrush", "[WebBrush] 采用失败：dataURL 不合法（len=${dataUrl.length}）")
            return
        }
        val bytes = runCatching { Base64.getDecoder().decode(dataUrl.substring(comma + 1)) }.getOrNull()
        if (bytes == null) {
            logInfo("WebBrush", "[WebBrush] 采用失败：base64 解不开（len=${dataUrl.length}）")
            return
        }
        logInfo("WebBrush", "[WebBrush] 收到网页图 png=${bytes.size}B")
        lastAutoAdoptBytes = bytes.size
        val action = adopt ?: return
        // 落到 AppState 那边要在 UI 线程上做（它会动 Compose 状态 + 落盘 ✓）。
        scope.launch {
            val ok = withContext(Dispatchers.Main) { runCatching { action(bytes) }.getOrDefault(false) }
            logInfo("WebBrush", "[WebBrush] 采用结果 ok=$ok")
            withContext(Dispatchers.Main) {
                frame?.let { f ->
                    (f.contentPane.getComponent(0) as? JPanel)
                        ?.components?.filterIsInstance<JLabel>()
                        ?.firstOrNull()
                        ?.text = if (ok) "已采用到绘画层 ✓" else "采用失败 ✗（看日志 WebBrush）"
                }
            }
        }
    }

    private fun reload() {
        val b = browser ?: return
        val url = htmlUrl(pageName) ?: return
        b.loadURL(url)
        scope.launch {
            kotlinx.coroutines.delay(3000)
            b.executeJavaScript(readyProbeJs(), url, 0)
        }
    }

    // -----------------------------------------------------------------------
    // 路径
    // -----------------------------------------------------------------------

    /**
     * 镜像里的 `app\` 目录（`jpackage.app-path` 是 exe 的路径 ✓，它的上一级的 `app` ✓）。
     * 开发时（`gradle run` / 探针）**没有**这个目录 ⇒ null ✓（那一页就走仓库里的 `docs/` ✓）。
     */
    private fun imageAppDir(): File? {
        System.getProperty("compose.application.resources.dir")?.let { return File(it) }
        val exe = System.getProperty("jpackage.app-path") ?: return null
        val app = File(File(exe).parentFile, "app")
        return if (app.isDirectory) app else null
    }

    /** 网页那一页：镜像里那一份 → 用户数据目录那一份 → 仓库里的 `docs/`（开发时 ✓）。 */
    private fun htmlFile(name: String): File? {
        // ⚠️ 只找**相对位置**，不写机器绝对路径 ✗（`gradle run` / 双击 exe 的工作目录不同，
        //    但这几条覆盖得住 ✓）。
        // ⚠️ 用户数据目录那一条是**保险**：`jpackage.app-path` 万一没给（不是所有启动方式都有 ✓），
        //    镜像里那份就找不到 —— 多一条兜底，页面永远开得出来 ✓（交付时两边都放了一份 ✓）。
        val candidates = listOfNotNull(
            imageAppDir()?.let { File(it, name) },
            File(appDir(), name),
            File("docs/$name"),
            File("../docs/$name"),
        )
        return candidates.firstOrNull { it.isFile }
    }

    private fun htmlUrl(name: String): String? = htmlFile(name)?.let { it.toURI().toString() }
}
