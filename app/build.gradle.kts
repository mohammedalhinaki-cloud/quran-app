import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

/**
 * قراءة مفتاح التوقيع.
 * - محليًا: من ملف keystore/keystore.properties
 * - في GitHub Actions: من متغيّرات البيئة SIGNING_STORE_FILE / SIGNING_STORE_PASSWORD /
 *   SIGNING_KEY_ALIAS / SIGNING_KEY_PASSWORD
 */
val keystorePropsFile = rootProject.file("keystore/keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

fun propOrEnv(propKey: String, envKey: String, default: String? = null): String? =
    keystoreProps.getProperty(propKey) ?: System.getenv(envKey) ?: default

val storeFilePath = propOrEnv("storeFile", "SIGNING_STORE_FILE")
val storeFileResolved: java.io.File? = storeFilePath?.let { path ->
    val candidate = file(path)
    if (candidate.exists()) candidate else rootProject.file(path)
}
val storePasswordValue = propOrEnv("storePassword", "SIGNING_STORE_PASSWORD")
val keyAliasValue = propOrEnv("keyAlias", "SIGNING_KEY_ALIAS")
val keyPasswordValue = propOrEnv("keyPassword", "SIGNING_KEY_PASSWORD")
val hasSigningKey = storeFileResolved != null && storePasswordValue != null &&
    keyAliasValue != null && keyPasswordValue != null

android {
    namespace = "com.borhji.mushaf"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.borhji.mushaf"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        if (hasSigningKey) {
            create("release") {
                storeFile = storeFileResolved
                storePassword = storePasswordValue
                keyAlias = keyAliasValue
                keyPassword = keyPasswordValue
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        release {
            // نُبقي تصغير الشيفرة معطّلًا لضمان استقرار التطبيق على كل الأجهزة،
            // ويمكن تفعيله لاحقًا (isMinifyEnabled = true) عند الحاجة لتصغير الحجم.
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (hasSigningKey) signingConfigs.getByName("release") else null
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        abortOnError = false
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Media3 / ExoPlayer
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.common)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
