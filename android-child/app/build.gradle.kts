plugins { 
    id("com.android.application")
}

android { 
    namespace = "com.example.parentalchild"
    compileSdk = 36
    defaultConfig { 
        applicationId = "com.example.parentalchild"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "API_URL", "\"http://10.0.2.2:3000\"")
        buildConfigField("String", "DEVICE_SECRET", "\"change-me\"")
    }
    buildFeatures { buildConfig = true }
    buildTypes { 
        release { isMinifyEnabled = false }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-ktx:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
