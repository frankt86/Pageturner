plugins {
  id("voice.library")
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.metro)
}

dependencies {
  implementation(projects.core.audiobookshelf.api)
  implementation(projects.core.common)
  implementation(projects.core.data.api)
  implementation(projects.core.initializer)
  implementation(projects.core.scanner)

  implementation(libs.androidxCore)
  implementation(libs.okhttp)
  implementation(libs.serialization.json)
}
