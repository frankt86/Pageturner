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
