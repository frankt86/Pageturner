package voice.core.opds.feed

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.URLEncoder

internal fun resolveUrl(
  base: String,
  href: String?,
): String? {
  val trimmed = href?.trim()?.takeIf { it.isNotEmpty() } ?: return null
  return base.toHttpUrlOrNull()?.resolve(trimmed)?.toString()
}

private val templateVariable = Regex("\\{[^}]*\\}")

/**
 * Resolves a search template against the feed it came from. The placeholders are swapped out first, as resolving
 * would percent-encode their braces.
 */
internal fun resolveTemplate(
  base: String,
  template: String?,
): String? {
  val trimmed = template?.trim()?.takeIf { it.isNotEmpty() } ?: return null
  val variables = templateVariable.findAll(trimmed).map { it.value }.toList()
  var masked = trimmed
  variables.forEachIndexed { index, variable -> masked = masked.replaceFirst(variable, "opdsvariable$index") }
  var resolved = resolveUrl(base, masked) ?: return null
  variables.forEachIndexed { index, variable -> resolved = resolved.replaceFirst("opdsvariable$index", variable) }
  return resolved
}

/**
 * Fills a search template: the OpenSearch kind of OPDS 1.2, like `/search?q={searchTerms}&page={startPage?}`, and the
 * URI templates of OPDS 2.0, like `/search{?query,author}`. Placeholders other than the search terms stay empty.
 */
public fun expandSearchTemplate(
  template: String,
  query: String,
): String {
  val encoded = URLEncoder.encode(query.trim(), "UTF-8").replace("+", "%20")
  return templateVariable.replace(template) { match ->
    val expression = match.value.removePrefix("{").removeSuffix("}")
    val operator = expression.firstOrNull()?.takeIf { it == '?' || it == '&' }
    if (operator != null) {
      val names = expression.drop(1).split(',').map { it.trim() }
      val name = names.firstOrNull { it.isSearchTerms() } ?: return@replace ""
      "$operator$name=$encoded"
    } else if (expression.isSearchTerms()) {
      encoded
    } else {
      ""
    }
  }
}

private fun String.isSearchTerms(): Boolean {
  val name = removeSuffix("?").substringAfterLast(':')
  return name == "searchTerms" || name == "query" || name == "q"
}

private val entity = Regex("&(#[xX][0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);")
private val namedEntities = mapOf(
  "amp" to "&",
  "lt" to "<",
  "gt" to ">",
  "quot" to "\"",
  "apos" to "'",
  "nbsp" to " ",
  "mdash" to "—",
  "ndash" to "–",
  "hellip" to "…",
  "rsquo" to "’",
  "lsquo" to "‘",
  "rdquo" to "”",
  "ldquo" to "“",
)

/** The plain text of a summary that comes as HTML, with its paragraphs kept apart. */
internal fun htmlToText(html: String): String {
  return html
    .replace(Regex("<(br|/p|/div|/li|/h[1-6])\\b[^>]*>", RegexOption.IGNORE_CASE), "\n")
    .replace(Regex("<[^>]*>"), "")
    .let { text ->
      entity.replace(text) { match ->
        val name = match.groupValues[1]
        when {
          name.startsWith("#x") || name.startsWith("#X") -> name.drop(2).toIntOrNull(16)?.let(::codePointToString)
          name.startsWith("#") -> name.drop(1).toIntOrNull()?.let(::codePointToString)
          else -> namedEntities[name]
        } ?: match.value
      }
    }
    .replace(Regex("[ \\t\\x0B\\f\\r]+"), " ")
    .replace(Regex(" *\n *"), "\n")
    .replace(Regex("\n{3,}"), "\n\n")
    .trim()
}

private fun codePointToString(codePoint: Int): String? {
  if (!Character.isValidCodePoint(codePoint)) return null
  return String(Character.toChars(codePoint))
}
