plugins {
  id("com.android.test")
  alias(libs.plugins.baselineProfile)
  id("voice.ktfmt")
}

android {
  namespace = "voice.baselineprofile"
  val compileSdkVersion = libs.versions.sdk.compile.get().split(".")
  compileSdk {
    version =
      release(compileSdkVersion[0].toInt()) {
        minorApiLevel = compileSdkVersion.getOrNull(1)?.toInt()
      }
  }
  targetProjectPath = ":app"

  defaultConfig {
    minSdk = libs.versions.sdk.min.get().toInt()
    targetSdk = libs.versions.sdk.target.get().toInt()
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  flavorDimensions += "distribution"
  productFlavors { register("free") { dimension = "distribution" } }

  compileOptions {
    val bytecode = JavaVersion.toVersion(libs.versions.jvm.bytecode.get())
    sourceCompatibility = bytecode
    targetCompatibility = bytecode
  }

  testOptions.managedDevices.localDevices.register("baselineProfileDevice") {
    device = "Pixel 6"
    // From API 34 the profile rule reinstalls the app before it records, which removes the library.
    apiLevel = 33
    systemImageSource = "google"
  }
}

kotlin {
  jvmToolchain(libs.versions.jvm.toolchain.get().toInt())
}

baselineProfile {
  managedDevices += "baselineProfileDevice"
  // The test installs its own build of the app, so it must never run on a listener's phone.
  useConnectedDevices = false
}

// The app id changes with the voice.applicationId and voice.applicationIdSuffix properties.
androidComponents {
  onVariants { variant ->
    val artifactsLoader = variant.artifacts.getBuiltArtifactsLoader()
    variant.instrumentationRunnerArguments.put(
      "targetAppId",
      variant.testedApks.map { checkNotNull(artifactsLoader.load(it)).applicationId },
    )
  }
}

dependencies {
  implementation(libs.androidX.test.junit)
  implementation(libs.uiautomator)
  implementation(libs.benchmark.macro)
}
