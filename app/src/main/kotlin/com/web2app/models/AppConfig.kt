package com.web2app.models

import org.json.JSONArray
import org.json.JSONObject

data class AppConfig(
    val id: String = "",
    val appName: String = "Web App",
    val appIcon: String = "",
    val packageName: String = "com.web2app",
    val versionName: String = "1.0",
    val versionCode: Int = 1,
    val websiteURL: String = "https://appofweb.com",
    val themeColor: String = "#FFFFFF",
    val splash: Splash = Splash(),
    val showTabbar: Boolean = false,
    val tabbarStyle: Int = 0,
    val tabbars: List<TabbarItem> = listOf(),
    val tabSettings: TabSettings = TabSettings(),
    val permissions: List<Int> = listOf(),
    val pushNotification: Boolean = false,
    val playStore: Boolean = false,
    val appStore: Boolean = false
)

data class Splash(
    val color: String = "#FFFFFF",
    val image: String = "",
    val size: Int = 30,
    val loader: Int = 0
)

data class TabSettings(
    val tabActiveColor: String = "#1976D2",
    val tabInactiveColor: String = "#888888",
    val tabBarColor: String = "#ffffff",
    val showTitles: Boolean = true
)

data class TabbarItem(
    val icon: String = "",
    val title: String = "",
    val url: String = ""
)

fun parseAppConfig(json: JSONObject): AppConfig {
    // Parse tabbars
    val tabbars = mutableListOf<TabbarItem>()
    val tabbarsArray = json.optJSONArray("tabbars")
    if (tabbarsArray != null) {
        for (i in 0 until tabbarsArray.length()) {
            val t = tabbarsArray.optJSONObject(i) ?: continue
            tabbars.add(
                TabbarItem(
                    icon = t.optString("icon", ""),
                    title = t.optString("title", ""),
                    url = t.optString("url", "")
                )
            )
        }
    }

    // Parse tabSettings
    val tsObj = json.optJSONObject("tabSettings") ?: JSONObject()
    val tabSettings = TabSettings(
        tabActiveColor = tsObj.optString("tabActiveColor", "#1976D2"),
        tabInactiveColor = tsObj.optString("tabInactiveColor", "#888888"),
        tabBarColor = tsObj.optString("tabBarColor", "#ffffff"),
        showTitles = tsObj.optBoolean("showTitles", true)
    )

    // Parse splash
    val splashObj = json.optJSONObject("splash") ?: JSONObject()
    val splash = Splash(
        color = splashObj.optString("color", "#ffffff"),
        image = splashObj.optString("image", ""),
        size = splashObj.optInt("size", 30),
        loader = splashObj.optInt("loader", 0)
    )

    // Parse permissions array
    val permissionsArray = json.optJSONArray("permissions")
    val permissions = mutableListOf<Int>()
    if (permissionsArray != null) {
        for (i in 0 until permissionsArray.length()) {
            val p = permissionsArray.optInt(i, -1)
            if (p >= 0) permissions.add(p)
        }
    }

    return AppConfig(
        id = json.optString("id", ""),
        appName = json.optString("appName", "Web App"),
        appIcon = json.optString("appIcon", ""),
        packageName = json.optString("package", "com.web2app"),
        versionName = json.optString("versionName", "1.0"),
        versionCode = json.optInt("versionCode", 1),
        websiteURL = json.optString("websiteUrl", "https://appofweb.com"),
        themeColor = json.optString("themeColor", "#FFFFFF"),
        splash = splash,
        showTabbar = json.optBoolean("showTabbar", false),
        tabbarStyle = json.optInt("tabbarStyle", 0),
        tabbars = tabbars,
        tabSettings = tabSettings,
        permissions = permissions,
        pushNotification = json.optBoolean("pushNotification", false),
        playStore = json.optBoolean("playStore", false),
        appStore = json.optBoolean("appStore", false)
    )
}

/**
 * Serialize an AppConfig back to a JSONObject (inverse of parseAppConfig).
 * Used to persist builder output into SharedPreferences.
 */
fun appConfigToJson(config: AppConfig): JSONObject {
    val obj = JSONObject()
    obj.put("id", config.id)
    obj.put("appName", config.appName)
    obj.put("appIcon", config.appIcon)
    obj.put("package", config.packageName)
    obj.put("versionName", config.versionName)
    obj.put("versionCode", config.versionCode)
    obj.put("websiteUrl", config.websiteURL)
    obj.put("themeColor", config.themeColor)

    val splashObj = JSONObject()
    splashObj.put("color", config.splash.color)
    splashObj.put("image", config.splash.image)
    splashObj.put("size", config.splash.size)
    splashObj.put("loader", config.splash.loader)
    obj.put("splash", splashObj)

    obj.put("showTabbar", config.showTabbar)
    obj.put("tabbarStyle", config.tabbarStyle)

    val tabbarsArray = JSONArray()
    for (tab in config.tabbars) {
        val t = JSONObject()
        t.put("icon", tab.icon)
        t.put("title", tab.title)
        t.put("url", tab.url)
        tabbarsArray.put(t)
    }
    obj.put("tabbars", tabbarsArray)

    val tsObj = JSONObject()
    tsObj.put("tabActiveColor", config.tabSettings.tabActiveColor)
    tsObj.put("tabInactiveColor", config.tabSettings.tabInactiveColor)
    tsObj.put("tabBarColor", config.tabSettings.tabBarColor)
    tsObj.put("showTitles", config.tabSettings.showTitles)
    obj.put("tabSettings", tsObj)

    val permsArray = JSONArray()
    for (p in config.permissions) permsArray.put(p)
    obj.put("permissions", permsArray)

    obj.put("pushNotification", config.pushNotification)
    obj.put("playStore", config.playStore)
    obj.put("appStore", config.appStore)
    return obj
}
