import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.URI
import java.util.Properties

plugins {
    id("com.android.application")
    id("com.google.gms.google-services")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// --- Version -----------------------------------------------------------------
// Four segments. Bump both together for every release: the tag pushed to
// GitHub is "v" + versionName, and the app compares versionName against the
// latest release to decide whether to show the update popup.
val ctrlapsVersionName = "0.2.1.0"
val ctrlapsVersionCode = 21

// --- Build-time configuration --------------------------------------------------
// Values reach the app through BuildConfig. Each is looked up, in order, as an
// environment variable, a key in the repository's .env file, a key in
// local.properties, then a Gradle property (gradle.properties or -P). Only the
// keys asked for below are read; the database and storage secrets in .env are
// the website's and never reach the APK.
val dotenv = Properties().apply {
    // The shared .env sits at the repository root, one level above android/;
    // an android/.env beside this build, if present, wins over it.
    val file = listOf(rootProject.file(".env"), rootProject.file("../.env")).firstOrNull { it.exists() }
    if (file != null) {
        file.readLines(Charsets.UTF_8).forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#") || !line.contains('=')) return@forEach
            val key = line.substringBefore('=').trim().removePrefix("export ").trim()
            var value = line.substringAfter('=').trim()
            val quoted = value.length >= 2 &&
                ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith('\'') && value.endsWith('\'')))
            if (quoted) value = value.substring(1, value.length - 1)
            if (key.isNotEmpty()) setProperty(key, value)
        }
    }
}
val local = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun setting(envName: String, propName: String, default: String = ""): String =
    (System.getenv(envName)?.takeIf { it.isNotBlank() }
        ?: dotenv.getProperty(envName)?.takeIf { it.isNotBlank() }
        ?: local.getProperty(propName)?.takeIf { it.isNotBlank() }
        ?: project.findProperty(propName)?.toString()?.takeIf { it.isNotBlank() }
        ?: default).trim()

// Release signing: every value comes from the environment / .env (CI, from
// secrets) or local.properties — nothing is hard-coded. Signing is optional: a
// release build with any of the four missing, or no keystore file, is simply
// unsigned.
val keystorePath = setting("CTRLAPS_KEYSTORE_PATH", "ctrlaps.keystore.path")
val keystorePassword = setting("CTRLAPS_KEYSTORE_PASSWORD", "ctrlaps.keystore.password")
val keyAliasName = setting("CTRLAPS_KEY_ALIAS", "ctrlaps.key.alias")
val keyPasswordValue = setting("CTRLAPS_KEY_PASSWORD", "ctrlaps.key.password")
val keystoreFile = if (keystorePath.isEmpty()) null
    else rootProject.file(keystorePath).let { if (it.isAbsolute) it else file(keystorePath) }
val hasSigning = keystoreFile != null && keystoreFile.exists() &&
    keystorePassword.isNotEmpty() && keyAliasName.isNotEmpty() && keyPasswordValue.isNotEmpty()

// The debug build installs beside the release one under its own id — but
// only once the Firebase project knows that id (google-services.json lists
// it), because the Google Services plugin refuses a package it has not seen.
val baseUrl = setting("CTRLAPS_BASE_URL", "ctrlaps.baseUrl").trimEnd('/')
val appLinkHost = runCatching { URI(baseUrl).host }.getOrNull().orEmpty()
tasks.named("preBuild") {
    doFirst {
        if (appLinkHost.isEmpty()) throw GradleException(
            "CTRLAPS_BASE_URL is not set (the server's address, e.g. https://example.com). " +
                "Put it in the root .env, local.properties (ctrlaps.baseUrl) or the environment."
        )
    }
}

val googleServices = file("google-services.json")
val debugSuffixKnown = googleServices.exists() && googleServices.readText().contains("\"com.arkhins.ctrlaps.debug\"")

android {
    namespace = "com.arkhins.ctrlaps"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.arkhins.ctrlaps"
        minSdk = 26
        targetSdk = 35
        versionCode = ctrlapsVersionCode
        versionName = ctrlapsVersionName

        buildConfigField("String", "BASE_URL", "\"$baseUrl\"")
        // The name under the icon: the test app (its own package, beside the live one) says so.
        resValue("string", "app_name", "CTR[L]APS")
        manifestPlaceholders["appLinkHost"] = appLinkHost
        // The site the app shares saved passwords with (its assetlinks.json also lists get_login_creds).
        resValue("string", "asset_statements", "[{\\\"include\\\": \\\"https://$appLinkHost/.well-known/assetlinks.json\\\"}]")
        buildConfigField("String", "UPDATE_URL", "\"${setting("CTRLAPS_UPDATE_URL", "ctrlaps.updateUrl")}\"")
        buildConfigField("String", "GITHUB_REPO", "\"${setting("CTRLAPS_GITHUB_REPO", "ctrlaps.githubRepo")}\"")
        // Who made it and the organisation's domain (About → License), from POWERED_BY_NAME, POWERED_BY_DOMAIN and
        // MAIN_DOMAIN in .env (CI: Actions variables). Blank ones are left out.
        fun text(v: String) = "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        fun domain(v: String) = v.replace(Regex("^https?://", RegexOption.IGNORE_CASE), "").trimEnd('/')
        buildConfigField("String", "POWERED_BY_NAME", text(setting("POWERED_BY_NAME", "ctrlaps.poweredByName")))
        buildConfigField("String", "POWERED_BY_DOMAIN", text(domain(setting("POWERED_BY_DOMAIN", "ctrlaps.poweredByDomain"))))
        buildConfigField("String", "MAIN_DOMAIN", text(domain(setting("MAIN_DOMAIN", "ctrlaps.mainDomain"))))
    }

    signingConfigs {
        if (hasSigning) {
            create("release") {
                storeFile = keystoreFile
                storePassword = keystorePassword
                keyAlias = keyAliasName
                keyPassword = keyPasswordValue
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasSigning) signingConfig = signingConfigs.getByName("release")
        }
        debug {
            if (debugSuffixKnown) {
                applicationIdSuffix = ".debug"
                resValue("string", "app_name", "CTR[L]APS Test")
            }
        }
        // For trying the app on a phone at real speed: optimised like a release (debug builds of
        // Compose run several times slower), but signed and named like the debug build, so it installs
        // over it, keeps its data, and can point at a local server. ./gradlew installFast
        create("fast") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            if (debugSuffixKnown) {
                applicationIdSuffix = ".debug"
                resValue("string", "app_name", "CTR[L]APS Test")
            }
            matchingFallbacks += "release"
        }
    }

    // A release also comes as one smaller APK per processor type (the in-app updater picks the phone's own, about
    // half the size) beside the universal one. Only when a release is being built: debug and fast builds stay one APK.
    splits {
        abi {
            isEnable = gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.06.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    implementation("androidx.navigation:navigation-compose:2.9.0")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    // Home-screen widgets (next session, unread chats, standings), written in Compose.
    implementation("androidx.glance:glance-appwidget:1.2.0")
    // Installs the startup profile (src/main/baseline-prof.txt plus the libraries' own) on phones that get
    // the APK outside the Play Store, so Android pre-compiles the screens instead of starting them slow.
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")

    // Push.
    implementation(platform("com.google.firebase:firebase-bom:34.19.0"))
    implementation("com.google.firebase:firebase-messaging")

    // The QR scanner (camera + barcode reading) and the account QR itself.
    implementation("androidx.camera:camera-core:1.4.2")
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("com.google.zxing:core:3.5.3")

    // Profile photos.
    implementation("io.coil-kt:coil-compose:2.7.0")
    // Pinch and double-tap zoom for the full-screen photo: zooms where the fingers are, stays in bounds, flings.
    implementation("me.saket.telephoto:zoomable-image-coil:0.14.0")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}

// The app's About → License page shows the repository's own LICENSE, copied in at build time.
val legalDir = layout.buildDirectory.dir("generated/legal")
val copyLegal by tasks.registering(Copy::class) {
    from(rootProject.file("../LICENSE")) { rename { "license.txt" } }
    into(legalDir.map { it.dir("raw") })
}
android.sourceSets["main"].res.srcDir(legalDir)
tasks.named("preBuild") { dependsOn(copyLegal) }
