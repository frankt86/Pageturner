package voice.core.featureflag

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.ElementsIntoSet
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.Qualifier
import dev.zacsweers.metro.SingleIn

@BindingContainer
@ContributesTo(AppScope::class)
object FeatureFlagBindingContainer {

  @Provides
  @SingleIn(AppScope::class)
  @UserAgentFeatureFlagQualifier
  fun userAgentFeatureFlag(factory: FeatureFlagFactory): FeatureFlag<String> {
    return factory.string(
      key = "user_agent",
      description = "Overrides the HTTP user agent used for cover downloads.",
      defaultValue = "Mozilla/5.0",
    )
  }

  @Provides
  @SingleIn(AppScope::class)
  @FolderPickerInSettingsFeatureFlagQualifier
  fun folderPickerInSettingsFeatureFlag(factory: FeatureFlagFactory): FeatureFlag<Boolean> {
    return factory.boolean(
      key = "folder_picker_in_settings",
      description = "Shows the folder picker entry directly in settings.",
      defaultValue = false,
    )
  }

  @Provides
  @SingleIn(AppScope::class)
  @ExperimentalPlaybackPersistenceQualifier
  fun experimentalPlaybackPersistenceQualifier(factory: FeatureFlagFactory): FeatureFlag<Boolean> {
    return factory.boolean(
      key = "experimental_playback_persistence",
      description = "Uses the experimental playback persistence implementation.",
    )
  }

  @Provides
  @SingleIn(AppScope::class)
  @Media3AudioOffloadFeatureFlagQualifier
  fun media3AudioOffloadFeatureFlag(factory: FeatureFlagFactory): FeatureFlag<Boolean> {
    return factory.boolean(
      key = "media3_audio_offload",
      description = "Uses Media3 audio offload when the device supports it.",
    )
  }

  @Provides
  @SingleIn(AppScope::class)
  @KioskModeFeatureFlagQualifier
  fun kioskModeFeatureFlag(factory: FeatureFlagFactory): FeatureFlag<Boolean> {
    return factory.boolean(
      key = "kiosk_mode",
      description = "Shows demo content on the overview, playback, and bookmark screens.",
    )
  }

  @Provides
  @SingleIn(AppScope::class)
  @AudiobookshelfFeatureFlagQualifier
  fun audiobookshelfFeatureFlag(factory: FeatureFlagFactory): FeatureFlag<Boolean> {
    return factory.boolean(
      key = "audiobookshelf",
      description = "Lets listeners connect an Audiobookshelf server. A server that is connected stays, also when this is off.",
    )
  }

  @Provides
  @ElementsIntoSet
  fun featureFlags(
    @FolderPickerInSettingsFeatureFlagQualifier folderPickerInSettings: FeatureFlag<Boolean>,
    @ExperimentalPlaybackPersistenceQualifier experimentalPlaybackPersistence: FeatureFlag<Boolean>,
    @Media3AudioOffloadFeatureFlagQualifier media3AudioOffload: FeatureFlag<Boolean>,
    @KioskModeFeatureFlagQualifier kioskMode: FeatureFlag<Boolean>,
    @AudiobookshelfFeatureFlagQualifier audiobookshelf: FeatureFlag<Boolean>,
  ): Set<FeatureFlag<*>> = setOf(
    folderPickerInSettings,
    experimentalPlaybackPersistence,
    media3AudioOffload,
    kioskMode,
    audiobookshelf,
  )
}

@Qualifier
annotation class UserAgentFeatureFlagQualifier

@Qualifier
annotation class FolderPickerInSettingsFeatureFlagQualifier

@Qualifier
annotation class ExperimentalPlaybackPersistenceQualifier

@Qualifier
annotation class Media3AudioOffloadFeatureFlagQualifier

@Qualifier
annotation class KioskModeFeatureFlagQualifier

@Qualifier
annotation class AudiobookshelfFeatureFlagQualifier
