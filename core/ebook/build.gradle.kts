plugins {
  id("voice.library")
}

dependencies {
  implementation(libs.androidxCore)
  implementation(libs.jsoup)
  implementation(libs.pdfium)
  implementation(libs.coroutines.core)
}
