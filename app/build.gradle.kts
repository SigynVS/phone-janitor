plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.sigynvs.phonejanitor"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.sigynvs.phonejanitor"
        minSdk = 30
        targetSdk = 36
        versionCode = 4
        versionName = "0.1.3"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // Pinned copy of the debug keystore so self-update signatures always match,
        // even if ~/.android/debug.keystore is regenerated. Not in git.
        create("pinned") {
            val ks = rootProject.file("signing/app-signing.jks")
            if (ks.exists()) {
                storeFile = ks
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            // Personal sideload build: R8 shrinks (material-icons-extended is ~40 MB unshrunk).
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("pinned")?.takeIf { it.storeFile != null }
                ?: signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/NOTICE.md",
                "/META-INF/LICENSE.md",
                "/META-INF/DEPENDENCIES",
                "/META-INF/NOTICE",
                "/META-INF/LICENSE",
                "/META-INF/NOTICE.txt",
                "/META-INF/LICENSE.txt",
            )
            // com.sun.mail: android-mail and android-activation each bundle these service files.
            pickFirsts += setOf(
                "META-INF/mailcap",
                "META-INF/mailcap.default",
                "META-INF/mimetypes.default",
                "META-INF/javamail.charset.map",
                "META-INF/javamail.default.address.map",
                "META-INF/javamail.default.providers",
                "META-INF/javamail.address.map",
                "META-INF/javamail.providers",
            )
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.coil.compose)
    implementation(libs.kotlinx.coroutines.android)

    // Junk Email (Milestone 4): Gmail IMAP over TLS + encrypted app-password store.
    implementation(libs.javax.mail)
    implementation(libs.javax.activation)
}
