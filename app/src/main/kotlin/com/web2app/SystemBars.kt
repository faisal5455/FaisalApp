package com.web2app

import android.app.Activity
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * targetSdk 35+ forces edge-to-edge windows, so content otherwise draws under the
 * status and navigation bars. These helpers restore the "proper place" layout.
 */

/**
 * Immersive full screen: hides the status + navigation bars, letting the content use
 * the whole display. The bars reappear transiently on a swipe from the edge and hide
 * again on their own, so call this from onCreate and onWindowFocusChanged.
 */
fun Activity.enterFullScreen() {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    WindowInsetsControllerCompat(window, window.decorView).apply {
        systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        hide(WindowInsetsCompat.Type.systemBars())
    }
}

/** Pads the content root by the system-bar insets (status + navigation). */
fun Activity.applySystemBarPadding() {
    val content = findViewById<View>(android.R.id.content)
    ViewCompat.setOnApplyWindowInsetsListener(content) { v, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
        insets
    }
}

/**
 * For the WebView shell: a coloured scrim (sized to the top inset) fills the
 * status-bar area so it keeps its solid brand colour, while the content root is
 * lifted off the navigation bar.
 */
fun Activity.applySystemBarInsets(rootId: Int, statusScrimId: Int) {
    val root = findViewById<View>(rootId)
    val scrim = findViewById<View>(statusScrimId)
    ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        scrim.layoutParams = scrim.layoutParams.apply { height = bars.top }
        v.setPadding(bars.left, 0, bars.right, bars.bottom)
        insets
    }
}
