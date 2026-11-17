import java.net.URI

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
// plugin versions come from the root buildscript classpath (see root build.gradle.kts)

// Vishie TV ships as a RELEASE (R8-optimised) build signed with its OWN key.
// The key's master copy lives IN the workspace (signing/debug.keystore) so it
// survives VM swaps. Separate app, separate identity (not the Vishie/Mausam keys).
val tvDebugKeystore: java.io.File? = listOf(
    file("${project.rootDir}/signing/debug.keystore")
).firstOrNull { it.exists() }

android {
    namespace = "com.vishal.vishietv"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.vishal.vishietv"
        minSdk = 26          // Sony Bravia 2015 runs Android 8.0 (API 26)
        targetSdk = 34
        versionCode = 4
        versionName = "1.3"
    }

    signingConfigs {
        create("tvDebugKey") {
            if (tvDebugKeystore != null) {
                storeFile = tvDebugKeystore
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            signingConfig = signingConfigs.getByName("tvDebugKey")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isMinifyEnabled = false
            isDebuggable = true
            signingConfig = signingConfigs.getByName("tvDebugKey")
        }
    }

    // AGP 8.7.2 ka lint is sandbox ke android-37.0 platform folder ko parse
    // nahi kar pata ("For input string: 37.0") — release lint vital band.
    lint {
        checkReleaseBuilds = false
    }

    testOptions {
        unitTests.all {
            it.maxHeapSize = "2048m"
        }
        unitTests.isIncludeAndroidResources = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")

    // Visual QA only (JVM screenshot rendering of the real screens; not in the APK):
    // Robolectric native-graphics view capture (same technique as Vishie/Mausam).
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
}

// Robolectric downloads its android-all runtime through the sandbox egress
// proxy (read from the environment at build time; nothing is written to
// disk) and validates certificates against the JDK's own cacerts — the
// workspace truststore.p12 is incomplete (see tvbuild.sh).
tasks.withType<Test>().configureEach {
    javaLauncher.set(
        javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    )
    val proxyUri = System.getenv("https_proxy")?.let { runCatching { URI(it) }.getOrNull() }
    if (proxyUri != null && proxyUri.host != null) {
        val userInfo = proxyUri.userInfo?.split(":", limit = 2)
        systemProperty("http.proxyHost", proxyUri.host)
        systemProperty("http.proxyPort", proxyUri.port.toString())
        systemProperty("https.proxyHost", proxyUri.host)
        systemProperty("https.proxyPort", proxyUri.port.toString())
        if (userInfo != null && userInfo.size == 2) {
            systemProperty("http.proxyUser", userInfo[0])
            systemProperty("http.proxyPassword", userInfo[1])
            systemProperty("https.proxyUser", userInfo[0])
            systemProperty("https.proxyPassword", userInfo[1])
        }
        systemProperty("http.nonProxyHosts", "localhost")
        systemProperty("https.nonProxyHosts", "localhost")
    }
    systemProperty(
        "javax.net.ssl.trustStore",
        "/home/hatch/workspace/tools/gradle-cacerts.jks"
    )
    systemProperty("javax.net.ssl.trustStorePassword", "changeit")
    systemProperty("javax.net.ssl.trustStoreType", "JKS")
}
