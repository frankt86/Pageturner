package voice.features.folderPicker.addcontent

import android.net.Uri
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import voice.core.analytics.api.Analytics
import voice.core.data.folders.AudiobookFolders
import voice.core.data.folders.FolderType
import voice.features.folderPicker.folderPicker.FileTypeSelection
import voice.navigation.Destination
import voice.navigation.Destination.OnboardingCompletion
import voice.navigation.Destination.SelectFolderType
import voice.navigation.Navigator
import voice.navigation.Origin

@AssistedInject
class AddContentViewModel(
  private val audiobookFolders: AudiobookFolders,
  private val navigator: Navigator,
  private val analytics: Analytics,
  @Assisted private val origin: Origin,
) {

  internal fun add(
    uri: Uri,
    type: FileTypeSelection,
  ) {
    when (type) {
      FileTypeSelection.File -> {
        audiobookFolders.add(uri, FolderType.SingleFile)
        when (origin) {
          Origin.Default -> {
            navigator.setRoot(Destination.BookOverview)
          }
          Origin.Onboarding -> {
            navigator.goTo(OnboardingCompletion)
          }
        }
      }
      FileTypeSelection.Folder -> {
        navigator.goTo(
          SelectFolderType(
            uri = uri,
            origin = origin,
          ),
        )
      }
    }
  }

  internal fun onFolderPickerCancel() {
    analytics.event("folder_picker_cancelled")
  }

  internal fun onHelpAnswer(answer: FolderHelpAnswer) {
    analytics.event("folder_help_answer", mapOf("answer" to answer.value()))
  }

  internal fun openLibriVox() {
    navigator.goTo(Destination.Website("https://librivox.org"))
  }

  private fun FolderHelpAnswer.value(): String {
    return when (this) {
      FolderHelpAnswer.NoFiles -> "no_files"
      FolderHelpAnswer.NotFound -> "not_found"
      FolderHelpAnswer.OtherApp -> "other_app"
    }
  }

  internal fun openCatalogs() {
    navigator.goTo(Destination.OpdsCatalogs)
  }

  internal fun back() {
    navigator.goBack()
  }

  @AssistedFactory
  interface Factory {
    fun create(origin: Origin): AddContentViewModel
  }
}
