plugins { id("voice.library") }

dependencies {
  implementation(projects.core.audiobookshelf.api)
  implementation(projects.core.common)
  implementation(projects.core.data.api)
  implementation(projects.core.initializer)
  implementation(projects.core.scanner)

  implementation(libs.okhttp)
}
