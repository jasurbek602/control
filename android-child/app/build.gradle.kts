plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

android { namespace="com.example.parentalchild"; compileSdk=37
    defaultConfig { applicationId="com.example.parentalchild"; minSdk=26; targetSdk=37; versionCode=1; versionName="0.1.0"; buildConfigField("String","API_URL", "\"http://10.0.2.2:3000\""); buildConfigField("String","DEVICE_SECRET", "\"change-me\"") }
    buildTypes { release { isMinifyEnabled=false } }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-ktx:1.12.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.appcompat:appcompat:1.8.0-rc01")
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.camera:camera-core:1.6.1")
    implementation("androidx.camera:camera-camera2:1.6.1")
    implementation("androidx.camera:camera-lifecycle:1.6.1")
    implementation("androidx.camera:camera-view:1.6.1")
    implementation("com.squareup.okhttp3:okhttp:5.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
