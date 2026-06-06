package com.web2app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import com.web2app.models.parseAppConfig

/**
 * Launcher entry point. Reads the app configuration from assets/app_settings.json,
 * shows the configured splash screen (background colour + image + loader) for a
 * short moment, then opens [MainActivity].
 */
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val config = parseAppConfig(readJson(this, "app_settings.json"))

        val root = findViewById<View>(R.id.splashRoot)
        val image = findViewById<ImageView>(R.id.splashImage)
        val loader = findViewById<ProgressBar>(R.id.splashLoader)

        // Background colour
        root.setBackgroundColor(safeColor(config.splash.color, Color.WHITE))

        // Splash image: configured base64 splash image, else base64 app icon,
        // else fall back to the launcher icon drawable.
        val imageData = config.splash.image.takeIf { it.isNotBlank() }
            ?: config.appIcon.takeIf { it.isNotBlank() }
        val bitmap = imageData?.let { Base64ImageUtil.base64ToBitmap(it) }
        if (bitmap != null) {
            image.setImageBitmap(bitmap)
        } else {
            image.setImageResource(R.drawable.app_icon)
        }
        image.visibility = View.VISIBLE

        loader.visibility = if (config.splash.loader == 0) View.GONE else View.VISIBLE

        Handler(Looper.getMainLooper()).postDelayed({
            if (isFinishing) return@postDelayed
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }, SPLASH_DURATION_MS)
    }

    private fun safeColor(hex: String?, default: Int): Int {
        return try {
            if (hex.isNullOrBlank()) default else Color.parseColor(hex)
        } catch (e: Exception) {
            default
        }
    }

    companion object {
        private const val SPLASH_DURATION_MS = 1800L
    }
}
