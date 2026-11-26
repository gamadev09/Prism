plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val sideBySidePreview = providers.gradleProperty("liquidySideBySide").map(String::toBoolean).orElse(false)

android {
    namespace = "com.gamadev.prism"
    compileSdk = 37

    defaultConfig {
        val preview = sideBySidePreview.get()
        applicationId = if (preview) "com.gamadev.prism.preview" else "com.gamadev.prism"
        manifestPlaceholders["liquidyLabel"] = if (preview) "Prism Performance" else "Prism"
        // 33: RenderEffect blur (31) + AGSL RuntimeShader (33), see docs/ARCHITECTURE.md
        minSdk = 33
        targetSdk = 36
        versionCode = 17
        versionName = "1.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Preview builds use the local development key. Distributions require a retained signing key.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint {
        // Compose lint's ModifierParameter detector crashes with the current Kotlin/AGP toolchain.
        disable += "ModifierParameter"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    // only for the debug panel (sliders), not for the home screen itself
    implementation(libs.compose.material3)
    implementation(libs.backdrop)
    implementation(libs.kyant.shapes)
}
