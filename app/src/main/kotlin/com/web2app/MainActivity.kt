package com.web2app

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.webkit.*
import androidx.appcompat.app.AppCompatActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.web2app.data.AppRepository
import com.web2app.handlers.PermissionsHandler
import com.web2app.models.parseAppConfig

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var progressBar: View
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var noInternetView: View

    /** Holder for the configured page loader; null/false means use the default spinner. */
    private var pageLoaderHolder: View? = null
    private var useCustomPageLoader = false

    private lateinit var permissionsHandler: PermissionsHandler
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private var selectedTabIndex = 0

    companion object {
        const val EXTRA_APP_ID = "extra_app_id"
        private const val DEFAULT_THEME = 0xFF5B5BD6.toInt()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Preview mode: load the app being previewed from the builder's storage.
        // Falls back to the bundled assets/app_settings.json when launched standalone.
        val appId = intent.getStringExtra(EXTRA_APP_ID)
        val isPreview = appId != null
        appConfig = if (isPreview) {
            AppRepository(this).getApp(appId!!) ?: appConfig
        } else {
            parseAppConfig(readJson(this, "app_settings.json"))
        }

        // Setup permissions handler (must be created before setContentView for ActivityResult)
        permissionsHandler = PermissionsHandler(this)

        setContentView(R.layout.activity_main)
        applySystemBarInsets(R.id.mainRoot, R.id.statusBarScrim)

        webView = findViewById(R.id.webView)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        progressBar = findViewById(R.id.progressBar)
        bottomNav = findViewById(R.id.bottomNav)
        noInternetView = findViewById(R.id.noInternetView)

        // Show the preview close button (faint eye behind a clear X) only when
        // previewing an app from the builder; tapping it dismisses the preview.
        findViewById<View>(R.id.previewControls).apply {
            visibility = if (isPreview) View.VISIBLE else View.GONE
            setOnClickListener { finish() }
        }

        setupPageLoader()
        setupWebView()
        setupBottomNav()
        setupConnectivity()
        requestConfiguredPermissions()

        swipeRefresh.setOnRefreshListener { webView.reload() }

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else {
            loadCurrentUrl()
        }
    }

    // ─── Page loader ─────────────────────────────────────────────────────────

    /**
     * Builds the configured in-page loader into [R.id.pageLoaderHolder]. When the page
     * loader isn't enabled we fall back to the default centered [progressBar].
     */
    private fun setupPageLoader() {
        val holder = findViewById<FrameLayout>(R.id.pageLoaderHolder)
        pageLoaderHolder = holder
        val pl = appConfig.pageLoader
        if (!pl.enabled) {
            useCustomPageLoader = false
            return
        }
        val theme = safeParseColor(appConfig.themeColor, DEFAULT_THEME)
        val icon = Base64ImageUtil.base64ToBitmap(appConfig.appIcon)
        val opts = PageLoaderViews.Opts(
            card = pl.card,
            cardColor = safeParseColor(pl.cardColor, Color.WHITE),
            cardOpacity = pl.cardOpacity,
            cardRadius = pl.cardRadius,
            gap = pl.gap,
            loaderWidth = pl.loaderWidth,
            loaderThickness = pl.loaderThickness
        )
        holder.removeAllViews()
        holder.addView(PageLoaderViews.create(this, pl.style, icon, theme, opts))
        useCustomPageLoader = true
    }

    /** Shows/hides whichever loader is in use (configured page loader or the default). */
    private fun showLoading(show: Boolean) {
        val target = if (useCustomPageLoader) pageLoaderHolder else progressBar
        target?.visibility = if (show) View.VISIBLE else View.GONE
    }

    // ─── WebView ───────────────────────────────────────────────────────────────

    @Suppress("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = true
            allowFileAccess = true
            allowContentAccess = true
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false

            // Allow mixed content (http resources on https pages) – mirrors reference Webview.kt
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }

            // Custom user-agent matching the reference
            userAgentString =
                "Mozilla/5.0 (Linux; Android ${Build.VERSION.RELEASE}; ${Build.MODEL}) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        }

        // Enable cookies (including third-party) – mirrors reference Webview.kt
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                setAcceptThirdPartyCookies(webView, true)
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView, request: WebResourceRequest
            ): Boolean {
                view.loadUrl(request.url.toString())
                return false
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                showLoading(true)
            }

            override fun onPageFinished(view: WebView, url: String) {
                showLoading(false)
                swipeRefresh.isRefreshing = false
            }
        }

        // WebChromeClient with popup-window support – mirrors reference Webview.kt onCreateWindow
        webView.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message?
            ): Boolean {
                val ctx = view?.context ?: return false

                val popupWebView = WebView(ctx).apply {
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        setSupportMultipleWindows(true)
                        javaScriptCanOpenWindowsAutomatically = true
                    }
                    webViewClient = object : WebViewClient() {}
                }

                android.app.Dialog(ctx).apply {
                    setContentView(popupWebView)
                    window?.setLayout(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    show()
                }

                val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                transport.webView = popupWebView
                resultMsg.sendToTarget()
                return true
            }
        }
    }

    // ─── Bottom Navigation ─────────────────────────────────────────────────────

    private fun setupBottomNav() {
        if (!appConfig.showTabbar || appConfig.tabbars.isEmpty()) {
            bottomNav.visibility = View.GONE
            return
        }

        bottomNav.visibility = View.VISIBLE

        // Apply colours from tabSettings (mirrors BottomNavigationBar composable)
        val activeColor = safeParseColor(appConfig.tabSettings.tabActiveColor, Color.BLUE)
        val inactiveColor = safeParseColor(appConfig.tabSettings.tabInactiveColor, Color.GRAY)
        val barColor = safeParseColor(appConfig.tabSettings.tabBarColor, Color.WHITE)

        bottomNav.setBackgroundColor(barColor)

        val colorStateList = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(-android.R.attr.state_checked)
            ),
            intArrayOf(activeColor, inactiveColor)
        )
        bottomNav.itemIconTintList = colorStateList
        bottomNav.itemTextColor = colorStateList
        bottomNav.setItemTextAppearanceActive(R.style.TextAppearance_Web2App_Tab)
        bottomNav.setItemTextAppearanceInactive(R.style.TextAppearance_Web2App_Tab)

        // Match the classic (shorter) bottom bar: drop the Material3 active-indicator
        // pill, which otherwise makes the bar noticeably taller, and use a compact
        // fixed height (a touch shorter when titles are hidden).
        bottomNav.isItemActiveIndicatorEnabled = false
        val showTitles = appConfig.tabSettings.showTitles
        bottomNav.labelVisibilityMode = if (showTitles) {
            com.google.android.material.bottomnavigation.BottomNavigationView.LABEL_VISIBILITY_LABELED
        } else {
            com.google.android.material.bottomnavigation.BottomNavigationView.LABEL_VISIBILITY_UNLABELED
        }
        val barHeightDp = if (showTitles) 60 else 54
        bottomNav.layoutParams = bottomNav.layoutParams.apply {
            height = (barHeightDp * resources.displayMetrics.density).toInt()
        }

        // Build menu items dynamically from JSON (Base64 icons → BitmapDrawable)
        val menu = bottomNav.menu
        menu.clear()

        // 3px gap between icon and title (only meaningful when titles are shown).
        val iconLabelGap = if (showTitles) 3 else 0
        for ((index, tab) in appConfig.tabbars.withIndex()) {
            val item = menu.add(0, index, index, tab.title)
            if (tab.icon.isNotBlank()) {
                val res = MaterialIcons.resFor(tab.icon)
                val icon: android.graphics.drawable.Drawable? = when {
                    LucideIcons.isLucide(tab.icon) -> {
                        val px = (24 * resources.displayMetrics.density).toInt()
                        LucideIcons.drawable(
                            this, LucideIcons.name(tab.icon), px, android.graphics.Color.BLACK
                        )
                    }
                    res != null -> androidx.core.content.ContextCompat.getDrawable(this, res)
                    else -> Base64ImageUtil.base64ToBitmapDrawable(this, tab.icon)
                }
                item.icon = icon?.let {
                    android.graphics.drawable.InsetDrawable(it, 0, 0, 0, iconLabelGap)
                }
            }
        }

        // Select first tab
        bottomNav.selectedItemId = 0

        bottomNav.setOnItemSelectedListener { item ->
            selectedTabIndex = item.itemId
            loadCurrentUrl()
            true
        }
    }

    private fun loadCurrentUrl() {
        val url = if (appConfig.showTabbar && appConfig.tabbars.isNotEmpty()) {
            appConfig.tabbars.getOrNull(selectedTabIndex)?.url ?: appConfig.websiteURL
        } else {
            appConfig.websiteURL
        }
        webView.loadUrl(url)
    }

    // ─── Network Connectivity (mirrors connectivityState / observeConnectivityAsFlow) ──

    private fun setupConnectivity() {
        connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // Show correct initial state
        if (isNetworkConnected()) showWebContent() else showNoInternet()

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                runOnUiThread { showWebContent() }
            }

            override fun onLost(network: Network) {
                runOnUiThread { showNoInternet() }
            }
        }

        connectivityManager?.registerNetworkCallback(request, networkCallback!!)
    }

    private fun isNetworkConnected(): Boolean {
        val cm = connectivityManager ?: return false
        return cm.getNetworkCapabilities(cm.activeNetwork)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    private fun showWebContent() {
        swipeRefresh.visibility = View.VISIBLE
        noInternetView.visibility = View.GONE
    }

    private fun showNoInternet() {
        swipeRefresh.visibility = View.GONE
        noInternetView.visibility = View.VISIBLE
    }

    // ─── Permissions (mirrors PermissionsWrapper composable in reference) ──────

    private fun requestConfiguredPermissions() {
        val perms = appConfig.permissions
        val needsLocation = perms.contains(0)
        val needsFileCamera = perms.contains(1)
        val needsAudioVideo = perms.contains(2)

        when {
            needsLocation -> permissionsHandler.requestLocationPermissions {
                if (needsFileCamera) {
                    permissionsHandler.requestFileAndCameraPermissions {
                        if (needsAudioVideo) permissionsHandler.requestAudioVideoPermissions()
                    }
                } else if (needsAudioVideo) {
                    permissionsHandler.requestAudioVideoPermissions()
                }
            }
            needsFileCamera -> permissionsHandler.requestFileAndCameraPermissions {
                if (needsAudioVideo) permissionsHandler.requestAudioVideoPermissions()
            }
            needsAudioVideo -> permissionsHandler.requestAudioVideoPermissions()
        }
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private fun safeParseColor(hex: String, default: Int): Int {
        return try {
            if (hex.isBlank()) default else Color.parseColor(hex)
        } catch (e: Exception) {
            default
        }
    }

    // ─── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }

    override fun onDestroy() {
        super.onDestroy()
        networkCallback?.let { connectivityManager?.unregisterNetworkCallback(it) }
    }
}
