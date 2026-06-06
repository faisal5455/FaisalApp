import groovy.json.JsonSlurper

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// Per-app identity (package, version, name) is injected from the pushed
// app_settings.json so each build produces its own installable app. Missing
// values fall back to the template defaults.
val appSettings: Map<*, *> = file("src/main/assets/app_settings.json").let { f ->
    if (f.exists()) JsonSlurper().parse(f) as Map<*, *> else emptyMap<String, Any?>()
}
val cfgPackage = (appSettings["package"] as? String)?.takeIf { it.isNotBlank() } ?: "com.web2app"
val cfgVersionName = (appSettings["versionName"] as? String)?.takeIf { it.isNotBlank() } ?: "1.0"
val cfgVersionCode = (appSettings["versionCode"] as? Number)?.toInt() ?: 1
val cfgAppName = (appSettings["appName"] as? String)?.takeIf { it.isNotBlank() } ?: "My App"

android {
    // Code/resource package stays fixed; only the installed applicationId varies.
    namespace = "com.web2app"
    compileSdk = 34

    defaultConfig {
        applicationId = cfgPackage
        minSdk = 21
        targetSdk = 34
        versionCode = cfgVersionCode
        versionName = cfgVersionName

        // Drives android:label so the launcher shows the user's app name.
        manifestPlaceholders["appLabel"] = cfgAppName
    }

    // Fixed signing key committed to the repo so every cloud build is signed with
    // the SAME key. Without this, each CI run regenerates the debug keystore →
    // different signature → "package conflicts with an existing package" on update.
    signingConfigs {
        create("shared") {
            storeFile = file("signing/web2app.keystore")
            storePassword = "android"
            keyAlias = "web2app"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("shared")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.swiperefreshlayout)
}
