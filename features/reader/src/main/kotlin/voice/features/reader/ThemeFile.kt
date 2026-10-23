package voice.features.reader

import android.content.Context
import android.net.Uri
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import voice.core.data.ReaderColors
import voice.core.logging.api.Logger

private val themesJson = Json {
  ignoreUnknownKeys = true
  prettyPrint = true
}

internal fun encodeThemes(themes: List<ReaderColors>): String {
  return themesJson.encodeToString(ListSerializer(ReaderColors.serializer()), themes)
}

/** The themes in [text] with colors the reader can show, empty if there are none. */
internal fun decodeThemes(text: String): List<ReaderColors> {
  val themes = try {
    themesJson.decodeFromString(ListSerializer(ReaderColors.serializer()), text)
  } catch (e: IllegalArgumentException) {
    Logger.w(e, "Could not read the themes")
    return emptyList()
  }
  return themes.filter { theme ->
    listOfNotNull(theme.background, theme.text, theme.link, theme.selection).all { parseHexColor(it) != null }
  }
}

internal fun exportThemes(
  context: Context,
  uri: Uri,
  themes: List<ReaderColors>,
): Boolean = try {
  val output = context.contentResolver.openOutputStream(uri, "wt") ?: error("No output")
  output.bufferedWriter().use { it.write(encodeThemes(themes)) }
  true
} catch (e: Exception) {
  Logger.w(e, "Could not export the themes")
  false
}

internal fun importThemes(
  context: Context,
  uri: Uri,
): List<ReaderColors> = try {
  val input = context.contentResolver.openInputStream(uri) ?: error("No input")
  decodeThemes(input.bufferedReader().use { it.readText() })
} catch (e: Exception) {
  Logger.w(e, "Could not import the themes")
  emptyList()
}
