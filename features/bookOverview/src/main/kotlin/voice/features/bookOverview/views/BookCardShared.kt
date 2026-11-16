@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package voice.features.bookOverview.views

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import voice.core.data.BookId
import voice.core.strings.R as StringsR
import voice.core.ui.R as UiR
import voice.core.ui.rememberCoverThumbnailRequest
import voice.core.ui.sharedCoverElementModifier

/** Percentage based so covers keep their look while shared element transitions resize them. */
internal val CoverShape = RoundedCornerShape(percent = 12)

@Composable
internal fun BookCover(
  bookId: BookId,
  cover: String?,
  modifier: Modifier = Modifier,
  shape: Shape = CoverShape,
  finished: Boolean = false,
  hidden: Boolean = false,
  selected: Boolean? = null,
  ebook: Boolean = false,
) {
  Box(modifier = modifier) {
    AsyncImage(
      modifier = Modifier.fillMaxSize().sharedCoverElementModifier(bookId).clip(shape),
      model = rememberCoverThumbnailRequest(cover),
      // a soft fill the cover fades in on, instead of the dark default art flashing up
      placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceContainerHighest),
      error = painterResource(id = UiR.drawable.album_art),
      contentScale = ContentScale.Crop,
      contentDescription = null,
    )
    if (ebook) {
      CoverBadge(
        icon = UiR.drawable.ic_book,
        contentDescription = stringResource(StringsR.string.library_book_ebook),
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.align(Alignment.TopStart).offset(x = (-4).dp, y = (-4).dp),
      )
    }
    // one badge per cover, being finished matters least
    val badgeModifier = Modifier.align(Alignment.BottomEnd).offset(x = 4.dp, y = 4.dp)
    val colors = MaterialTheme.colorScheme
    when {
      hidden ->
        CoverBadge(
          icon = UiR.drawable.ic_visibility_off,
          contentDescription = stringResource(StringsR.string.library_book_hidden),
          containerColor = colors.surfaceContainerHighest,
          contentColor = colors.onSurfaceVariant,
          modifier = badgeModifier,
        )
      finished ->
        CoverBadge(
          icon = UiR.drawable.ic_check,
          contentDescription = null,
          containerColor = colors.primary,
          contentColor = colors.onPrimary,
          modifier = badgeModifier,
        )
    }
    SelectionBadge(selected)
  }
}

/** In the place of an audiobook's play button, it opens an ebook in the reader. */
@Composable
internal fun ReadButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  size: Dp = 56.dp,
) {
  val shape = MaterialShapes.Cookie9Sided.toShape()
  Box(
    modifier =
      modifier
        .size(size)
        .clip(shape)
        .background(MaterialTheme.colorScheme.primary)
        .clickable(role = Role.Button, onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      modifier = Modifier.fillMaxSize(0.4F),
      painter = painterResource(UiR.drawable.ic_book),
      contentDescription = stringResource(StringsR.string.opds_entry_read),
      tint = MaterialTheme.colorScheme.onPrimary,
    )
  }
}

@Composable
private fun CoverBadge(
  @DrawableRes icon: Int,
  contentDescription: String?,
  containerColor: Color,
  contentColor: Color,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier =
      modifier
        .size(24.dp)
        .background(MaterialTheme.colorScheme.surface, CircleShape)
        .padding(2.dp)
        .background(containerColor, CircleShape),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      modifier = Modifier.size(14.dp),
      painter = painterResource(icon),
      contentDescription = contentDescription,
      tint = contentColor,
    )
  }
}
