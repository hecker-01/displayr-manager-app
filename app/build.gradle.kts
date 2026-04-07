plugins {
    alias(libs.plugins.android.application)
}

val gitCommitCount = providers.exec {
    commandLine("git", "rev-list", "--count", "HEAD")
}.standardOutput.asText.get().trim().toInt()

android {
    namespace = "app.displayr.manager"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.displayr.manager"
        minSdk = 31
        targetSdk = 36
        versionCode = gitCommitCount
        versionName = "26.1.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("cordium-dev.keystore")
            storePassword = "CordiumPassw"
            keyAlias = "cordium"
            keyPassword = "CordiumPassw"
        }
        create("release") {
            storeFile = file("cordium-release.keystore")
            storePassword = "CordiumPassw"
            keyAlias = "cordium"
            keyPassword = "CordiumPassw"
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
}

androidComponents {
    onVariants { variant ->
        val versionName = android.defaultConfig.versionName ?: "0.0.0"
        val apkFileName = when (variant.buildType) {
            "debug" -> "displayr-dev-${versionName.replace(Regex("-.*"), "").replace(".", "-")}.apk"
            "release" -> "displayr-release-${versionName.replace(".", "-")}.apk"
            else -> "displayr-${variant.name}.apk"
        }
        variant.outputs.forEach { output ->
            if (output is com.android.build.api.variant.impl.VariantOutputImpl) {
                output.outputFileName = apkFileName
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.navigation.fragment.ktx)
    implementation(libs.navigation.ui.ktx)
    implementation(libs.json)
    implementation(libs.markwon)
    implementation(libs.mlkit.barcode)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
