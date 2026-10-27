plugins {
  id("voice.library")
  id("voice.compose")
  alias(libs.plugins.metro)
}

android {
  androidResources.enable = true
}

dependencies {
  implementation(projects.navigation)
  implementation(projects.core.common)
  implementation(projects.core.ui)
  implementation(projects.core.strings)
  implementation(projects.core.data.api)
  implementation(projects.core.ebook)
  implementation(projects.core.opds)

  implementation(libs.androidxCore)
  implementation(libs.navigation3.ui)
  implementation(libs.commonsCompress)
  implementation(libs.serialization.json)
  implementation(libs.fragment)
  implementation(libs.readium.shared)
  implementation(libs.readium.streamer)
  implementation(libs.readium.navigator) {
    // only its EPUB navigator is used, its audio parts would move the player to another media3 version
    exclude(group = "androidx.media3")
  }
}
