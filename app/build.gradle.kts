import org.gradle.api.tasks.bundling.AbstractArchiveTask

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Release signing (CI on tags): enabled only when all four variables are present.
// Without them assembleRelease still produces the unsigned APK (local builds, PR CI).
val releaseSigningEnv: Map<String, String>? = listOf(
    "SCROLLESS_KEYSTORE_PATH",
    "SCROLLESS_KEYSTORE_PASSWORD",
    "SCROLLESS_KEY_ALIAS",
    "SCROLLESS_KEY_PASSWORD",
).associateWith { System.getenv(it).orEmpty() }
    .takeIf { env -> env.values.all { it.isNotBlank() } }

android {
    namespace = "com.scrolless.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.scrolless.app"
        minSdk = 26
        targetSdk = 35
        // CI stamps these from the tag: -PversionName=1.2.3 -PversionCode=42
        versionCode = (findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = (findProperty("versionName") as String?) ?: "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseSigningEnv != null) {
            create("release") {
                storeFile = file(releaseSigningEnv.getValue("SCROLLESS_KEYSTORE_PATH"))
                storePassword = releaseSigningEnv.getValue("SCROLLESS_KEYSTORE_PASSWORD")
                keyAlias = releaseSigningEnv.getValue("SCROLLESS_KEY_ALIAS")
                keyPassword = releaseSigningEnv.getValue("SCROLLESS_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            if (releaseSigningEnv != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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

    testOptions {
        // LocalStoreMigrationTest builds a fake Context on top of the android.jar stubs.
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// Reproducible builds: strip non-deterministic timestamps and enforce stable entry ordering
tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
}
