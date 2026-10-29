package voice.features.bookOverview.ebookActions

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue.Expanded
import androidx.compose.material3.SheetValue.Hidden
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.retain.retain
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavEntry
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import voice.core.common.rootGraphAs
import voice.core.ui.CoverTheme
import voice.features.bookOverview.bottomSheet.BookActionsContent
import voice.features.bookOverview.bottomSheet.BottomSheetItem
import voice.features.bookOverview.editBook.EditBookSheet
import voice.navigation.Destination
import voice.navigation.NavEntryProvider
import voice.navigation.OverlayNav

@ContributesTo(AppScope::class)
interface EbookActionsGraph {
  val ebookActionsViewModelFactory: EbookActionsViewModel.Factory
}

@BindingContainer
@ContributesTo(AppScope::class)
object EbookActionsProvider {

  @Provides
  @IntoSet
  fun ebookActionsNavEntryProvider(): NavEntryProvider<*> = NavEntryProvider<Destination.EbookActions> { key ->
    NavEntry(key, metadata = OverlayNav.overlay()) {
      EbookActions(key.uri)
    }
  }
}

private val EbookItems = listOf(
  BottomSheetItem.Edit,
  BottomSheetItem.InternetCover,
  BottomSheetItem.FileCover,
  BottomSheetItem.RemoveFromLibrary,
)

/** Like the menu of an audiobook, it closes once its last sheet is gone. */
@Composable
private fun EbookActions(uri: Uri) {
  val viewModel = retain(uri.toString()) {
    rootGraphAs<EbookActionsGraph>().ebookActionsViewModelFactory.create(uri)
  }
  val scope = rememberCoroutineScope()
  var showMenu by rememberSaveable { mutableStateOf(true) }
  var pickingCover by rememberSaveable { mutableStateOf(false) }
  fun closeIfDone() {
    if (!showMenu && !pickingCover && viewModel.editState == null) {
      viewModel.onClose()
    }
  }
  LaunchedEffect(Unit) {
    closeIfDone()
  }

  val getContentLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.GetContent(),
    onResult = { image ->
      pickingCover = false
      if (image != null) {
        viewModel.onCoverPicked(image)
      }
      closeIfDone()
    },
  )

  val editState = viewModel.editState
  if (editState != null) {
    CoverTheme(cover = editState.cover) {
      EditBookSheet(
        viewState = editState,
        onTitleChange = viewModel::onEditTitleChange,
        onAuthorChange = viewModel::onEditAuthorChange,
        onConfirm = {
          viewModel.onEditConfirm()
          closeIfDone()
        },
        onDismiss = {
          viewModel.onEditDismiss()
          closeIfDone()
        },
      )
    }
  }

  val state = viewModel.state()
  if (showMenu && state != null) {
    CoverTheme(cover = state.book.cover) {
      val sheetState = rememberBottomSheetState(
        initialValue = Hidden,
        enabledValues = setOf(Hidden, Expanded),
      )
      var pendingStatusChange by remember { mutableStateOf<Job?>(null) }
      var closing by remember { mutableStateOf(false) }
      fun hideThen(action: () -> Unit) {
        if (closing) return
        closing = true
        scope.launch {
          sheetState.hide()
          action()
          showMenu = false
          closeIfDone()
        }
      }
      ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = {
          if (!closing) {
            pendingStatusChange?.cancel()
            showMenu = false
            closeIfDone()
          }
        },
      ) {
        BookActionsContent(
          book = state.book,
          source = null,
          download = null,
          bookSize = 0,
          category = state.category,
          items = EbookItems,
          onItemClick = { item ->
            when (item) {
              BottomSheetItem.Edit -> hideThen { viewModel.onEdit(state.book) }
              BottomSheetItem.InternetCover -> hideThen(viewModel::onInternetCover)
              BottomSheetItem.FileCover -> if (!closing) {
                pickingCover = true
                getContentLauncher.launch("image/*")
                hideThen {}
              }
              BottomSheetItem.RemoveFromLibrary -> hideThen(viewModel::onRemove)
              else -> Unit
            }
          },
          onStatusChange = { category ->
            pendingStatusChange?.cancel()
            if (category != state.category) {
              pendingStatusChange = scope.launch {
                delay(450)
                hideThen { viewModel.onStatusChange(category) }
              }
            }
          },
        )
      }
    }
  }
}
