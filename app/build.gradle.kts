plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// Optional personal signing. Keep passwords outside the repository and Gradle command line.
val releaseSigning = listOf("TTS_KEYSTORE_PATH", "TTS_KEYSTORE_PASSWORD", "TTS_KEY_ALIAS", "TTS_KEY_PASSWORD")
    .associateWith { providers.environmentVariable(it).orNull }
val hasReleaseSigning = releaseSigning.values.all { !it.isNullOrBlank() }
require(hasReleaseSigning || releaseSigning.values.all { it.isNullOrBlank() }) {
    "Release signing requires all four TTS_KEYSTORE_PATH, TTS_KEYSTORE_PASSWORD, TTS_KEY_ALIAS, TTS_KEY_PASSWORD variables."
}

android {
    namespace = "com.trangnx.ttsbrowser"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.trangnx.ttsbrowser"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("personalRelease") {
                storeFile = rootProject.file(releaseSigning.getValue("TTS_KEYSTORE_PATH")!!)
                storePassword = releaseSigning.getValue("TTS_KEYSTORE_PASSWORD")
                keyAlias = releaseSigning.getValue("TTS_KEY_ALIAS")
                keyPassword = releaseSigning.getValue("TTS_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("personalRelease")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.okhttp)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.org.json)
}
