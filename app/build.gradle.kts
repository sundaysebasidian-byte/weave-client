plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.weave.client"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.weave.client"
        minSdk = 26
        targetSdk = 36
        // Local preview build; keep the application ID/signature for a data-preserving update.
        versionCode = 89
        versionName = "0.3.0-alpha83"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        externalNativeBuild {
            cmake {
                arguments += "-DANDROID_STL=none"
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
        }
        // A locally installable, optimized build for device profiling. It has the same R8 and
        // resource-shrinking settings as release, but uses the debug certificate so it can be
        // installed over the development APK without requiring a signing key in this repository.
        create("localOptimized") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = false
            matchingFallbacks += listOf("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        // Runtime caches are not distribution assets, even if a local core test created one.
        ignoreAssetsPattern = "${ignoreAssetsPattern.orEmpty()}:cache.db"
    }

    ndkVersion = "29.0.14206865"

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.31.6"
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            val configuredAbis = if (
                providers.gradleProperty("weaveArm64Only").orNull == "true"
            ) {
                listOf("arm64-v8a")
            } else {
                listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
            }
            include(*configuredAbis.toTypedArray())
            // A universal APK would bundle all four native cores (~230 MB). Publish
            // the matching ABI split instead; Android Studio/Play can select it. GitHub
            // maintainers can pass -PweaveArm64Only=true for a phone-only artifact.
            isUniversalApk = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        // The pinned Go/Mihomo core is highly compressible (~47 MB -> ~17 MB on arm64). Let the
        // package manager extract it at install time: GitHub APKs stay small and OEM linkers get
        // ordinary filesystem libraries instead of relying on direct APK mmap support.
        jniLibs.useLegacyPackaging = true
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    // 1.19.0 / Lifecycle 2.11.0 require compileSdk 37. Keep SDK 36 compatible
    // versions until API 37 is installed on the build and device-test matrix.
    implementation("androidx.core:core:1.18.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("com.google.zxing:core:3.5.4")
    // On-device live QR preview/analysis. No image capture, cloud decoder or downloaded model.
    implementation("androidx.camera:camera-camera2:1.5.3")
    implementation("androidx.camera:camera-lifecycle:1.5.3")
    implementation("androidx.camera:camera-view:1.5.3")
    // Safe YAML data parsing for nested Clash providers; never construct arbitrary Java objects.
    implementation("org.yaml:snakeyaml:2.5")

    androidTestImplementation(platform("androidx.compose:compose-bom:2026.09.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
}

dependencyLocking {
    lockAllConfigurations()
}
