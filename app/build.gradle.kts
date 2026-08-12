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
        versionCode = 29
        versionName = "0.3.0-alpha27"

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
    }

    buildFeatures {
        compose = true
        buildConfig = true
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
            include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
            // A universal APK would bundle all four native cores (~230 MB). Publish
            // the matching ABI split instead; Android Studio/Play can select it.
            isUniversalApk = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.06.00"))
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
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
    implementation("com.google.zxing:core:3.5.4")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
}

dependencyLocking {
    lockAllConfigurations()
}
