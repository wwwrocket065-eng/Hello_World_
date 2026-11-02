plugins { id("com.android.application") }
android {
  namespace = "com.example.arkrecruit"
  compileSdk = 35
  defaultConfig {
    applicationId = "com.example.arkrecruit"
    minSdk = 26
    targetSdk = 35
    versionCode = 13
    versionName = "0.3.10"
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}
dependencies {
  implementation("com.google.mlkit:text-recognition:16.0.1")
}

