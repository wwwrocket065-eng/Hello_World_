plugins { id("com.android.application") }
android {
  namespace = "com.example.arkrecruit"
  compileSdk = 35
  defaultConfig {
    applicationId = "com.example.arkrecruit"
    minSdk = 26
    targetSdk = 35
    versionCode = 34
    versionName = "0.3.31"
    ndk { abiFilters += listOf("arm64-v8a") }
    externalNativeBuild { cmake { cppFlags += "-std=c++17" } }
  }
  ndkVersion = "27.2.12479018"
  externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}
dependencies {
  implementation("com.google.mlkit:text-recognition:16.0.1")
}

