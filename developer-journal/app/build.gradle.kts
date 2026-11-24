plugins { id("com.android.application") }
android {
 namespace = "com.developer.journal"
 compileSdk = 35
 defaultConfig {
  applicationId = "com.developer.journal"
  minSdk = 26
  targetSdk = 34
  versionCode = 1
  versionName = "1.0.0"
 }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
