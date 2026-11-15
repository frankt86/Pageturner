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
 * Only the books in English. A book without a language only stays when [keepUntagged] says that the
 * catalog sent books in English only. Shelves and other links stay.
 */
internal fun OpdsFeed.inEnglish(keepUntagged: Boolean): OpdsFeed =
  copy(
    publications =
      publications.filter {
        if (it.languages.isEmpty()) keepUntagged else ENGLISH in it.languages
      },
  )
