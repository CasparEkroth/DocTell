plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}

android {
    namespace = "com.doctell.app"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.doctell.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 13
        versionName = "1.0.13"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            debugSymbolLevel = "SYMBOL_TABLE"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/java")
        }
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    //pdf reader
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    //for saving bookMarks
    implementation("com.google.code.gson:gson:2.10.1")
    //mediaPlayer
    implementation("androidx.media:media:1.7.0")
    //firebase
    implementation(platform("com.google.firebase:firebase-bom:34.6.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-crashlytics")

    implementation(libs.google.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}