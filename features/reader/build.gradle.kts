plugins { id("voice.feature") }

dependencies {
  implementation(projects.core.data.api)
  implementation(projects.core.ebook)
  implementation(projects.core.opds)

  implementation(libs.navigation3.ui)
  implementation(libs.commonsCompress)
  implementation(libs.fragment)
  implementation(libs.readium.shared)
  implementation(libs.readium.streamer)
  implementation(libs.readium.navigator) {
    // only its EPUB navigator is used, its audio parts would move the player to another media3
    // version
    exclude(group = "androidx.media3")
  }
}

android {
  defaultConfig { testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
  compileOptions { isCoreLibraryDesugaringEnabled = true }
  // the arm64 voice runs translated on the x86_64 emulator only when the APK has no x86 libraries
  packaging {
    jniLibs.excludes += listOf("lib/x86/**", "lib/x86_64/**", "lib/armeabi-v7a/**")
  }
}

dependencies {
  androidTestImplementation(libs.androidX.test.junit)
  androidTestImplementation("androidx.test:runner:1.7.0")
  coreLibraryDesugaring(libs.desugarJdkLibs)
}
