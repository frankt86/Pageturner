package voice.core.opds.feed

private const val ENGLISH = "en"

// "und" is the code for a language nobody determined, which says as little as no code
private val unknown = setOf("", "und", "zxx")

/** The language as a code like `en`. Feeds write English as `en`, `eng`, `en-US` or `English`. */
internal fun languageCode(language: String): String? {
  val code = language.trim().lowercase().substringBefore('-').substringBefore('_')
  return when (code) {
    "eng",
    "english" -> ENGLISH
    in unknown -> null
    else -> code
  }
}

internal fun languageCodes(languages: List<String>): List<String> =
  languages.mapNotNull(::languageCode).distinct()

/**
 * Only the books in English and the ones without a language, as many feeds don't tell it. Shelves
 * and other links stay.
 */
internal fun OpdsFeed.inEnglish(): OpdsFeed =
  copy(
    publications = publications.filter { it.languages.isEmpty() || ENGLISH in it.languages },
  )
