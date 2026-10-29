plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose") }
val apiUrl = providers.gradleProperty("TRIEPS_API_URL").orElse("https://api.trieps.example/api/v1/")
val googleId = providers.gradleProperty("TRIEPS_GOOGLE_CLIENT_ID").orElse("")
android {
 namespace = "id.trieps.app"
 compileSdk = 36
 defaultConfig {
  applicationId = "id.trieps.app"; minSdk = 26; targetSdk = 36; versionCode = 1; versionName = "1.0.0"
  buildConfigField("String", "API_URL", "\"${apiUrl.get()}\"")
  buildConfigField("String", "GOOGLE_CLIENT_ID", "\"${googleId.get()}\"")
  testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
 }
 buildFeatures { compose = true; buildConfig = true }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
 buildTypes { release { isMinifyEnabled = false } }
}
dependencies {
 implementation(platform("androidx.compose:compose-bom:2025.10.01"))
 implementation("androidx.activity:activity-compose:1.11.0")
 implementation("androidx.compose.material3:material3")
 implementation("androidx.compose.material:material-icons-extended")
 implementation("androidx.compose.ui:ui-tooling-preview")
 debugImplementation("androidx.compose.ui:ui-tooling")
 implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
 implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
 implementation("androidx.work:work-runtime-ktx:2.10.5")
 implementation("com.google.android.gms:play-services-location:21.3.0")
 implementation("androidx.credentials:credentials:1.5.0")
 implementation("androidx.credentials:credentials-play-services-auth:1.5.0")
 implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
 implementation("com.squareup.okhttp3:okhttp:4.12.0")
 implementation("io.coil-kt:coil-compose:2.7.0")
 implementation("io.livekit:livekit-android:2.20.1")
 implementation("org.osmdroid:osmdroid-android:6.1.20")
 testImplementation("junit:junit:4.13.2")
}
