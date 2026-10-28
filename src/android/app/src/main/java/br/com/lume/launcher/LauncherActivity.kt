package br.com.lume.launcher

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.content.Context
import android.content.ActivityNotFoundException
import android.net.Uri
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.*
import br.com.lume.launcher.core.LauncherRules
import br.com.lume.launcher.core.LocaleRules
import br.com.lume.launcher.core.MenuSelectGuard
import br.com.lume.launcher.core.NetworkStatus
import br.com.lume.launcher.core.ScreensaverRules
import org.json.JSONArray
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors
import android.content.pm.PackageManager
import android.os.CancellationSignal
import br.com.lume.launcher.core.SharedProgram

class LauncherActivity : Activity() {
    private companion object {
        val updateWorker = java.util.concurrent.ThreadPoolExecutor(1, 1, 15, java.util.concurrent.TimeUnit.SECONDS,
            java.util.concurrent.ArrayBlockingQueue(1),
            { task -> Thread(task, "lume-update").apply { isDaemon = true } },
            java.util.concurrent.ThreadPoolExecutor.AbortPolicy()).apply { allowCoreThreadTimeOut(true) }
    }
    private val prefs by lazy { getSharedPreferences("orbita", MODE_PRIVATE) }
    private val worker = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private var contentJob: java.util.concurrent.Future<*>? = null
    private var contentTimeout: Runnable? = null
    private var contentSignal: CancellationSignal? = null
    private var contentBusy = false
    private var contentGeneration = 0
    private var contentLoading = false
    private var contentSnapshot = TvContentCatalog.Snapshot(emptyList(), emptyList(), false)
    private var contentHost: FrameLayout? = null
    private var contentInfo: View? = null
    private var contentInfoIcon: ImageView? = null
    private var contentInfoTitle: TextView? = null
    private var contentInfoMeta: TextView? = null
    private var contentInfoDescription: TextView? = null
    private var scenePreview: View? = null
    private val contentDetailActions = mutableMapOf<String, (Boolean) -> Unit>()
    private var contentRenderGeneration = 0
    private var contentTab = "next"
    private var pendingContentEntry: String? = null
    private var restoreContentOnResume = false
    private val catalog by lazy { AppCatalog(this) }
    private val actions by lazy { DeviceActions(this) }
    private val artwork by lazy { ProgramArtworkBinder { active } }
    private val network by lazy { NetworkMonitor(this) { networkStatus = it; updateNetworkButton() } }
    private var networkStatus = NetworkStatus("", "")
    private var networkButton: TextView? = null
    private var clock: TextView? = null
    private var date: TextView? = null
    private lateinit var pageHost: FrameLayout
    private lateinit var wallpaper: ImageView
    private lateinit var cinemaLayer: View
    private lateinit var page: LinearLayout
    private var apps = emptyList<InstalledApp>()
    private var favoritePackages = emptyList<String>()
    private var movingPackage: String? = null
    private var moveOriginal: List<String>? = null
    private var currentPage = "home"
    private var pendingRestoredFocusTag: String? = null
    private var languageOriginTag: String? = null
    private var active = false
    private var loaded = false
    private var loading = false
    private var currentScene = -1
    @Volatile private var pageImageGeneration = 0
    @Volatile private var wallpaperGeneration = 0
    private var cinemaWasActive: Boolean? = null
    private var cinemaButton: TextView? = null
    private var atmosphereButton: TextView? = null
    private var dailyButton: TextView? = null
    private val navigationControls = mutableListOf<View>()
    private var openDialog: Dialog? = null
    private var updateDialog: Dialog? = null
    private var updateClient: GitHubUpdater? = null
    private var updateJob: java.util.concurrent.Future<*>? = null
    private var preparedUpdate: PreparedUpdate? = null
    private var updateGeneration = 0
    private var changingUpdateDialog = false
    private val updateDeadline = Runnable {
        if (updateClient != null && openDialog === updateDialog) {
            cancelUpdate()
            updateError(UpdateFailure(UpdateFailureReason.NETWORK))
        }
    }
    private var appMenu: PopupWindow? = null
    private var appMenuOrigin: View? = null
    private var selectionDownTime = Long.MIN_VALUE
    private var dialogOrigin: View? = null
    private val idle = Runnable {
        if (active && hasWindowFocus() && openDialog == null && appMenu == null && movingPackage == null && prefs.getBoolean("screensaver_enabled", true)) startScreensaver()
        else resetScreensaverTimer()
    }
    private val locale get() = AppLocales.formattingLocale(this)
    private data class Scene(val id: String, val title: String, val theme: String, val file: String, val thumb: String)
    private val scenes by lazy {
        val json = JSONArray(assets.open("scenes.json").bufferedReader().use { it.readText() })
        (0 until json.length()).map { i -> json.getJSONObject(i).let {
            Scene(it.getString("id"), LocalizedText.sceneTitle(this, i), LocalizedText.sceneTheme(this, i), it.getString("file"), it.getString("thumb"))
        } }
    }
    private val tick = object : Runnable {
        override fun run() { if (active) { refreshAmbient(); handler.postDelayed(this, 60000 - System.currentTimeMillis() % 60000) } }
    }
    private fun dp(value: Int) = GlassUi.dp(this, value)
    private fun s(id: Int, vararg args: Any) = getString(id, *args.map {
        if (it is String) android.text.BidiFormatter.getInstance(rtl).unicodeWrap(it) else it
    }.toTypedArray())
    private fun hour() = LocalTime.now().hour
    private fun cinema() = LauncherRules.cinemaActive(prefs.getString("cinema", "off"), hour())
    private fun appearance(): Triple<Int, Int, Boolean> {
        val on = cinema()
        return Triple(LauncherRules.transparency(prefs.getInt("transparency", 58), on),
            LauncherRules.reflections(prefs.getInt("reflections", 65), on),
            LauncherRules.animations(prefs.getBoolean("animations", true), on))
    }
    private fun style(view: View) {
        val tag = view.tag as? String
        val radius = when {
            view is HomeAppTile -> view.cornerRadiusDp
            tag?.startsWith("content-tab:") == true || tag?.startsWith("program:") == true || tag?.startsWith("platform:") == true -> 10f
            else -> GlassUi.CORNER_RADIUS_DP
        }
        GlassUi.focus(view, radius) { appearance().let { if (prefs.getBoolean("lightweight", false)) Triple(18, 0, false) else it } }
        if (tag?.startsWith("content-tab:") == true) {
            val glassFocus = view.onFocusChangeListener
            view.setOnFocusChangeListener { target, focused ->
                glassFocus?.onFocusChange(target, focused)
                if (focused && currentPage == "home") activateContentTab(tag.removePrefix("content-tab:"))
            }
        } else if (tag?.startsWith("program:") == true || tag?.startsWith("platform:") == true) {
            val glassFocus = view.onFocusChangeListener
            view.setOnFocusChangeListener { target, focused ->
                glassFocus?.onFocusChange(target, focused)
                if (focused) contentDetailActions[tag]?.invoke(true)
            }
        }
    }

    private fun setContentDetailsVisible(visible: Boolean) {
        val info = contentInfo ?: return
        val target = if (visible) View.VISIBLE else View.GONE
        if (info.visibility == target) return
        info.visibility = target
        scenePreview?.visibility = if (visible) View.GONE else View.VISIBLE
    }

    private fun updateContentPreview() {
        val host = contentHost ?: return
        val previewTag = if (currentPage == "home" && currentFocus?.tag == "content-tab:$contentTab")
            LauncherRules.contentEntryTag(prefs.getString("content_focus_$contentTab", null), contentDetailActions.keys, contentLoading)
        else null
        contentDetailActions.keys.forEach { tag ->
            host.findViewWithTag<View>(tag)?.let { card ->
                val preview = tag == previewTag
                if (card.isActivated != preview) { card.isActivated = preview; style(card) }
            }
        }
        previewTag?.let { contentDetailActions[it]?.invoke(false) }
    }

    private fun activateContentTab(key: String, enterCards: Boolean = false) {
        pendingContentEntry = if (enterCards) key else null
        if (contentTab != key) {
            contentTab = key
            prefs.edit().putString("content_tab", key).apply()
            renderContent()
        }
        val host = contentHost ?: return
        val generation = contentRenderGeneration
        host.post {
            if (contentHost === host && generation == contentRenderGeneration) enterRequestedContent(host)
        }
    }
    private fun enterRequestedContent(host: View) {
        if (!active || currentPage != "home" || host !== contentHost) return
        val targetTag = LauncherRules.contentEntryTag(prefs.getString("content_focus_$contentTab", null), contentDetailActions.keys, contentLoading)
        val target = targetTag?.let { host.findViewWithTag<View>(it) }
        val emptyAction = if (target == null && !contentLoading) host.findViewWithTag<View>("content-connect") else null
        val explicitEmptyEntry = emptyAction != null && pendingContentEntry == contentTab && currentFocus?.tag == "content-tab:$contentTab"
        if (explicitEmptyEntry || LauncherRules.canEnterContent(pendingContentEntry, contentTab, currentFocus?.tag as? String, target?.tag as? String)) {
            pendingContentEntry = null
            (target ?: emptyAction)?.requestFocus()
        }
    }
    private fun enterActiveContent(enterCards: Boolean = false): Boolean {
        if (!active || currentPage != "home") return false
        val header = page.findViewWithTag<View>("content-tab:$contentTab") ?: return false
        if (!header.requestFocus()) return false
        if (enterCards) activateContentTab(contentTab, true)
        return true
    }
    private fun text(value: String, size: Float = 16f, secondary: Boolean = false) = GlassUi.text(this, value, size, secondary)
    private fun button(value: String, action: () -> Unit): TextView = GlassUi.button(this, value, action).also { it.id = View.generateViewId(); style(it) }
    private fun navigation(value: String, icon: Int, action: () -> Unit) = button(value, action).apply {
        textSize = 14f; gravity = Gravity.CENTER_VERTICAL; maxLines = 1; setPadding(dp(10), dp(8), dp(10), dp(8))
        setAutoSizeTextTypeUniformWithConfiguration(10, 14, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
        setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0); compoundDrawablePadding = dp(8)
        navigationControls.add(this)
        setOnKeyListener { view, key, event ->
            if (view === navigationControls.firstOrNull() && currentPage == "home" &&
                logicalKey(key) == KeyEvent.KEYCODE_DPAD_RIGHT && event.action == KeyEvent.ACTION_DOWN)
                page.findViewWithTag<View>("content-tab:next")?.requestFocus() ?: false
            else false
        }
    }
    private val rtl: Boolean get() = resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
    private fun logicalKey(code: Int) = br.com.lume.launcher.core.LocaleRules.navigationKey(code, rtl)
    private fun focusStart(view: View, id: Int) { if (rtl) view.nextFocusRightId = id else view.nextFocusLeftId = id }
    private fun focusEnd(view: View, id: Int) { if (rtl) view.nextFocusLeftId = id else view.nextFocusRightId = id }
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun gap(parent: LinearLayout, amount: Int = 8) { parent.addView(View(this), LinearLayout.LayoutParams(if (parent.orientation == LinearLayout.HORIZONTAL) dp(amount) else 1, if (parent.orientation == LinearLayout.VERTICAL) dp(amount) else 1)) }
    private fun safePanel(view: View) { view.tag = "glass"; val a = appearance(); view.background = GlassUi.panel(if (prefs.getBoolean("lightweight", false)) 18 else a.first, if (prefs.getBoolean("lightweight", false)) 0 else a.second) }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocales.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        networkStatus = NetworkStatus(s(R.string.common_network), s(R.string.common_checking_internet))
        currentPage = savedInstanceState?.getString("state.page")?.takeIf { it in setOf("home", "apps", "scenes") } ?: "home"
        contentTab = (savedInstanceState?.getString("state.content_tab") ?: prefs.getString("content_tab", "next"))
            .takeIf { it == "suggestions" } ?: "next"
        pendingRestoredFocusTag = savedInstanceState?.getString("state.focus")?.takeIf { it.length <= 256 && it.none { char -> char.isISOControl() } }
        buildShell()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("state.page", currentPage)
        outState.putString("state.content_tab", contentTab)
        outState.putString("state.focus", languageOriginTag ?: currentFocus?.tag as? String)
        super.onSaveInstanceState(outState)
    }

    private fun restoreInstanceFocus() {
        val tag = pendingRestoredFocusTag ?: return
        if (!active || isDestroyed) return
        val target = window.decorView.findViewWithTag<View>(tag) ?: return
        if (target.isShown && target.isAttachedToWindow && target.requestFocus()) pendingRestoredFocusTag = null
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            pendingRestoredFocusTag = null
            closeDialog()
            showPage("home")
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) resetScreensaverTimer() else handler.removeCallbacks(idle)
        if (hasFocus && active) { updateContentPreview(); artwork.refresh() }
        if (hasFocus) window.decorView.windowInsetsController?.apply {
            hide(WindowInsets.Type.systemBars())
            systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun buildShell() {
        val frame = FrameLayout(this)
        wallpaper = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
        frame.addView(wallpaper, FrameLayout.LayoutParams(-1, -1))
        frame.addView(View(this).apply { setBackgroundColor(Color.argb(105, 3, 10, 20)); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }, FrameLayout.LayoutParams(-1, -1))
        cinemaLayer = View(this).apply { setBackgroundColor(Color.argb(128, 21, 13, 15)); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
        frame.addView(cinemaLayer, FrameLayout.LayoutParams(-1, -1))
        val shell = row().apply { gravity = Gravity.TOP; setPadding(dp(24), dp(20), dp(24), dp(16)) }
        frame.addView(shell, FrameLayout.LayoutParams(-1, -1))
        val rail = column()
        shell.addView(rail, LinearLayout.LayoutParams(dp(if (resources.configuration.screenWidthDp >= 1100) 184 else 144), -1))
        val brand = column().apply { setPadding(dp(8), 0, 0, 0) }
        brand.addView(text("lume", 32f).apply { typeface = Typeface.create("sans-serif-light", Typeface.NORMAL) })
        brand.addView(text("Ó R B I T A", 10f, true))
        rail.addView(brand, LinearLayout.LayoutParams(-1, dp(64))); gap(rail, 10)
        val sidebar = column().apply { gravity = Gravity.TOP; setPadding(dp(10), dp(12), dp(10), dp(12)); safePanel(this) }
        rail.addView(sidebar, LinearLayout.LayoutParams(-1, 0, 1f))
        listOf(s(R.string.activity_nav_home) to "home", s(R.string.activity_nav_apps) to "apps", s(R.string.activity_nav_scenes) to "scenes").forEach { (name, destination) ->
            val icon = when (destination) { "home" -> R.drawable.ic_home; "apps" -> R.drawable.ic_apps; else -> R.drawable.ic_scenes }
            sidebar.addView(navigation(name, icon) { showPage(destination) }.apply { tag = "navigation:$destination" }, LinearLayout.LayoutParams(-1, dp(48))); gap(sidebar, 8)
        }
        sidebar.addView(navigation(s(R.string.activity_cinema_title), R.drawable.ic_cinema) { showCinema() }, LinearLayout.LayoutParams(-1, dp(48)))
        sidebar.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))
        sidebar.addView(text(s(R.string.activity_sidebar_motto), 10f, true).apply { setPadding(dp(8), 0, 0, 0) })
        gap(shell, 20)
        val main = column().apply { setPadding(dp(4), 0, dp(4), 0) }
        shell.addView(main, LinearLayout.LayoutParams(0, -1, 1f))
        val top = row()
        val time = column()
        clock = text("", 28f).apply { typeface = Typeface.create("sans-serif-light", Typeface.NORMAL) }
        date = text("", 12f, true)
        time.addView(clock); time.addView(date)
        top.addView(time, LinearLayout.LayoutParams(0, -2, 1f))
        networkButton = button("") { openNetwork() }.apply {
            textSize = 14f; maxLines = 2; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(4), dp(12), dp(4))
            setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_network, 0, 0, 0); compoundDrawablePadding = dp(8)
        }
        top.addView(networkButton, LinearLayout.LayoutParams(dp(195), dp(48))); gap(top, 8)
        updateNetworkButton()
        val gear = ImageButton(this).apply { id = View.generateViewId(); tag = "launcher-settings"; setImageResource(R.drawable.ic_settings); scaleType = ImageView.ScaleType.CENTER; contentDescription = s(R.string.activity_settings_a11y); setOnClickListener { showSettings() }; style(this) }
        top.addView(gear, LinearLayout.LayoutParams(dp(48), dp(48)))
        main.addView(top, LinearLayout.LayoutParams(-1, dp(64))); gap(main, 10)
        pageHost = FrameLayout(this)
        main.addView(pageHost, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(frame)
        frame.viewTreeObserver.addOnGlobalFocusChangeListener { _, focused ->
            val tag = focused?.tag as? String
            if (focused != null && tag != "content-tab:$pendingContentEntry") pendingContentEntry = null
            if (focused != null && tag?.startsWith("program:") != true && tag?.startsWith("platform:") != true)
                setContentDetailsVisible(false)
            updateContentPreview()
        }
        renderPage()
        refreshAmbient()
    }

    override fun onResume() {
        super.onResume(); active = true
        restoreContentOnResume = currentPage == "home" && openDialog == null && appMenu == null && movingPackage == null &&
            prefs.getString("content_focus_$contentTab", null) != null
        setHomeBordersActive(window.decorView, true); network.start(); loadApps(); handler.removeCallbacks(tick); tick.run()
        resetScreensaverTimer()
        handler.post { if (active) artwork.refresh() }
        preparedUpdate?.takeIf { openDialog === updateDialog }?.let { showPreparedUpdate(it) }
        UpdateInstaller.reconcile(this) {
            if (active && !isDestroyed) {
                if (UpdateInstaller.consumeInstallSuccess(this)) message(s(R.string.activity_update_install_success))
                if (UpdateInstaller.consumeHomeWarning(this)) showUpdateHomeWarning()
            }
        }
    }
    override fun onPause() {
        restoreContentOnResume = false
        cancelContent()
        closeAppMenu(false)
        active = false; network.stop(); handler.removeCallbacks(tick)
        artwork.pause()
        handler.removeCallbacks(idle)
        setHomeBordersActive(window.decorView, false)
        if (movingPackage != null) finishMove(false, false)
        super.onPause()
    }
    override fun onDestroy() { active = false; pageImageGeneration++; wallpaperGeneration++; cancelContent(); cancelUpdate(); artwork.dispose(); handler.removeCallbacks(idle); setHomeBordersActive(window.decorView, false); closeAppMenu(false); openDialog?.dismiss(); worker.shutdownNow(); super.onDestroy() }

    override fun onUserInteraction() { super.onUserInteraction(); pendingRestoredFocusTag = null; restoreContentOnResume = false; resetScreensaverTimer() }

    private fun resetScreensaverTimer() {
        handler.removeCallbacks(idle)
        if (active && hasWindowFocus() && prefs.getBoolean("screensaver_enabled", true))
            handler.postDelayed(idle, ScreensaverRules.idleMillis(prefs.getInt("screensaver_idle_minutes", 2)))
    }

    private fun startScreensaver() {
        if (!active) return
        handler.removeCallbacks(idle)
        if (!actions.openScreensaver()) { message(s(R.string.activity_screensaver_start_failed)); resetScreensaverTimer() }
    }

    private fun setHomeBordersActive(view: View, enabled: Boolean) {
        if (view is HomeAppTile) view.setActivityActive(enabled)
        if (view is ViewGroup) for (i in 0 until view.childCount) setHomeBordersActive(view.getChildAt(i), enabled)
    }

    private fun loadApps() {
        if (loading) return
        loading = true
        worker.execute {
            val result = runCatching { catalog.load() }
            handler.post {
                loading = false
                if (isDestroyed) return@post
                result.onSuccess { found ->
                    val changed = found != apps || !loaded
                    apps = found; loaded = true
                    val saved = prefs.getString("favorites", null)
                    if (movingPackage == null || changed) {
                        if (movingPackage != null) {
                            movingPackage = null; moveOriginal = null
                            message(s(R.string.activity_apps_changed_move_canceled))
                        }
                        favoritePackages = if (saved == null) apps.take(8).map { it.packageName }
                            else LauncherRules.validFavorites(saved.split('|'), apps.map { it.packageName }.toSet())
                    }
                    if (changed) renderPage()
                    loadContent()
                    if (restoreContentOnResume && active && currentPage == "home" && openDialog == null && appMenu == null && movingPackage == null) {
                        restoreContentOnResume = false
                        if (currentFocus?.tag != prefs.getString("content_focus_$contentTab", null)) enterActiveContent()
                    }
                }.onFailure { message(s(R.string.activity_apps_read_failed)) }
            }
        }
    }

    private fun firstControl(view: View): View? {
        if (view.visibility != View.VISIBLE || !view.isEnabled) return null
        if (view.isFocusable) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) firstControl(view.getChildAt(i))?.let { return it }
        return null
    }
    private fun showPage(destination: String) {
        currentPage = destination; renderPage(null)
        val firstApp = if (destination == "apps") apps.firstOrNull()?.let { page.findViewWithTag<View>("app:${it.packageName}") } else null
        val activeHeader = if (destination == "home") page.findViewWithTag<View>("content-tab:$contentTab") else null
        (firstApp ?: activeHeader ?: firstControl(page))?.requestFocus()
    }
    private fun renderPage(focusTag: String? = currentFocus?.tag as? String) {
        pendingContentEntry = null
        pageImageGeneration++
        closeAppMenu(false)
        artwork.clearBindings()
        contentDetailActions.clear(); contentInfo = null; scenePreview = null
        contentInfoIcon = null; contentInfoTitle = null; contentInfoMeta = null; contentInfoDescription = null
        pageHost.removeAllViews(); cinemaButton = null; atmosphereButton = null; dailyButton = null; contentHost = null
        page = column().apply { setPadding(dp(6), dp(6), dp(6), dp(8)) }
        if (currentPage == "home") {
            pageHost.addView(page, FrameLayout.LayoutParams(-1, -1))
            home(focusTag)
        } else {
            val scroll = ScrollView(this).apply { isFillViewport = true; isVerticalScrollBarEnabled = false }
            scroll.addView(page, FrameLayout.LayoutParams(-1, -2))
            pageHost.addView(scroll, FrameLayout.LayoutParams(-1, -1))
            if (currentPage == "apps") library() else gallery()
        }
        firstControl(page)?.let { target ->
            if (target.id == View.NO_ID) target.id = View.generateViewId()
            val entry = if (currentPage == "home") page.findViewWithTag<View>("content-tab:next") ?: target else target
            navigationControls.forEach { focusEnd(it, entry.id) }
            focusStart(target, navigationControls[when (currentPage) { "apps" -> 1; "scenes" -> 2; else -> 0 }].id)
        }
        if (focusTag != null && (focusTag.startsWith("app:") || focusTag == "include-apps")) {
            val renderedPage = page
            pageHost.post { if (!isDestroyed && page === renderedPage) renderedPage.findViewWithTag<View>(focusTag)?.requestFocus() }
        }
        pageHost.post { restoreInstanceFocus() }
    }

    private fun home(focusTag: String?) {
        val hero = row().apply { gravity = Gravity.TOP }
        val welcome = column().apply { safePanel(this); setPadding(dp(20), dp(12), dp(20), dp(8)); gravity = Gravity.CENTER_VERTICAL }
        welcome.setPadding(dp(8), dp(6), dp(8), dp(6))
        val tabs = row()
        listOf("next" to s(R.string.activity_continue_watching), "suggestions" to s(R.string.activity_suggestions)).forEach { (key, label) ->
            tabs.addView(button(label) { activateContentTab(key, true) }.apply {
                textSize = 13f; minHeight = dp(32); minimumHeight = dp(32); setPadding(dp(8), dp(2), dp(8), dp(2))
                tag = "content-tab:$key"
                style(this)
                setOnKeyListener { _, code, event ->
                    if (event.action != KeyEvent.ACTION_DOWN) false
                    else when (logicalKey(code)) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> if (key == "next") navigationControls.first().requestFocus()
                            else page.findViewWithTag<View>("content-tab:next")?.requestFocus() ?: false
                        KeyEvent.KEYCODE_DPAD_RIGHT -> if (key == "next") page.findViewWithTag<View>("content-tab:suggestions")?.requestFocus() ?: false
                            else cinemaButton?.requestFocus() ?: false
                        KeyEvent.KEYCODE_DPAD_DOWN -> { activateContentTab(key, true); true }
                        else -> false
                    }
                }
                isSelected = key == contentTab
                setTextColor(if (isSelected) Color.rgb(167, 226, 255) else Color.rgb(193, 206, 215))
                typeface = Typeface.create("sans-serif", if (isSelected) Typeface.BOLD else Typeface.NORMAL)
            }, LinearLayout.LayoutParams(0, dp(32), 1f))
        }
        welcome.addView(tabs); gap(welcome, 4)
        contentHost = FrameLayout(this)
        welcome.addView(contentHost, LinearLayout.LayoutParams(-1, 0, 1f))
        val info = row().apply { visibility = View.GONE; setPadding(dp(8), dp(6), dp(8), 0); gravity = Gravity.TOP }
        contentInfoIcon = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
        info.addView(contentInfoIcon, LinearLayout.LayoutParams(dp(40), dp(40)).apply { topMargin = dp(4) }); gap(info, 10)
        val infoText = column()
        contentInfoTitle = text("", 17f).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
        contentInfoMeta = text("", 11f, true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
        contentInfoDescription = text("", 12f).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END }
        infoText.addView(contentInfoTitle, LinearLayout.LayoutParams(-1, dp(24)))
        infoText.addView(contentInfoMeta, LinearLayout.LayoutParams(-1, dp(17)))
        infoText.addView(contentInfoDescription, LinearLayout.LayoutParams(-1, dp(32)))
        info.addView(infoText, LinearLayout.LayoutParams(0, -1, 1f))
        contentInfo = info; welcome.addView(info, LinearLayout.LayoutParams(-1, dp(80)))
        hero.addView(welcome, LinearLayout.LayoutParams(0, -1, 1f)); gap(hero, 12)
        val widgets = column()
        cinemaButton = button(cinemaLabel()) { toggleCinema() }.apply { gravity = Gravity.CENTER_VERTICAL; textSize = 12f; maxLines = 3; setPadding(dp(12), dp(6), dp(12), dp(6)) }
        cinemaButton?.setOnKeyListener { _, code, event ->
            if (logicalKey(code) == KeyEvent.KEYCODE_DPAD_LEFT && event.action == KeyEvent.ACTION_DOWN)
                page.findViewWithTag<View>("content-tab:suggestions")?.requestFocus() ?: false
            else false
        }
        widgets.addView(cinemaButton, LinearLayout.LayoutParams(-1, 0, 1f)); gap(widgets, 8)
        atmosphereButton = button(s(if (prefs.getBoolean("daily", true)) R.string.activity_daily_atmosphere else R.string.activity_manual_atmosphere, scenes[sceneIndex()].title)) { showPage("scenes") }.apply { textSize = 12f; maxLines = 3; setPadding(dp(12), dp(6), dp(12), dp(6)) }
        widgets.addView(atmosphereButton, LinearLayout.LayoutParams(-1, 0, 1f))
        hero.addView(widgets, LinearLayout.LayoutParams(dp(152), -1))
        page.addView(hero, LinearLayout.LayoutParams(-1, 0, 1f)); gap(page, 8)

        val favoritesPanel = column().apply { safePanel(this); setPadding(dp(8), dp(4), dp(8), dp(4)) }
        val heading = row()
        val favoriteHeading = if (movingPackage == null) text(s(R.string.activity_favorites_heading), 14f)
            else text(s(R.string.activity_move_hint), 12f).apply { maxLines = 2; contentDescription = s(R.string.activity_move_a11y) }
        heading.addView(favoriteHeading, LinearLayout.LayoutParams(0, -2, 1f))
        heading.addView(button(s(R.string.activity_include_apps)) { showPage("apps") }.apply { tag = "include-apps"; textSize = 11f; minHeight = dp(28); minimumHeight = dp(28); setPadding(dp(10), dp(3), dp(10), dp(3)) }, LinearLayout.LayoutParams(-2, dp(28)))
        favoritesPanel.addView(heading, LinearLayout.LayoutParams(-1, dp(28)))
        val favorites = favoritePackages.mapNotNull { key -> apps.find { it.packageName == key } }
        if (favorites.isEmpty()) favoritesPanel.addView(text(if (loaded) s(R.string.activity_favorites_empty) else s(R.string.activity_apps_loading), 14f, true), LinearLayout.LayoutParams(-1, dp(80)))
        else {
            val strip = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; isFocusable = false; isFillViewport = false; clipToPadding = false }
            val line = row().apply { clipChildren = false; clipToPadding = false; setPadding(dp(6), dp(4), dp(6), 0) }
            strip.addView(line, FrameLayout.LayoutParams(-2, -1))
            var lastWidth = 0
            var pendingFocus = focusTag
            line.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                if (line.width > 0 && line.childCount > 0) {
                    pendingFocus?.let { line.findViewWithTag<View>(it)?.requestFocus() }
                    pendingFocus = null
                }
            }
            fun fill(width: Int) {
                if (width <= 0 || width == lastWidth) return
                lastWidth = width
                val focusedTag = currentFocus?.tag as? String
                line.removeAllViews()
                val innerWidth = (width - line.paddingLeft - line.paddingRight).coerceAtLeast(1)
                val slots = LauncherRules.favoriteSlots(innerWidth, dp(64), dp(8))
                val slotWidth = ((innerWidth - dp(8) * (slots - 1)) / slots).coerceAtLeast(1)
                favorites.forEachIndexed { index, app ->
                    if (index > 0) gap(line, 8)
                    line.addView(appCard(app, showFavorite = false), LinearLayout.LayoutParams(slotWidth, -1))
                }
                line.findViewWithTag<View>("app:${favorites.first().packageName}")?.let { first ->
                    focusStart(first, navigationControls.first().id)
                }
                if (focusedTag != null) line.findViewWithTag<View>(focusedTag)?.requestFocus()
            }
            strip.addOnLayoutChangeListener { _, left, _, right, _, _, _, _, _ -> fill(right - left - strip.paddingLeft - strip.paddingRight) }
            favoritesPanel.addView(strip, LinearLayout.LayoutParams(-1, dp(84)))
        }
        page.addView(favoritesPanel, LinearLayout.LayoutParams(-1, -2)); gap(page, 8)
        val previews = column(); scenePreview = previews
        val scenesHeading = row()
        scenesHeading.addView(text(s(R.string.activity_scenes_heading), 16f), LinearLayout.LayoutParams(0, -2, 1f))
        scenesHeading.addView(button(s(R.string.activity_view_all_scenes)) { showPage("scenes") }.apply { textSize = 12f; minHeight = dp(32); minimumHeight = dp(32); setPadding(dp(10), dp(4), dp(10), dp(4)) }, LinearLayout.LayoutParams(-2, dp(32)))
        previews.addView(scenesHeading, LinearLayout.LayoutParams(-1, dp(32)))
        val recommended = row()
        repeat(3) { offset ->
            val index = (sceneIndex() + offset * 7) % scenes.size
            recommended.addView(sceneCard(index, true), LinearLayout.LayoutParams(0, dp(68), 1f))
            if (offset < 2) gap(recommended, 10)
        }
        previews.addView(recommended, LinearLayout.LayoutParams(-1, dp(68)))
        page.addView(previews)
        renderContent()
    }

    private fun cancelContent() {
        contentGeneration++; contentLoading = false
        contentTimeout?.let { handler.removeCallbacks(it) }; contentTimeout = null
        val signal = contentSignal
        contentSignal = null; contentBusy = false
        contentJob?.cancel(true); contentJob = null; TvContentWorker.purgeCanceled()
        if (signal != null) TvContentCancellation.tryCancel { signal.cancel() }
    }

    private fun connectContent() {
        if (checkSelfPermission("android.permission.READ_TV_LISTINGS") != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf("android.permission.READ_TV_LISTINGS"), 82)
        } else { prefs.edit().putBoolean("shared_content", true).apply(); loadContent() }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 82) {
            val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
            prefs.edit().putBoolean("shared_content", granted).apply()
            if (!granted) message(s(R.string.activity_content_permission_denied))
            loadContent(); renderContent()
        }
    }

    private fun loadContent() {
        if (!active || !loaded || contentBusy || !prefs.getBoolean("shared_content", false)) return
        if (checkSelfPermission("android.permission.READ_TV_LISTINGS") != PackageManager.PERMISSION_GRANTED) {
            contentSnapshot = TvContentCatalog.Snapshot(emptyList(), emptyList(), true); renderContent(); return
        }
        val generation = ++contentGeneration
        val installed = apps.map { it.packageName }.toSet()
        val signal = CancellationSignal(); contentSignal = signal; contentBusy = true; contentLoading = true
        if (contentSnapshot.next.isEmpty() && contentSnapshot.suggestions.isEmpty()) renderContent()
        // Provider cancellation may block in Binder; the UI deadline must never share its executor.
        val deadline = Runnable {
            if (!isDestroyed && generation == contentGeneration && contentSignal === signal) {
                cancelContent(); contentSnapshot = TvContentCatalog.Snapshot(emptyList(), emptyList(), true); renderContent()
            }
        }
        contentTimeout = deadline; handler.postDelayed(deadline, 3000L)
        try { contentJob = TvContentWorker.submit(Runnable {
            val result = runCatching { TvContentCatalog(this).load(installed, signal) }
            handler.post {
                if (contentSignal !== signal) return@post
                handler.removeCallbacks(deadline); contentTimeout = null
                contentBusy = false; contentSignal = null; contentJob = null
                if (isDestroyed || !active) return@post
                if (generation != contentGeneration) return@post
                if (signal.isCanceled) return@post
                contentLoading = false
                val updated = result.getOrElse { TvContentCatalog.Snapshot(emptyList(), emptyList(), true) }
                if (updated != contentSnapshot || (contentSnapshot.next.isEmpty() && contentSnapshot.suggestions.isEmpty())) {
                    contentSnapshot = updated
                    renderContent()
                } else {
                    contentHost?.let { enterRequestedContent(it) }
                    updateContentPreview()
                    artwork.refresh()
                }
            }
        }) } catch (_: java.util.concurrent.RejectedExecutionException) {
            handler.removeCallbacks(deadline); contentTimeout = null; contentBusy = false; contentLoading = false; contentSignal = null
            contentSnapshot = TvContentCatalog.Snapshot(emptyList(), emptyList(), true); renderContent()
        }
    }

    private fun renderContent() {
        val host = contentHost ?: return
        setContentDetailsVisible(false)
        contentDetailActions.clear()
        val renderGeneration = ++contentRenderGeneration
        listOf("next", "suggestions").forEach { key ->
            page.findViewWithTag<TextView>("content-tab:$key")?.let { tab ->
                tab.isSelected = key == contentTab
                tab.setTextColor(if (tab.isSelected) Color.rgb(167, 226, 255) else Color.rgb(193, 206, 215))
                tab.typeface = Typeface.create("sans-serif", if (tab.isSelected) Typeface.BOLD else Typeface.NORMAL)
            }
        }
        val focusedTag = currentFocus?.tag as? String
        val restoreFocus = host.hasFocus()
        artwork.clearBindings()
        host.removeAllViews()
        fun finishContent() {
            host.post {
                if (contentHost !== host || renderGeneration != contentRenderGeneration || !active) return@post
                val first = firstControl(host)
                first?.let { focusStart(it, navigationControls.first().id) }
                listOf("next", "suggestions").forEach { key ->
                    page.findViewWithTag<View>("content-tab:$key")?.nextFocusDownId = first?.id ?: View.NO_ID
                }
                val selectedTab = page.findViewWithTag<View>("content-tab:$contentTab")
                if (selectedTab != null) {
                    page.findViewWithTag<View>("content-tab:next")?.let { entry -> navigationControls.forEach { focusEnd(it, entry.id) } }
                    fun returnToTab(view: View) {
                        if (view.isFocusable && view.isClickable) view.nextFocusUpId = selectedTab.id
                        if (view is ViewGroup) for (i in 0 until view.childCount) returnToTab(view.getChildAt(i))
                    }
                    returnToTab(host)
                }
                val focus = currentFocus
                if (restoreFocus && (focus == null || !focus.isAttachedToWindow)) (focusedTag?.let { host.findViewWithTag<View>(it) } ?: firstControl(host))?.requestFocus()
                enterRequestedContent(host)
                updateContentPreview()
                artwork.refresh()
                restoreInstanceFocus()
            }
        }
        val rows = if (contentTab == "next") contentSnapshot.next else contentSnapshot.suggestions
        val enabled = prefs.getBoolean("shared_content", false) && checkSelfPermission("android.permission.READ_TV_LISTINGS") == PackageManager.PERMISSION_GRANTED
        if (rows.isEmpty() && contentTab == "next") {
            val body = column()
            body.addView(text(when { contentLoading -> s(R.string.activity_content_loading); !enabled -> s(R.string.activity_content_connect_hint); contentSnapshot.unavailable -> s(R.string.activity_content_unavailable); else -> s(R.string.activity_content_no_progress) }, 12f, true).apply { maxLines = 2 })
            body.addView(button(if (enabled) s(R.string.activity_content_refresh) else s(R.string.activity_content_connect)) { connectContent() }.apply { textSize = 12f; setPadding(dp(8), dp(2), dp(8), dp(2)); tag = "content-connect" }, LinearLayout.LayoutParams(-1, dp(36)))
            host.addView(body, FrameLayout.LayoutParams(-1, -2)); finishContent(); return
        }
        val strip = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; isFocusable = false; clipToPadding = false }
        val line = row().apply { clipChildren = false; clipToPadding = false; setPadding(dp(12), dp(10), dp(12), dp(10)) }
        strip.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (host.width > 0 && host.height > 0) {
                val (horizontal, vertical) = LauncherRules.contentFocusInsets(host.width, host.height, dp(10))
                if (line.paddingLeft != horizontal || line.paddingTop != vertical)
                    line.setPadding(horizontal, vertical, horizontal, vertical)
            }
        }
        strip.addView(line, FrameLayout.LayoutParams(-2, -1)); host.addView(strip, FrameLayout.LayoutParams(-1, -1))
        strip.setOnScrollChangeListener { _, _, _, _, _ -> artwork.refresh() }
        fun card(app: InstalledApp, title: String, subtitle: String, tagValue: String, program: SharedProgram? = null, action: () -> Unit) {
            contentDetailActions[tagValue] = { remember ->
                val preference = "content_focus_$contentTab"
                if (remember && (!contentLoading || program != null) && !contentSnapshot.unavailable && prefs.getString(preference, null) != tagValue)
                    prefs.edit().putString(preference, tagValue).apply()
                contentInfoIcon?.setImageDrawable(catalog.icon(app))
                contentInfoTitle?.text = title
                contentInfoMeta?.text = listOfNotNull(subtitle,
                    program?.season?.takeIf { it.isNotBlank() }?.let { s(R.string.activity_season, it) },
                    program?.episode?.takeIf { it.isNotBlank() }?.let { s(R.string.activity_episode, it) },
                    program?.let { LocalizedText.playback(this, it) }?.takeIf { it.isNotBlank() }).joinToString(" · ")
                contentInfoDescription?.text = program?.description.orEmpty()
                setContentDetailsVisible(true)
            }
            val body = FrameLayout(this).apply {
                id = View.generateViewId(); tag = tagValue; isFocusable = true; isClickable = true; style(this)
                contentDescription = s(R.string.activity_content_open_a11y, title, subtitle)
                setOnClickListener { action() }
                setOnKeyListener { view, key, event ->
                    if (key == KeyEvent.KEYCODE_DPAD_UP && event.action == KeyEvent.ACTION_DOWN) {
                        page.findViewWithTag<View>("content-tab:$contentTab")?.requestFocus() ?: false
                    } else if (logicalKey(key) == KeyEvent.KEYCODE_DPAD_LEFT && event.action == KeyEvent.ACTION_DOWN && firstControl(host) === view) {
                        navigationControls.first().requestFocus()
                    } else false
                }
            }
            val surface = FrameLayout(this).apply {
                clipToOutline = true
                background = GradientDrawable().apply { setColor(Color.rgb(20, 38, 54)); cornerRadius = dp(8).toFloat() }
            }
            body.addView(surface, FrameLayout.LayoutParams(-1, -1).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) })
            val image = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_INSIDE; setImageDrawable(catalog.icon(app)); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
            surface.addView(image, FrameLayout.LayoutParams(-1, -1))
            surface.addView(View(this).apply { background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x2007111A, 0x3007111A, 0xF207111A.toInt())) }, FrameLayout.LayoutParams(-1, -1))
            val badge = row().apply {
                setPadding(dp(6), dp(3), dp(6), dp(3))
                background = GradientDrawable().apply { setColor(Color.argb(170, 5, 12, 21)); cornerRadius = dp(9).toFloat() }
            }
            badge.addView(ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER; setImageDrawable(catalog.icon(app)); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }, LinearLayout.LayoutParams(dp(16), dp(16)))
            gap(badge, 4); badge.addView(text(subtitle, 10f).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }, LinearLayout.LayoutParams(0, -2, 1f))
            surface.addView(badge, FrameLayout.LayoutParams(-1, dp(24), Gravity.TOP).apply { setMargins(dp(3), dp(3), dp(3), 0) })
            surface.addView(text(title, 13f).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END; setPadding(dp(6), 0, dp(6), dp(4)); gravity = Gravity.BOTTOM }, FrameLayout.LayoutParams(-1, dp(40), Gravity.BOTTOM).apply { bottomMargin = dp(3) })
            if (program != null) {
                artwork.bind(image, program)
                br.com.lume.launcher.core.ContentRules.progressPercent(program)?.let { percent ->
                    surface.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                        max = 100; progress = percent; isFocusable = false; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                        progressTintList = android.content.res.ColorStateList.valueOf(Color.rgb(117, 211, 248))
                    }, FrameLayout.LayoutParams(-1, dp(3), Gravity.BOTTOM))
                    body.contentDescription = s(R.string.activity_content_continue_a11y, title, subtitle, percent)
                }
            }
            body.addOnLayoutChangeListener { view, _, top, _, bottom, _, _, _, _ ->
                val height = bottom - top
                val viewport = host.width - line.paddingLeft - line.paddingRight
                if (height > 0 && viewport > 0) {
                    val targetWidth = LauncherRules.contentCardWidth(height, viewport)
                    if (view.layoutParams.width != targetWidth) view.layoutParams = (view.layoutParams as LinearLayout.LayoutParams).apply { width = targetWidth }
                }
                if (view.hasFocus() || view.isActivated) GlassUi.revealFocus(view)
            }
            line.addView(body, LinearLayout.LayoutParams(dp(232), -1)); gap(line, 18)
        }
        rows.forEach { program -> apps.find { it.packageName == program.packageName }?.let { app ->
            card(app, program.title, if (program.live) s(R.string.activity_live_platform, app.label) else app.label, "program:${program.key}", program) {
                if (!actions.openProgram(program)) { message(s(R.string.activity_content_open_fallback)); if (!actions.openApp(app)) message(s(R.string.activity_app_unavailable)) }
            }
        } }
        if (rows.isEmpty()) {
            val platforms = apps.filter { it.packageName in setOf("com.netflix.ninja", "com.amazon.amazonvideo.livingroom", "com.disney.disneyplus", "com.globo.globoplay", "com.globo.globotv", "com.wbd.stream", "com.hbo.hbonow", "com.google.android.youtube.tv", "com.apple.atve.androidtv.appletv", "br.com.sbt.mais", "br.com.sbt.sbtvideos", "com.sbt.tv") }
            platforms.take(8).forEach { app -> card(app, s(R.string.activity_explore_platform, app.label), s(R.string.activity_open_platform), "platform:${app.packageName}") { if (!actions.openApp(app)) message(s(R.string.activity_app_unavailable)) } }
            if (platforms.isEmpty()) line.addView(text(s(R.string.activity_content_no_suggestions), 12f, true))
        }
        finishContent()
    }

    private fun library() {
        page.addView(text(s(R.string.activity_apps_heading), 28f)); gap(page, 8)
        page.addView(button(s(R.string.activity_install_google_play)) { openStore() })
        page.addView(text(s(R.string.activity_apps_hint), 12f, true)); gap(page, 12)
        if (apps.isEmpty()) page.addView(text(if (loaded) s(R.string.activity_apps_empty) else s(R.string.activity_apps_reading), 16f))
        val cols = if (resources.configuration.screenWidthDp >= 1100) 6 else 5
        apps.chunked(cols).forEach { group ->
            val line = row()
            group.forEach { app -> line.addView(appCard(app), LinearLayout.LayoutParams(0, dp(96), 1f)); gap(line, 8) }
            repeat(cols - group.size) { line.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f)); gap(line, 8) }
            page.addView(line); gap(page, 10)
        }
    }

    private fun appCard(app: InstalledApp, showFavorite: Boolean = true): View {
        val card = column().apply { gravity = Gravity.CENTER_HORIZONTAL; clipChildren = false }
        val tileHolder = FrameLayout(this).apply { clipChildren = false }
        val tile = (if (showFavorite) FrameLayout(this) else HomeAppTile(this)).apply {
            id = View.generateViewId(); tag = "app:${app.packageName}"; isFocusable = true; isClickable = true
            contentDescription = s(if (showFavorite) {
                if (app.packageName in favoritePackages) R.string.activity_app_favorite_a11y else R.string.activity_app_add_favorite_a11y
            } else R.string.activity_app_open_a11y, app.label)
            isSelected = showFavorite && app.packageName in favoritePackages
            if (this is HomeAppTile) setActivityActive(active)
            style(this)
            setOnClickListener {
                if (movingPackage != null) finishMove(true)
                else if (showFavorite) toggleFavorite(app)
                else if (!actions.openApp(app)) message(s(R.string.activity_app_open_failed))
            }
            setOnLongClickListener { if (movingPackage == null) showAppOptions(app, this); true }
            if (this is HomeAppTile) addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ -> if (view.hasFocus()) GlassUi.revealFocus(view) }
            setOnKeyListener { view, key, event ->
                if (!showFavorite && currentPage == "home" && movingPackage == null && appMenu == null &&
                    key == KeyEvent.KEYCODE_DPAD_UP && event.action == KeyEvent.ACTION_DOWN) {
                    enterActiveContent(true)
                } else if (!showFavorite && currentPage == "home" && movingPackage == null && appMenu == null &&
                    logicalKey(key) == KeyEvent.KEYCODE_DPAD_RIGHT && event.action == KeyEvent.ACTION_DOWN &&
                    app.packageName == favoritePackages.lastOrNull()) {
                    enterActiveContent()
                } else if (event.action == KeyEvent.ACTION_DOWN && event.isLongPress && isSelectionKey(key)) {
                    view.cancelLongPress()
                    view.isPressed = false
                    if (appMenu == null && movingPackage == null) view.performLongClick()
                    true
                } else false
            }
        }
        val image = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO; setImageDrawable(catalog.icon(app)) }
        tile.addView(image, FrameLayout.LayoutParams(dp(if (showFavorite) 48 else 40), dp(if (showFavorite) 48 else 40), Gravity.CENTER))
        tileHolder.addView(tile, FrameLayout.LayoutParams(dp(if (showFavorite) 64 else 54), dp(if (showFavorite) 64 else 54), Gravity.CENTER))
        if (showFavorite) {
            val marker = ImageView(this).apply {
                setImageResource(if (app.packageName in favoritePackages) R.drawable.ic_star_filled else R.drawable.ic_star)
                scaleType = ImageView.ScaleType.CENTER_INSIDE; setPadding(dp(6), dp(6), dp(6), dp(6))
                background = GlassUi.panel(10, 20)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            tileHolder.addView(marker, FrameLayout.LayoutParams(dp(28), dp(28), Gravity.TOP or Gravity.END).apply { topMargin = dp(4); marginEnd = dp(4) })
        }
        card.addView(tileHolder, LinearLayout.LayoutParams(dp(if (showFavorite) 72 else 60), dp(if (showFavorite) 72 else 60)))
        card.addView(text(app.label, if (showFavorite) 12f else 11f).apply { gravity = Gravity.CENTER; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }, LinearLayout.LayoutParams(-1, dp(if (showFavorite) 24 else 20)))
        return card
    }

    private fun saveFavorites(focusTag: String? = currentFocus?.tag as? String) {
        prefs.edit().putString("favorites", favoritePackages.joinToString("|")).apply(); renderPage(focusTag)
    }
    private fun toggleFavorite(app: InstalledApp) {
        val oldIndex = favoritePackages.indexOf(app.packageName)
        favoritePackages = LauncherRules.toggleFavorite(favoritePackages, app.packageName)
        val focusTag = if (currentPage != "home") "app:${app.packageName}"
            else if (app.packageName in favoritePackages) "app:${app.packageName}"
            else favoritePackages.getOrNull(oldIndex.coerceIn(0, (favoritePackages.size - 1).coerceAtLeast(0)))?.let { "app:$it" } ?: "include-apps"
        saveFavorites(focusTag)
    }
    private fun showAppOptions(app: InstalledApp, anchor: View) {
        if (!active || isFinishing || isDestroyed || !anchor.isAttachedToWindow) return
        closeAppMenu(false)
        val body = column()
        val guard = MenuSelectGuard(selectionDownTime)
        fun item(label: String, action: () -> Unit) {
            val option = button(label) { closeAppMenu(false); action() }.apply {
                textSize = 14f; gravity = Gravity.CENTER_VERTICAL
                minHeight = dp(44); minimumHeight = dp(44); setPadding(dp(12), dp(8), dp(12), dp(8))
                nextFocusLeftId = id; nextFocusRightId = id
                setOnKeyListener { view, key, event ->
                    if (!isSelectionKey(key) || event.action !in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) false
                    else guard.consume(event.downTime, event.action == KeyEvent.ACTION_DOWN, event.repeatCount, event.isCanceled).also { consumed ->
                        if (consumed && event.action == KeyEvent.ACTION_UP) { view.isPressed = false; view.cancelLongPress() }
                    }
                }
            }
            body.addView(option, LinearLayout.LayoutParams(-1, -2)); gap(body, 2)
        }
        if (currentPage == "apps") item(s(R.string.activity_app_open)) { if (!actions.openApp(app)) message(s(R.string.activity_app_open_failed)) }
        val saved = app.packageName in favoritePackages
        if (saved) item(s(R.string.activity_move)) { beginMove(app) }
        item(if (saved) s(R.string.activity_favorite_remove) else s(R.string.activity_favorite_add)) { toggleFavorite(app) }
        item(s(R.string.activity_install_apps_arrow)) { openStore() }
        item(s(R.string.activity_app_information)) { if (!actions.openAppDetails(app)) message(s(R.string.activity_app_information_unavailable)) }
        if (actions.canUninstall(app)) item(s(R.string.activity_uninstall)) {
            if (!actions.requestUninstall(app)) message(s(R.string.activity_uninstall_confirmation_failed))
        }
        val panel = column().apply { background = GlassUi.panel(8, 30); setPadding(dp(8), dp(8), dp(8), dp(8)) }
        val title = text(app.label, 16f).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END; setPadding(dp(8), dp(4), dp(8), dp(8)) }
        panel.addView(title)
        val decor = window.decorView
        val visible = Rect().also { decor.getWindowVisibleDisplayFrame(it) }
        if (visible.width() <= 0 || visible.height() <= 0) return
        val (width, screenHeight) = LauncherRules.popupBounds(visible.width().coerceAtLeast(1), visible.height().coerceAtLeast(1), dp(256), dp(16))
        val popup = PopupWindow(panel, width, ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT)); elevation = dp(12).toFloat()
            inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED; isOutsideTouchable = true
            setIsClippedToScreen(true); animationStyle = 0
        }
        val maximumHeight = minOf(screenHeight, (popup.getMaxAvailableHeight(anchor, dp(6)) - dp(16)).coerceAtLeast(1))
        title.measure(View.MeasureSpec.makeMeasureSpec((width - panel.paddingLeft - panel.paddingRight).coerceAtLeast(1), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        val bodyHeight = (maximumHeight - panel.paddingTop - panel.paddingBottom - title.measuredHeight).coerceAtLeast(0)
        val scroll = object : ScrollView(this) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val limit = if (View.MeasureSpec.getMode(heightMeasureSpec) == View.MeasureSpec.UNSPECIFIED) bodyHeight
                    else minOf(bodyHeight, View.MeasureSpec.getSize(heightMeasureSpec))
                super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(limit, View.MeasureSpec.AT_MOST))
            }
        }.apply { isFillViewport = false; setPadding(dp(2), dp(2), dp(2), dp(2)); addView(body) }
        panel.addView(scroll, LinearLayout.LayoutParams(-1, -2))
        panel.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(maximumHeight, View.MeasureSpec.AT_MOST))
        popup.height = panel.measuredHeight.coerceIn(1, maximumHeight)
        val detached = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {}
            override fun onViewDetachedFromWindow(view: View) { if (appMenu === popup) closeAppMenu(false) }
        }
        val resized = View.OnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (appMenu === popup && (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop)) closeAppMenu(false)
        }
        popup.setOnDismissListener {
            anchor.removeOnAttachStateChangeListener(detached); decor.removeOnLayoutChangeListener(resized)
            if (appMenu === popup) {
                appMenu = null; appMenuOrigin = null
                if (active && anchor.isAttachedToWindow) anchor.requestFocus()
            }
        }
        appMenu = popup; appMenuOrigin = anchor
        anchor.addOnAttachStateChangeListener(detached); decor.addOnLayoutChangeListener(resized)
        try {
            val location = IntArray(2).also { anchor.getLocationOnScreen(it) }
            val inset = minOf(dp(16), (visible.width() - width).coerceAtLeast(0) / 2)
            val x = location[0].coerceIn(visible.left + inset, visible.right - inset - width)
            popup.showAsDropDown(anchor, x - location[0], dp(6), Gravity.START)
            panel.post { if (appMenu === popup) firstControl(body)?.requestFocus() }
        } catch (_: WindowManager.BadTokenException) {
            anchor.removeOnAttachStateChangeListener(detached); decor.removeOnLayoutChangeListener(resized)
            closeAppMenu(false)
        }
    }
    private fun closeAppMenu(restoreFocus: Boolean = true) {
        val menu = appMenu ?: return
        val origin = appMenuOrigin
        appMenu = null; appMenuOrigin = null; menu.dismiss()
        if (restoreFocus && active && origin?.isAttachedToWindow == true) origin.requestFocus()
    }
    private fun isSelectionKey(key: Int) = key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER || key == KeyEvent.KEYCODE_NUMPAD_ENTER

    private fun beginMove(app: InstalledApp) {
        if (app.packageName !in favoritePackages) return
        closeDialog()
        moveOriginal = favoritePackages.toList()
        movingPackage = app.packageName
        currentPage = "home"
        renderPage("app:${app.packageName}")
    }
    private fun finishMove(save: Boolean, announce: Boolean = true) {
        val packageName = movingPackage ?: return
        if (!save) favoritePackages = moveOriginal ?: favoritePackages
        movingPackage = null; moveOriginal = null
        if (save) saveFavorites("app:$packageName") else renderPage("app:$packageName")
        if (announce) message(if (save) s(R.string.activity_position_saved) else s(R.string.activity_move_canceled))
    }
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // The opening key-up may return to its original window before popup focus settles.
        if (appMenu != null && isSelectionKey(event.keyCode) && event.downTime == selectionDownTime) return true
        if (isSelectionKey(event.keyCode) && event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) selectionDownTime = event.downTime
        val packageName = movingPackage
        if (packageName != null) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        val delta = if (logicalKey(event.keyCode) == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1
                        val reordered = LauncherRules.moveFavorite(favoritePackages, packageName, delta)
                        if (reordered != favoritePackages) { favoritePackages = reordered; renderPage("app:$packageName") }
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) finishMove(true)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun sceneIndex() = LauncherRules.sceneIndex(scenes.size, prefs.getBoolean("daily", true), prefs.getInt("scene", 0), LocalDate.now())
    private fun sceneCard(index: Int, compact: Boolean): View {
        val scene = scenes[index]
        val card = column().apply { tag = "scene:${scene.id}"; isFocusable = true; isClickable = true; style(this); setPadding(dp(3), dp(3), dp(3), dp(3)); contentDescription = s(R.string.activity_scene_a11y, scene.title, scene.theme) }
        val image = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) { outline.setRoundRect(0, 0, view.width, view.height, dp(14).toFloat()) }
            }
            clipToOutline = true
        }
        val generation = pageImageGeneration
        worker.execute {
            if (generation != pageImageGeneration) return@execute
            val bitmap = runCatching { assets.open(scene.thumb).use { BitmapFactory.decodeStream(it) } }.getOrNull()
            handler.post {
                if (!isDestroyed && generation == pageImageGeneration) image.setImageBitmap(bitmap)
                else bitmap?.recycle() // Unpublished result from a page that has already been replaced.
            }
        }
        card.addView(image, LinearLayout.LayoutParams(-1, 0, 1f))
        card.addView(text(scene.title, if (compact) 12f else 14f).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; setPadding(dp(6), 0, dp(4), 0) }, LinearLayout.LayoutParams(-1, dp(25)))
        card.setOnClickListener { prefs.edit().putInt("scene", index).putBoolean("daily", false).apply(); refreshAmbient(); message(s(R.string.activity_scene_selected, scene.title)) }
        return card
    }
    private fun gallery() {
        page.addView(text(s(R.string.activity_gallery_heading), 28f)); page.addView(text(s(R.string.activity_gallery_description), 14f, true)); gap(page, 8)
        dailyButton = button(if (prefs.getBoolean("daily", true)) s(R.string.activity_daily_on) else s(R.string.activity_daily_enable)) {
            val daily = prefs.getBoolean("daily", true)
            prefs.edit().putInt("scene", sceneIndex()).putBoolean("daily", !daily).apply(); refreshAmbient(); renderPage()
        }
        page.addView(dailyButton); gap(page, 12)
        scenes.indices.chunked(3).forEach { indices ->
            val line = row()
            indices.forEachIndexed { j, index -> line.addView(sceneCard(index, false), LinearLayout.LayoutParams(0, dp(126), 1f)); if (j < 2) gap(line, 10) }
            page.addView(line); gap(page, 12)
        }
    }

    private fun refreshAmbient() {
        clock?.text = LocaleRules.formatTime(LocalTime.now(), AppLocales.effectiveTag(this))
        date?.text = LocalDate.now().format(DateTimeFormatter.ofPattern("EEE, d MMM", locale))
        val index = sceneIndex()
        if (index != currentScene) {
            currentScene = index
            val targetIndex = index
            val generation = ++wallpaperGeneration
            worker.execute {
                if (generation != wallpaperGeneration) return@execute
                val bitmap = runCatching { assets.open(scenes[targetIndex].file).use { BitmapFactory.decodeStream(it) } }.getOrNull()
                handler.post { if (!isDestroyed && generation == wallpaperGeneration) wallpaper.setImageBitmap(bitmap) else bitmap?.recycle() }
            }
        }
        val on = cinema()
        if (cinemaWasActive != on) {
            cinemaWasActive = on; cinemaLayer.visibility = if (on) View.VISIBLE else View.GONE
            restyle(window.decorView)
        }
        cinemaButton?.text = cinemaLabel()
        atmosphereButton?.text = s(if (prefs.getBoolean("daily", true)) R.string.activity_daily_atmosphere else R.string.activity_manual_atmosphere, scenes[index].title)
        dailyButton?.text = if (prefs.getBoolean("daily", true)) s(R.string.activity_daily_on) else s(R.string.activity_daily_enable)
    }

    private fun restyle(view: View) {
        if (view.isFocusable && view.isClickable) style(view)
        if (view.tag == "glass") safePanel(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) restyle(view.getChildAt(i))
    }
    private fun cinemaLabel() = s(R.string.activity_cinema_label,
        if (cinema()) s(R.string.activity_cinema_on) else s(R.string.activity_cinema_off),
        if (prefs.getString("cinema", "off") == "auto") s(R.string.activity_cinema_schedule_label) else "")
    private fun toggleCinema() {
        val wasScheduled = prefs.getString("cinema", "off") == "auto"
        prefs.edit().putString("cinema", LauncherRules.toggleCinema(prefs.getString("cinema", "off"), hour())).apply()
        refreshAmbient(); if (wasScheduled) message(s(R.string.activity_cinema_manual_schedule_off))
    }
    private fun showCinema() {
        val body = column()
        body.addView(text(s(R.string.activity_cinema_description), 15f, true)); gap(body, 12)
        val toggle = button(if (cinema()) s(R.string.activity_cinema_disable_now) else s(R.string.activity_cinema_enable_now)) { }
        toggle.setOnClickListener { toggleCinema(); toggle.text = if (cinema()) s(R.string.activity_cinema_disable_now) else s(R.string.activity_cinema_enable_now); showCinema() }
        body.addView(toggle); gap(body, 8)
        val scheduled = prefs.getString("cinema", "off") == "auto"
        body.addView(button(if (scheduled) s(R.string.activity_cinema_schedule_disable) else s(R.string.activity_cinema_schedule_enable)) {
            prefs.edit().putString("cinema", LauncherRules.scheduleCinema(prefs.getString("cinema", "off"), !scheduled, hour())).apply(); refreshAmbient(); showCinema()
        })
        gap(body, 12); body.addView(text(s(R.string.activity_cinema_schedule_description), 12f, true))
        dialog(s(R.string.activity_cinema_title), body)
    }

    private fun openNetwork() {
        closeDialog()
        if (!actions.openNetwork()) message(s(R.string.activity_network_unavailable))
    }
    private fun updateNetworkButton() {
        networkButton?.text = GlassUi.networkLabel(networkStatus)
        networkButton?.contentDescription = s(R.string.activity_network_a11y, networkStatus.connection, networkStatus.internet)
    }
    private fun showSettings() {
        val body = column()
        listOf<Pair<String, () -> Unit>>(
            s(R.string.activity_android_settings) to { closeDialog(); if (!actions.openSettings()) message(s(R.string.activity_android_settings_unavailable)) },
            s(R.string.activity_network_settings) to { openNetwork() }, s(R.string.activity_install_apps) to { showStore() }, s(R.string.activity_cinema_settings) to { showCinema() },
            s(R.string.activity_appearance_title) to { showAppearance() }, s(R.string.activity_screensaver_title) to { showScreensaverSettings() }, s(R.string.activity_hdmi_inputs) to { showInputs() },
            s(R.string.activity_language_setting, languageName()) to { showLanguage() },
            (if (prefs.getBoolean("shared_content", false)) s(R.string.activity_content_disable) else s(R.string.activity_content_enable)) to {
                closeDialog()
                if (prefs.getBoolean("shared_content", false)) {
                    prefs.edit().putBoolean("shared_content", false).apply(); cancelContent()
                    contentSnapshot = TvContentCatalog.Snapshot(emptyList(), emptyList(), false); renderContent()
                } else connectContent()
            },
            s(R.string.activity_home_choose) to { showHomeChoice() },
            s(R.string.activity_about_title) to { showAbout() }
        ).forEach { (label, action) -> body.addView(button(label, action), LinearLayout.LayoutParams(-1, -2)); gap(body, 6) }
        dialog(s(R.string.activity_settings_title), body)
    }
    private fun showAbout() {
        val body = column()
        body.addView(text(applicationInfo.loadLabel(packageManager).toString(), 24f)); gap(body, 8)
        val version = try {
            @Suppress("DEPRECATION")
            val info = packageManager.getPackageInfo(packageName, 0)
            s(R.string.activity_about_version, info.versionName ?: s(R.string.activity_about_version_unknown), info.longVersionCode)
        } catch (_: PackageManager.NameNotFoundException) {
            s(R.string.activity_about_version_unknown)
        }
        body.addView(text(version, 15f, true)); gap(body, 16)
        body.addView(button(s(R.string.activity_update_check)) { checkForUpdate() }.apply { tag = "update:check" })
        gap(body, 12)
        body.addView(text(s(R.string.activity_about_license), 14f, true)); gap(body, 16)
        fun openLink(url: String) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE))
            } catch (_: ActivityNotFoundException) {
                message(s(R.string.activity_about_link_failed))
            } catch (_: SecurityException) {
                message(s(R.string.activity_about_link_failed))
            }
        }
        body.addView(button(s(R.string.activity_about_repository)) { openLink("https://github.com/JohnSalazar/lume-orbita") })
        gap(body, 6)
        body.addView(button(s(R.string.activity_about_license_open)) { openLink("https://github.com/JohnSalazar/lume-orbita/blob/main/LICENSE") })
        dialog(s(R.string.activity_about_title), body)
    }
    private fun cancelUpdate() {
        updateGeneration++
        handler.removeCallbacks(updateDeadline)
        updateClient?.cancel(); updateJob?.cancel(true); updateJob = null
        updateWorker.purge()
        preparedUpdate?.let { runCatching { updateClient?.discard(it) } }; preparedUpdate = null
        updateClient = null; updateDialog = null
    }
    private fun submitUpdate(action: () -> Unit) {
        updateWorker.purge()
        try {
            updateJob = updateWorker.submit(action)
            handler.postDelayed(updateDeadline, 60_000L)
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            cancelUpdate()
            updateError(UpdateFailure(UpdateFailureReason.NETWORK))
        }
    }
    private fun updatePanel(status: String, busy: Boolean = false, actionLabel: String? = null, action: (() -> Unit)? = null): TextView {
        changingUpdateDialog = true
        try {
            val body = column()
            val label = text(status, 16f).apply { tag = "update:status" }
            body.addView(label); gap(body, 12)
            if (busy) body.addView(ProgressBar(this).apply { isIndeterminate = true }, LinearLayout.LayoutParams(dp(32), dp(32)))
            if (actionLabel != null && action != null) body.addView(button(actionLabel, action).apply { tag = "update:action" })
            dialog(s(R.string.activity_update_check), body)
            updateDialog = openDialog
            return label
        } finally { changingUpdateDialog = false }
    }
    private fun updateError(error: Throwable) {
        val key = when ((error as? UpdateFailure)?.reason) {
            UpdateFailureReason.INVALID_RELEASE, UpdateFailureReason.INVALID_APK -> R.string.activity_update_rejected
            UpdateFailureReason.RATE_LIMIT -> R.string.activity_update_rate_limit
            UpdateFailureReason.STORAGE -> R.string.activity_update_storage
            else -> R.string.activity_update_error
        }
        updatePanel(s(key), actionLabel = s(R.string.activity_update_retry), action = ::checkForUpdate)
    }
    private fun checkForUpdate() {
        cancelUpdate()
        val client = GitHubUpdater(applicationContext)
        updateClient = client
        val generation = updateGeneration
        updatePanel(s(R.string.activity_update_checking), busy = true)
        submitUpdate {
            val result = runCatching { client.check() }
            handler.post {
                if (isDestroyed || generation != updateGeneration || openDialog !== updateDialog) return@post
                handler.removeCallbacks(updateDeadline)
                updateJob = null
                result.fold({ release ->
                    if (release == null) updatePanel(s(R.string.activity_update_current), actionLabel = s(R.string.activity_update_check), action = ::checkForUpdate)
                    else showAvailableUpdate(release)
                }, ::updateError)
            }
        }
    }
    private fun showAvailableUpdate(release: UpdateRelease) {
        val status = listOfNotNull(s(R.string.activity_update_available, release.version),
            s(R.string.activity_update_evaluation).takeIf { release.prerelease }, s(R.string.activity_update_notice)).joinToString("\n\n")
        updatePanel(status, actionLabel = s(R.string.activity_update_download), action = { downloadUpdate(release) })
    }
    private fun downloadUpdate(release: UpdateRelease) {
        val client = updateClient ?: return
        val generation = updateGeneration
        val label = updatePanel(s(R.string.activity_update_progress, 0), busy = true)
        submitUpdate {
            var previousProgress = -1
            val result = runCatching { client.download(release) { progress ->
                if (progress != previousProgress) {
                    previousProgress = progress
                    handler.post {
                        if (generation == updateGeneration && openDialog === updateDialog && label.isAttachedToWindow)
                            label.text = if (progress >= 100) s(R.string.activity_update_verify) else s(R.string.activity_update_progress, progress)
                    }
                }
            } }
            handler.post {
                if (isDestroyed || generation != updateGeneration || openDialog !== updateDialog) {
                    result.getOrNull()?.let { runCatching { client.discard(it) } }; return@post
                }
                handler.removeCallbacks(updateDeadline)
                updateJob = null
                result.fold({ prepared -> preparedUpdate = prepared; showPreparedUpdate(prepared) }, ::updateError)
            }
        }
    }
    private fun showPreparedUpdate(prepared: PreparedUpdate) {
        if (!UpdateInstaller.canInstall(this)) {
            updatePanel(s(R.string.activity_update_source_required), actionLabel = s(R.string.activity_update_settings), action = {
                if (!UpdateInstaller.openPermissionSettings(this)) message(s(R.string.activity_update_install_failed))
            })
        } else {
            updatePanel(s(R.string.activity_update_available, prepared.release.version) + "\n\n" + s(R.string.activity_update_notice),
                actionLabel = s(R.string.activity_update_install), action = {
                    if (UpdateInstaller.launch(this, prepared)) {
                        preparedUpdate = null
                        closeDialog()
                    } else message(s(R.string.activity_update_install_failed))
                })
        }
    }
    private fun showUpdateHomeWarning() {
        val body = column()
        body.addView(text(s(R.string.activity_update_home_changed), 15f)); gap(body, 12)
        body.addView(button(s(R.string.activity_update_home_review)) { showHomeChoice() })
        dialog(s(R.string.activity_update_check), body)
    }
    private fun languageName(): String = AppLocales.languages.firstOrNull { it.tag == AppLocales.selected(this) }?.nativeName
        ?: s(R.string.activity_language_system)

    private fun showLanguage() {
        val body = column()
        val originTag = dialogOrigin?.tag as? String ?: currentFocus?.tag as? String
        val selected = AppLocales.selected(this)
        body.addView(text(s(R.string.activity_language_description), 14f, true)); gap(body, 12)
        val options = listOf(LanguageOption(br.com.lume.launcher.core.LocaleRules.SYSTEM, s(R.string.activity_language_system))) + AppLocales.languages
        var selectedControl: View? = null
        options.forEach { option ->
            val isCurrent = option.tag == selected
            val control = button(if (isCurrent) s(R.string.activity_language_selected, option.nativeName) else option.nativeName) {
                if (option.tag != selected) {
                    languageOriginTag = originTag
                    AppLocales.set(this, option.tag)
                    closeDialog()
                    recreate()
                }
            }.apply { tag = "language:${option.tag}"; isSelected = isCurrent }
            body.addView(control, LinearLayout.LayoutParams(-1, -2)); gap(body, 6)
            if (isCurrent) selectedControl = control
        }
        dialog(s(R.string.activity_language_title), body)
        val languageDialog = openDialog
        body.post { if (active && openDialog === languageDialog && body.isAttachedToWindow) selectedControl?.requestFocus() }
    }

    private fun showScreensaverSettings(focusOption: Int = 0) {
        val body = column()
        val options = mutableListOf<View>()
        fun option(label: String, action: () -> Unit) { val control = button(label, action); options.add(control); body.addView(control, LinearLayout.LayoutParams(-1, -2)); gap(body, 6) }
        val enabled = prefs.getBoolean("screensaver_enabled", true)
        val minutes = ScreensaverRules.idleMinutes(prefs.getInt("screensaver_idle_minutes", 2))
        val interval = ScreensaverRules.intervalMillis(prefs.getLong("screensaver_interval", 60000L))
        val dim = ScreensaverRules.dimPercent(prefs.getInt("screensaver_dim", 25))
        val clockEnabled = prefs.getBoolean("screensaver_clock", false)
        option(s(R.string.activity_screensaver_start)) { closeDialog(); startScreensaver() }
        option(if (enabled) s(R.string.activity_screensaver_on) else s(R.string.activity_screensaver_off)) {
            prefs.edit().putBoolean("screensaver_enabled", !enabled).apply(); resetScreensaverTimer(); showScreensaverSettings(1)
        }
        option(s(R.string.activity_screensaver_delay, minutes)) {
            val values = listOf(2, 5, 10, 15)
            prefs.edit().putInt("screensaver_idle_minutes", values[(values.indexOf(minutes) + 1) % values.size]).apply(); resetScreensaverTimer(); showScreensaverSettings(2)
        }
        option(s(R.string.activity_screensaver_interval, if (interval == 30000L) s(R.string.activity_duration_30_seconds) else if (interval == 60000L) s(R.string.activity_duration_one_minute) else s(R.string.activity_duration_minutes, interval / 60000))) {
            val values = listOf(30000L, 60000L, 180000L, 300000L)
            prefs.edit().putLong("screensaver_interval", values[(values.indexOf(interval) + 1) % values.size]).apply(); showScreensaverSettings(3)
        }
        option(s(R.string.activity_screensaver_dim, dim)) {
            val values = listOf(10, 25, 40)
            prefs.edit().putInt("screensaver_dim", values[(values.indexOf(dim) + 1) % values.size]).apply(); showScreensaverSettings(4)
        }
        option(if (clockEnabled) s(R.string.activity_clock_on) else s(R.string.activity_clock_off)) {
            prefs.edit().putBoolean("screensaver_clock", !clockEnabled).apply(); showScreensaverSettings(5)
        }
        option(s(R.string.activity_android_screensaver)) { closeDialog(); if (!actions.openScreensaverSettings()) message(s(R.string.activity_android_screensaver_unavailable)) }
        option(s(R.string.activity_photo_credits)) { showScreensaverCredits() }
        gap(body, 6)
        body.addView(text(s(R.string.activity_screensaver_description), 12f, true))
        dialog(s(R.string.activity_screensaver_title), body)
        body.post { options.getOrNull(focusOption)?.requestFocus() }
    }
    private fun showScreensaverCredits() {
        val body = column()
        body.addView(text(s(R.string.activity_photo_credits_description), 12f, true)); gap(body, 16)
        runCatching {
            val images = JSONArray(assets.open("screensaver.json").bufferedReader().use { it.readText() })
            for (i in 0 until images.length()) {
                val image = images.getJSONObject(i)
                body.addView(text(s(R.string.activity_photo_credit, LocalizedText.photoTitle(this, i), image.getString("artist"), image.getString("license"), image.getString("licenseUrl"), image.getString("sourceUrl")), 12f, true)); gap(body, 16)
            }
        }.onFailure { body.addView(text(s(R.string.activity_photo_credits_unavailable), 14f)) }
        dialog(s(R.string.activity_photo_credits), body)
    }
    private fun showHomeChoice() {
        val body = column()
        body.addView(text(s(R.string.activity_home_choice_description), 15f, true)); gap(body, 12)
        body.addView(button(s(R.string.activity_home_open_selection)) { closeDialog(); if (!actions.requestHome()) message(s(R.string.activity_home_selection_unavailable)) })
        gap(body, 8); body.addView(text(s(R.string.activity_home_restore), 14f, true))
        dialog(s(R.string.activity_home_choice_title), body)
    }
    private fun showInputs() {
        val body = column()
        val inputs = actions.inputs()
        if (inputs.isEmpty()) body.addView(text(s(R.string.activity_inputs_empty), 16f))
        inputs.forEach { (id, label) -> body.addView(button(label) { closeDialog(); if (!actions.openInput(id)) message(s(R.string.activity_input_unavailable)) }); gap(body) }
        dialog(s(R.string.activity_inputs_title), body)
    }
    private fun openStore() { if (!actions.openStore()) message(s(R.string.activity_google_play_tv_unavailable)) }
    private fun showStore() {
        val body = column()
        body.addView(text(s(R.string.activity_store_description), 14f, true)); gap(body, 12)
        body.addView(button(s(R.string.activity_google_play_open)) { closeDialog(); if (!actions.openStore()) message(s(R.string.activity_google_play_device_unavailable)) })
        val suggestions = listOf("YouTube" to "com.google.android.youtube.tv", "Netflix" to "com.netflix.ninja", "Disney+" to "com.disney.disneyplus", "Prime Video" to "com.amazon.amazonvideo.livingroom", "Spotify" to "com.spotify.tv.android", "Plex" to "com.plexapp.android", "VLC" to "org.videolan.vlc")
        suggestions.forEach { (name, packageName) -> gap(body, 6); body.addView(button(s(if (apps.any { it.packageName == packageName }) R.string.activity_store_app_installed else R.string.activity_store_app_available, name)) { closeDialog(); if (!actions.openStore(packageName)) message(s(R.string.activity_store_app_failed)) }) }
        dialog(s(R.string.activity_install_apps), body)
    }
    private fun showAppearance(focusKey: String? = null) {
        val body = column()
        body.addView(text(s(R.string.activity_appearance_description), 14f, true)); gap(body)
        listOf("transparency" to s(R.string.activity_transparency), "reflections" to s(R.string.activity_reflections)).forEach { (key, name) ->
            val label = text(s(R.string.activity_appearance_value, name, prefs.getInt(key, if (key == "transparency") 58 else 65)), 16f)
            body.addView(label)
            val seek = SeekBar(this).apply { max = 100; progress = prefs.getInt(key, if (key == "transparency") 58 else 65); contentDescription = name; minimumHeight = dp(48) }
            seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) { if (fromUser) { prefs.edit().putInt(key, progress).apply(); label.text = s(R.string.activity_appearance_value, name, progress); restyle(window.decorView) } }
                override fun onStartTrackingTouch(bar: SeekBar?) = Unit
                override fun onStopTrackingTouch(bar: SeekBar?) = Unit
            }); body.addView(seek, LinearLayout.LayoutParams(-1, dp(48)))
        }
        listOf("animations" to s(R.string.activity_animations), "lightweight" to s(R.string.activity_lightweight)).forEach { (key, name) ->
            val enabled = prefs.getBoolean(key, key == "animations")
            body.addView(button(s(if (enabled) R.string.activity_setting_on else R.string.activity_setting_off, name)) { prefs.edit().putBoolean(key, !enabled).apply(); restyle(window.decorView); showAppearance(key) }.apply { tag = "appearance:$key" }); gap(body, 6)
        }
        body.addView(button(s(R.string.activity_choose_scenes)) { closeDialog(); showPage("scenes") })
        dialog(s(R.string.activity_appearance_title), body)
        val appearanceDialog = openDialog
        if (focusKey != null) body.post {
            if (active && body.isAttachedToWindow && openDialog === appearanceDialog)
                body.findViewWithTag<View>("appearance:$focusKey")?.requestFocus()
        }
    }
    private fun dialog(title: String, content: LinearLayout) {
        closeAppMenu(false)
        val origin = dialogOrigin ?: currentFocus
        openDialog?.dismiss()
        dialogOrigin = origin
        val outer = column().apply { setPadding(dp(24), dp(20), dp(24), dp(16)); background = GlassUi.panel(0, appearance().second) }
        val header = row()
        val back = GlassUi.backButton(this, s(R.string.activity_back), { closeDialog() }, ::appearance).apply { id = View.generateViewId(); style(this) }
        header.addView(back, LinearLayout.LayoutParams(-2, -2)); gap(header, 16)
        header.addView(text(title, 24f).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END }, LinearLayout.LayoutParams(0, -2, 1f))
        outer.addView(header, LinearLayout.LayoutParams(-1, -2)); gap(outer, 12)
        content.setPadding(dp(6), dp(6), dp(6), dp(6))
        val scroll = object : ScrollView(this) {
            var maximumHeight = 0
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val limit = if (View.MeasureSpec.getMode(heightMeasureSpec) == View.MeasureSpec.UNSPECIFIED) maximumHeight
                    else minOf(maximumHeight, View.MeasureSpec.getSize(heightMeasureSpec))
                super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(limit, View.MeasureSpec.AT_MOST))
            }
        }.apply { isFillViewport = false }
        scroll.addView(content, FrameLayout.LayoutParams(-1, -2))
        outer.addView(scroll, LinearLayout.LayoutParams(-1, -2))
        fun linkBack(view: View) {
            if (view is Button) focusStart(view, back.id)
            if (view is ViewGroup) for (i in 0 until view.childCount) linkBack(view.getChildAt(i))
        }
        linkBack(content)
        firstControl(content)?.let { first ->
            if (first.id == View.NO_ID) first.id = View.generateViewId()
            first.nextFocusUpId = back.id
            focusEnd(back, first.id); back.nextFocusDownId = first.id
        }
        val dialog = Dialog(this, android.R.style.Theme_Material_Dialog_NoActionBar)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(outer)
        val decor = window.decorView
        fun resize() {
            val metrics = resources.displayMetrics
            val insets = decor.rootWindowInsets?.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            val availableWidth = ((decor.width.takeIf { it > 0 } ?: metrics.widthPixels) - (insets?.left ?: 0) - (insets?.right ?: 0)).coerceAtLeast(1)
            val availableHeight = ((decor.height.takeIf { it > 0 } ?: metrics.heightPixels) - (insets?.top ?: 0) - (insets?.bottom ?: 0)).coerceAtLeast(1)
            val (width, maxHeight) = LauncherRules.popupBounds(availableWidth, availableHeight, dp(720), dp(24))
            val innerWidth = (width - outer.paddingLeft - outer.paddingRight).coerceAtLeast(1)
            val widthSpec = View.MeasureSpec.makeMeasureSpec(innerWidth, View.MeasureSpec.EXACTLY)
            val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            header.measure(widthSpec, heightSpec)
            scroll.maximumHeight = (maxHeight - outer.paddingTop - outer.paddingBottom - header.measuredHeight - dp(12)).coerceAtLeast(0)
            scroll.requestLayout()
            dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val layoutListener = View.OnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) resize()
        }
        dialog.setOnDismissListener {
            decor.removeOnLayoutChangeListener(layoutListener)
            if (updateDialog === dialog && !changingUpdateDialog) cancelUpdate()
            if (openDialog === dialog) {
                openDialog = null; dialogOrigin = null
                if (origin?.isAttachedToWindow == true) origin.requestFocus()
            }
        }
        openDialog = dialog
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            decorView.setPadding(0, 0, 0, 0)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
        decor.addOnLayoutChangeListener(layoutListener)
        resize()
        dialog.show()
        resize()
        outer.post { if (openDialog === dialog) (firstControl(content) ?: back).requestFocus() }
    }
    private fun closeDialog() { openDialog?.dismiss() }
    private fun message(value: String) = Toast.makeText(this, value, Toast.LENGTH_LONG).show()
    @Deprecated("API30 compatibility; TV Back returns to our Home before leaving evaluation mode")
    override fun onBackPressed() {
        if (movingPackage != null) finishMove(false)
        else if (currentPage != "home") showPage("home")
        else if (!actions.isHome()) super.onBackPressed()
    }
}
