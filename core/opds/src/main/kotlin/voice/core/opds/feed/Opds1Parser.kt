package voice.core.opds.feed

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException

public class OpdsParseException(
  message: String,
  cause: Throwable? = null,
) : Exception(message, cause)

private const val ACQUISITION = "http://opds-spec.org/acquisition"
private val acquisitionRels = setOf(ACQUISITION, "$ACQUISITION/open-access")
private val coverRels = setOf("http://opds-spec.org/image", "http://opds-spec.org/cover", "x-stanza-cover-image")
private val thumbnailRels = setOf(
  "http://opds-spec.org/image/thumbnail",
  "http://opds-spec.org/thumbnail",
  "x-stanza-cover-image-thumbnail",
)

/** Reads an OPDS 1.2 feed, which is an Atom feed. Links in it are resolved against [url], where it came from. */
internal fun parseOpds1(
  xml: String,
  url: String,
): OpdsFeed {
  val feed = parseXml(xml).documentElement
  if (feed?.localName != "feed") throw OpdsParseException("Not an Atom feed")
  val links = feed.children("link").map { it.toLink(url) }
  val navigation = mutableListOf<OpdsNavigation>()
  val publications = mutableListOf<OpdsPublication>()
  feed.children("entry").forEach { entry ->
    val entryLinks = entry.children("link").map { it.toLink(url) }
    val acquisitions = entryLinks.filter { it.rel in acquisitionRels && it.href != null }
    val title = entry.child("title")?.text().orEmpty()
    if (acquisitions.isNotEmpty()) {
      publications += OpdsPublication(
        id = entry.child("id")?.text()?.takeIf { it.isNotEmpty() } ?: acquisitions.first().href!!,
        title = title,
        authors = entry.children("author").mapNotNull { it.child("name")?.text()?.takeIf(String::isNotEmpty) },
        summary = entry.summary(),
        coverUrl = entryLinks.firstOrNull { it.rel in coverRels }?.href,
        thumbnailUrl = entryLinks.firstOrNull { it.rel in thumbnailRels }?.href,
        acquisitions = acquisitions.map {
          OpdsAcquisition(url = it.href!!, type = it.type, title = it.title, length = it.length)
        },
        isAudiobook = entry.isAudiobook(),
      )
    } else {
      val target = entryLinks.firstOrNull { it.isFeed && it.rel != "alternate" } ?: entryLinks.firstOrNull { it.isFeed }
      if (target?.href != null && title.isNotEmpty()) {
        navigation += OpdsNavigation(title = title, url = target.href, summary = entry.summary())
      }
    }
  }
  return OpdsFeed(
    title = feed.child("title")?.text()?.takeIf { it.isNotEmpty() },
    navigation = navigation,
    publications = publications,
    nextUrl = links.firstOrNull { it.rel == "next" }?.href,
    search = links.filter { it.rel == "search" }.firstNotNullOfOrNull { it.toSearch(url) },
  )
}

/** The search template from an OpenSearch description, preferring the one that answers with a catalog. */
internal fun parseOpenSearchTemplate(
  xml: String,
  url: String,
): String? {
  val root = parseXml(xml).documentElement ?: return null
  val urls = root.children("Url").filter { it.getAttribute("template").isNotEmpty() }
  val best = urls.firstOrNull { "opds" in it.getAttribute("type") }
    ?: urls.firstOrNull { "atom" in it.getAttribute("type") }
    ?: urls.firstOrNull()
  return best?.let { resolveTemplate(url, it.getAttribute("template")) }
}

private class AtomLink(
  val rel: String?,
  val href: String?,
  val rawHref: String?,
  val type: String?,
  private val rawType: String,
  val title: String?,
  val length: Long?,
) {
  // a link to a single entry, like the details of a book, isn't a feed
  val isFeed: Boolean get() = ("atom+xml" in rawType || "opds" in rawType) && "type=entry" !in rawType
}

private fun Element.toLink(base: String): AtomLink {
  val rawHref = getAttribute("href").takeIf { it.isNotEmpty() }
  return AtomLink(
    rel = getAttribute("rel").trim().takeIf { it.isNotEmpty() },
    href = resolveUrl(base, rawHref),
    rawHref = rawHref,
    type = getAttribute("type").takeIf { it.isNotEmpty() }?.mediaType(),
    rawType = getAttribute("type").lowercase(),
    title = getAttribute("title").trim().takeIf { it.isNotEmpty() },
    length = getAttribute("length").trim().toLongOrNull()?.takeIf { it > 0 },
  )
}

// dc:type, or a category like schema.org's additionalType
private fun Element.isAudiobook(): Boolean {
  val types = children("type").map { it.text() } +
    children("category").flatMap { listOf(it.getAttribute("term"), it.getAttribute("label")) }
  return types.any { type ->
    val name = type.trim().substringAfterLast('/').lowercase()
    name == "audiobook" || name == "sound"
  }
}

private fun AtomLink.toSearch(base: String): OpdsSearch? {
  val raw = rawHref ?: return null
  return when {
    '{' in raw -> resolveTemplate(base, raw)?.let(OpdsSearch::Template)
    type == "application/opensearchdescription+xml" -> href?.let(OpdsSearch::Description)
    else -> null
  }
}

private fun Element.summary(): String? {
  val summary = child("summary") ?: child("content") ?: return null
  val text = when (summary.getAttribute("type")) {
    "html", "text/html" -> htmlToText(summary.textContent)
    "xhtml" -> summary.textContent.trim()
    else -> if ('<' in summary.textContent) htmlToText(summary.textContent) else summary.textContent.trim()
  }
  return text.takeIf { it.isNotEmpty() }
}

private fun Element.children(localName: String): List<Element> {
  val nodes = childNodes
  return (0 until nodes.length)
    .mapNotNull { nodes.item(it) as? Element }
    .filter { (it.localName ?: it.tagName) == localName }
}

private fun Element.child(localName: String): Element? = children(localName).firstOrNull()

private fun Element.text(): String = textContent.trim().replace(Regex("\\s+"), " ")

private fun parseXml(xml: String): Document {
  val factory = DocumentBuilderFactory.newInstance()
  factory.isNamespaceAware = true
  factory.isExpandEntityReferences = false
  // a feed has no business loading other files
  listOf(
    "http://xml.org/sax/features/external-general-entities",
    "http://xml.org/sax/features/external-parameter-entities",
    "http://apache.org/xml/features/nonvalidating/load-external-dtd",
  ).forEach { feature ->
    try {
      factory.setFeature(feature, false)
    } catch (_: ParserConfigurationException) {
      // Android's parser doesn't know these, and never loads external entities anyway
    }
  }
  return try {
    factory.newDocumentBuilder().parse(InputSource(StringReader(xml.trimStart('\uFEFF', ' ', '\n', '\r', '\t'))))
  } catch (e: Exception) {
    throw OpdsParseException("Not XML", e)
  }
}
