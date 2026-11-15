plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
 namespace = "com.footballanalysis.app"
 compileSdk = 35
 defaultConfig { applicationId = "com.footballanalysis.app"; minSdk = 26; targetSdk = 35; versionCode = 3; versionName = "0.3.0" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
}


dependencies {
    val media3Version = "1.5.1"

    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")
    implementation("androidx.media3:media3-transformer:$media3Version")
    implementation("androidx.media3:media3-effect:$media3Version")
}
