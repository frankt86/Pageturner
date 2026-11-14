plugins {
  id("voice.library")
}

dependencies {
  implementation(projects.core.common)
  implementation(projects.core.data.api)
  implementation(projects.core.featureflag)

  implementation(libs.okhttp)
}
