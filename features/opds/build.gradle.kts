plugins { id("voice.feature") }

dependencies {
  implementation(projects.core.data.api)
  implementation(projects.core.opds)

  implementation(libs.coil)
  implementation(libs.navigation3.ui)
}
