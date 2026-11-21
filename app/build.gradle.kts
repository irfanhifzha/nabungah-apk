import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File
import java.util.Properties

// Local release signing: CI uses env vars, this machine can use the gitignored properties file.
val keystoreProperties = rootProject.file("keystore.properties")
    .takeIf { it.exists() }
    ?.let { file -> Properties().apply { file.inputStream().use { load(it) } } }

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.nabungah"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nabungah"
        minSdk = 24
        targetSdk = 35
        versionCode = 3
        versionName = "1.3"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // CI supplies a release keystore through env vars; local builds without it fall back to
    // the debug key so `assembleRelease` stays installable.
    signingConfigs {
        val keystorePath = System.getenv("NABUNGAH_KEYSTORE_PATH")
            ?: keystoreProperties?.getProperty("storeFile")
        val keystoreFile = keystorePath?.takeIf { it.isNotBlank() }?.let { path ->
            val file = File(path)
            if (file.isAbsolute) file else rootProject.file(path)
        }
        if (keystoreFile != null && keystoreFile.exists()) {
            create("release") {
                storeFile = keystoreFile
                storePassword = System.getenv("NABUNGAH_KEYSTORE_PASSWORD")
                    ?: keystoreProperties?.getProperty("storePassword")
                keyAlias = System.getenv("NABUNGAH_KEY_ALIAS")
                    ?: keystoreProperties?.getProperty("keyAlias")
                keyPassword = System.getenv("NABUNGAH_KEY_PASSWORD")
                    ?: keystoreProperties?.getProperty("keyPassword")
                    ?: storePassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug")
        }
    }

    // Name artifacts after the app, not the module: nabungah-debug.apk / nabungah-release.apk.
    applicationVariants.all {
        val variantName = name
        outputs.all {
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName =
                "nabungah-$variantName.apk"
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

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    testImplementation("junit:junit:4.13.2")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core:1.6.1")
}
