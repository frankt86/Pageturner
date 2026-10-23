@file:OptIn(ExperimentalReadiumApi::class)

package voice.features.reader

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.data.Container
import org.readium.r2.shared.util.resource.InMemoryResource
import org.readium.r2.shared.util.resource.Resource
import voice.core.data.ReaderFont
import voice.core.data.ReaderSettings
import voice.core.logging.api.Logger
import java.io.File
import java.io.IOException
import org.readium.r2.navigator.epub.css.FontStyle as ReadiumFontStyle
import org.readium.r2.navigator.epub.css.FontWeight as ReadiumFontWeight
import org.readium.r2.navigator.preferences.FontFamily as ReadiumFontFamily
import voice.core.ui.R as UiR

/** The fonts the reader added, copied into the app so they stay when the original file goes. */
internal fun fontsFolder(context: Context): File = File(context.filesDir, "fonts")

internal fun importedFonts(context: Context): List<String> {
  return fontsFolder(context).listFiles()?.filter { it.isFile }?.map { it.name }?.sorted().orEmpty()
}

/** What the list of fonts calls an added font. */
internal fun fontLabel(fileName: String): String = fileName.substringBeforeLast('.').replace('_', ' ')

/** Copies the font at [uri] into the app and returns its file name, or null when it's no font Android can use. */
internal fun importFont(
  context: Context,
  uri: Uri,
): String? {
  val temporary = File(context.cacheDir, "font_import")
  return try {
    val displayName = context.contentResolver
      .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
      ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
      ?: return null
    val extension = displayName.substringAfterLast('.', "").lowercase()
    if (extension != "ttf" && extension != "otf") return null
    // only plain characters, so the name works as a path and in the new engine's font address
    val name = displayName.substringBeforeLast('.')
      .replace(Regex("[^A-Za-z0-9-]+"), "_")
      .trim('_')
      .take(60)
      .ifEmpty { "font" } + ".$extension"
    val input = context.contentResolver.openInputStream(uri) ?: return null
    input.use { source -> temporary.outputStream().use { source.copyTo(it) } }
    val typeface: Typeface? = Typeface.Builder(temporary).build()
    if (typeface == null) return null
    val folder = fontsFolder(context)
    folder.mkdirs()
    temporary.copyTo(File(folder, name), overwrite = true)
    name
  } catch (e: Exception) {
    Logger.w(e, "Could not add a font")
    null
  } finally {
    temporary.delete()
  }
}

private fun literata(
  weight: Int,
  opticalSize: TextUnit,
) = Font(
  resId = UiR.font.literata,
  weight = FontWeight(weight),
  variationSettings = FontVariation.Settings(
    FontVariation.weight(weight),
    FontVariation.opticalSizing(opticalSize),
  ),
)

private val AtkinsonHyperlegible = FontFamily(
  Font(R.font.atkinson_hyperlegible_regular),
  Font(R.font.atkinson_hyperlegible_bold, FontWeight.Bold),
  Font(R.font.atkinson_hyperlegible_italic, style = FontStyle.Italic),
  Font(R.font.atkinson_hyperlegible_bold_italic, FontWeight.Bold, FontStyle.Italic),
)

// Readium brings it along for its own font choice
private const val OPEN_DYSLEXIC_ASSET = "readium/fonts/OpenDyslexic-Regular.otf"

/** The font of the old engine's pages. [opticalSize] is the size Literata is drawn for, as on the rest of the app. */
@Composable
internal fun ReaderSettings.pageFontFamily(opticalSize: TextUnit): FontFamily {
  val context = LocalContext.current
  val customFile = remember(customFont) { customFont?.let { File(fontsFolder(context), it) }?.takeIf { it.isFile } }
  return if (customFile != null) {
    remember(customFile) { FontFamily(Font(customFile)) }
  } else {
    font.fontFamily(opticalSize)
  }
}

@Composable
internal fun ReaderFont.fontFamily(opticalSize: TextUnit): FontFamily {
  val context = LocalContext.current
  return when (this) {
    ReaderFont.Serif, ReaderFont.Literata -> remember(opticalSize) {
      FontFamily((300..800 step 100).map { literata(it, opticalSize) })
    }
    ReaderFont.Sans -> FontFamily.SansSerif
    ReaderFont.Monospace -> FontFamily.Monospace
    ReaderFont.AtkinsonHyperlegible -> AtkinsonHyperlegible
    ReaderFont.OpenDyslexic -> remember(context) { FontFamily(Font(OPEN_DYSLEXIC_ASSET, context.assets)) }
  }
}

/**
 * Readium serves this host from the book's files, so the fonts are added to them, see [FontsContainer].
 * A font on the book's own host also needs no permission to load.
 */
private const val FONTS_URL = "https://readium_package/pageturner-fonts/"
private const val CUSTOM_FONT = "custom/"
private const val LITERATA_FILE = "literata.ttf"

private val atkinsonFiles = mapOf(
  "atkinson-hyperlegible-regular.ttf" to R.font.atkinson_hyperlegible_regular,
  "atkinson-hyperlegible-bold.ttf" to R.font.atkinson_hyperlegible_bold,
  "atkinson-hyperlegible-italic.ttf" to R.font.atkinson_hyperlegible_italic,
  "atkinson-hyperlegible-bold-italic.ttf" to R.font.atkinson_hyperlegible_bold_italic,
)

private val LiterataFamily = ReadiumFontFamily("Literata")
private val AtkinsonFamily = ReadiumFontFamily("Atkinson Hyperlegible")
private val CustomFamily = ReadiumFontFamily("Pageturner custom font")

/** The book's files, with the reader's fonts added for the new engine. */
internal class FontsContainer(
  private val context: Context,
  private val book: Container<Resource>,
) : Container<Resource> by book {

  override operator fun get(url: Url): Resource? {
    val address = url.toString()
    if (!address.startsWith(FONTS_URL)) return book[url]
    val name = address.removePrefix(FONTS_URL)
    val bytes = try {
      when {
        name == LITERATA_FILE -> readResource(UiR.font.literata)
        name in atkinsonFiles -> readResource(atkinsonFiles.getValue(name))
        name.startsWith(CUSTOM_FONT) -> {
          val fileName = name.removePrefix(CUSTOM_FONT)
          val folder = fontsFolder(context)
          File(folder, fileName).takeIf { '/' !in fileName && it.isFile }?.readBytes()
        }
        else -> null
      }
    } catch (e: IOException) {
      Logger.w(e, "Could not read a font")
      null
    }
    return bytes?.let { InMemoryResource(it) }
  }

  private fun readResource(id: Int): ByteArray = context.resources.openRawResource(id).use { it.readBytes() }
}

private fun fontUrl(name: String): AbsoluteUrl = checkNotNull(AbsoluteUrl(FONTS_URL + name))

/** Tells Readium about the bundled fonts and [customFont], which [FontsContainer] serves. */
internal fun EpubNavigatorFragment.Configuration.addReaderFonts(customFont: String?) {
  addFontFamilyDeclaration(LiterataFamily) {
    addFontFace {
      addSource(fontUrl(LITERATA_FILE))
      setFontWeight(200..900)
    }
  }
  addFontFamilyDeclaration(AtkinsonFamily) {
    atkinsonFiles.keys.forEach { file ->
      addFontFace {
        addSource(fontUrl(file))
        setFontWeight(if ("bold" in file) ReadiumFontWeight.BOLD else ReadiumFontWeight.NORMAL)
        setFontStyle(if ("italic" in file) ReadiumFontStyle.ITALIC else ReadiumFontStyle.NORMAL)
      }
    }
  }
  if (customFont != null) {
    addFontFamilyDeclaration(CustomFamily) {
      addFontFace { addSource(fontUrl(CUSTOM_FONT + customFont)) }
    }
  }
}

/** The font for the new engine, or null to keep the book's own. */
internal fun ReaderSettings.readiumFontFamily(): ReadiumFontFamily? = when {
  publisherStyles -> null
  customFont != null -> CustomFamily
  else -> when (font) {
    ReaderFont.Serif -> ReadiumFontFamily.SERIF
    ReaderFont.Sans -> ReadiumFontFamily.SANS_SERIF
    ReaderFont.Monospace -> ReadiumFontFamily.MONOSPACE
    ReaderFont.Literata -> LiterataFamily
    ReaderFont.AtkinsonHyperlegible -> AtkinsonFamily
    ReaderFont.OpenDyslexic -> ReadiumFontFamily.OPEN_DYSLEXIC
  }
}
