package voice.core.opds.feed

/** Reads an OPDS 2.0 feed when it is JSON, and an OPDS 1.2 feed otherwise. */
public fun parseFeed(
  body: String,
  url: String,
  contentType: String?,
): OpdsFeed {
  val json = contentType?.contains("json") == true || body.trimStart('\uFEFF', ' ', '\n', '\r', '\t').startsWith("{")
  return if (json) parseOpds2(body, url) else parseOpds1(body, url)
}
