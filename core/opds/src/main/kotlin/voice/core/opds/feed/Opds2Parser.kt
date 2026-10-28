package voice.core.opds.feed

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

private const val ACQUISITION = "http://opds-spec.org/acquisition"
private val acquisitionRels = setOf(ACQUISITION, "$ACQUISITION/open-access")

/** Reads an OPDS 2.0 feed. Links in it are resolved against [url], where it came from. */
internal fun parseOpds2(
  json: String,
  url: String,
): OpdsFeed {
  val element = try {
    Json.parseToJsonElement(json)
  } catch (e: SerializationException) {
    throw OpdsParseException("Not JSON", e)
  }
  val root = element as? JsonObject ?: throw OpdsParseException("Not a JSON object")
  if (root["metadata"] == null && root["publications"] == null && root["navigation"] == null) {
    throw OpdsParseException("Not an OPDS 2 feed")
  }
  val links = root.links("links", url)
  val navigation = root.links("navigation", url).mapNotNull { it.toNavigation() }.toMutableList()
  val publications = root.objects("publications").mapNotNull { it.toPublication(url) }.toMutableList()
  root.objects("groups").forEach { group ->
    val title = group.obj("metadata")?.string("title")
    val self = group.links("links", url).firstOrNull { "self" in it.rels }?.href
    if (title != null && self != null) {
      // a group that has its own feed, like a shelf on a start page, opens it
      navigation += OpdsNavigation(title = title, url = self, summary = null)
    } else {
      navigation += group.links("navigation", url).mapNotNull { it.toNavigation() }
      publications += group.objects("publications").mapNotNull { it.toPublication(url) }
    }
  }
  return OpdsFeed(
    title = root.obj("metadata")?.string("title"),
    navigation = navigation,
    publications = publications,
    nextUrl = links.firstOrNull { "next" in it.rels }?.href,
    search = links.firstOrNull { "search" in it.rels }?.let { link ->
      when {
        link.templated || '{' in link.rawHref -> resolveTemplate(url, link.rawHref)?.let(OpdsSearch::Template)
        link.type == "application/opensearchdescription+xml" -> link.href?.let(OpdsSearch::Description)
        else -> null
      }
    },
  )
}

private class JsonLink(
  val href: String?,
  val rawHref: String,
  val type: String?,
  val rels: List<String>,
  val title: String?,
  val templated: Boolean,
  val width: Int?,
  val length: Long?,
)

private fun JsonLink.toNavigation(): OpdsNavigation? {
  return OpdsNavigation(
    title = title?.takeIf { it.isNotBlank() } ?: return null,
    url = href ?: return null,
    summary = null,
  )
}

private fun JsonObject.toPublication(base: String): OpdsPublication? {
  val metadata = obj("metadata") ?: return null
  val links = links("links", base)
  val acquisitions = links.filter { link -> link.href != null && link.rels.any { it in acquisitionRels } }
  if (acquisitions.isEmpty()) return null
  val images = links("images", base).filter { it.href != null }
  return OpdsPublication(
    id = metadata.string("identifier") ?: acquisitions.first().href!!,
    title = metadata.string("title").orEmpty(),
    authors = metadata["author"].contributorNames(),
    summary = metadata.string("description")?.let(::htmlToText)?.takeIf { it.isNotEmpty() },
    coverUrl = images.maxByOrNull { it.width ?: 0 }?.href,
    thumbnailUrl = images.filter { it.width != null }.minByOrNull { it.width!! }?.href,
    acquisitions = acquisitions.map { it.toAcquisition() },
    isAudiobook = metadata.isAudiobook(),
  )
}

private fun JsonLink.toAcquisition(): OpdsAcquisition = OpdsAcquisition(url = href!!, type = type, title = title, length = length)

private fun JsonObject.isAudiobook(): Boolean {
  val types = listOf("@type", "conformsTo").flatMap { key ->
    when (val value = this[key]) {
      is JsonPrimitive -> listOfNotNull(value.contentOrNull)
      is JsonArray -> value.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
      else -> emptyList()
    }
  }
  return types.any { "audiobook" in it.lowercase() }
}

/**
 * The audio files of a Readium audiobook manifest, in the order they play. Links in it are resolved against [url],
 * where it came from.
 */
internal fun parseAudiobookManifest(
  json: String,
  url: String,
): List<OpdsAcquisition> {
  val root = try {
    Json.parseToJsonElement(json) as? JsonObject
  } catch (e: SerializationException) {
    throw OpdsParseException("Not JSON", e)
  } ?: throw OpdsParseException("Not a JSON object")
  return root.links("readingOrder", url)
    .filter { it.href != null }
    .map { it.toAcquisition() }
}

private fun JsonElement?.contributorNames(): List<String> {
  return when (this) {
    is JsonPrimitive -> listOfNotNull(contentOrNull?.trim()?.takeIf { it.isNotEmpty() })
    is JsonObject -> listOfNotNull(string("name"))
    is JsonArray -> flatMap { it.contributorNames() }
    else -> emptyList()
  }
}

private fun JsonObject.links(
  key: String,
  base: String,
): List<JsonLink> {
  return objects(key).mapNotNull { link ->
    val rawHref = link.string("href") ?: return@mapNotNull null
    val templated = (link["templated"] as? JsonPrimitive)?.booleanOrNull == true
    JsonLink(
      href = if (templated) null else resolveUrl(base, rawHref),
      rawHref = rawHref,
      type = link.string("type")?.mediaType(),
      rels = when (val rel = link["rel"]) {
        is JsonPrimitive -> listOfNotNull(rel.contentOrNull)
        is JsonArray -> rel.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        else -> emptyList()
      },
      title = link.string("title"),
      templated = templated,
      width = (link["width"] as? JsonPrimitive)?.intOrNull,
      length = ((link["length"] ?: link["size"]) as? JsonPrimitive)?.longOrNull?.takeIf { it > 0 },
    )
  }
}

private fun JsonObject.objects(key: String): List<JsonObject> = (this[key] as? JsonArray)?.filterIsInstance<JsonObject>().orEmpty()

private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

/** A plain string, or the first translation of one that comes in several languages. */
private fun JsonObject.string(key: String): String? {
  val value = when (val element = this[key]) {
    is JsonPrimitive -> element.contentOrNull
    is JsonObject -> element.values.firstNotNullOfOrNull { (it as? JsonPrimitive)?.contentOrNull }
    else -> null
  }
  return value?.trim()?.takeIf { it.isNotEmpty() }
}
