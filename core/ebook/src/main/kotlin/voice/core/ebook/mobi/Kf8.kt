package voice.core.ebook.mobi

private val posFidLink = Regex("""kindle:pos:fid:([0-9A-V]{4}):off:([0-9A-V]{10})""")
private val embedLink = Regex("""kindle:embed:([0-9A-V]{4})(?:\?mime=[^'"()\s>]*)?""")
private val flowTag = Regex("""<[^<>]*kindle:flow:([0-9A-V]{4})[^<>]*>""")
private val flowLink = Regex("""kindle:flow:([0-9A-V]{4})(?:\?mime=[^'"()\s>]*)?""")
private val aidAttribute = Regex("""\s(?:aid|AID)\s*=\s*['"]([^'"]+)['"]""")
private val idInTag = Regex("""^<[^>]*\sid\s*=\s*['"]([^'"]*)['"]""", RegexOption.IGNORE_CASE)
private val nameInTag = Regex("""^<[^>]*\sname\s*=\s*['"]([^'"]*)['"]""", RegexOption.IGNORE_CASE)
private val aidInTag = Regex("""^<[^>]+\s(?:aid|AID)\s*=\s*['"]([^'"]+)['"]""")

internal class ConvertedText(
  val documents: List<EpubFile>,
  val resources: List<EpubFile>,
  val toc: List<TocEntry>,
)

private class Fragment(
  val insertPosition: Int,
  val fileNumber: Int,
  val length: Int,
)

private class Skeleton(
  val fragmentCount: Int,
  val start: Int,
  val length: Int,
)

private class Flow(
  val content: String,
  /** The file the flow is written to, or null when it goes into the page in place of the tag that links it. */
  val file: EpubFile?,
)

/**
 * A KF8 book, like an AZW3: the text is split into skeletons with fragments inserted into them, which together make the
 * XHTML files. Style sheets and SVG images are further flows of text, and links point at fragments, flows and resources.
 */
internal fun kf8Content(
  db: PalmDb,
  header: MobiHeader,
  resources: Resources,
): ConvertedText {
  val raw = readText(db, header)
  val flows = flows(db, header, raw)
  // byte positions stay character positions in ISO-8859-1, and the UTF-8 bytes come back unchanged when writing
  val text = flows.first()

  val skeletons = readIndex(db, header.skeletonIndex).entries.map { entry ->
    Skeleton(fragmentCount = entry.tag(1) ?: 0, start = entry.tag(6, 0) ?: 0, length = entry.tag(6, 1) ?: 0)
  }
  val fragments = readIndex(db, header.fragmentIndex).entries.map { entry ->
    Fragment(
      insertPosition = String(entry.name, Charsets.ISO_8859_1).toIntOrNull() ?: 0,
      fileNumber = entry.tag(3) ?: 0,
      length = entry.tag(6, 1) ?: 0,
    )
  }

  val parts = mutableListOf<String>()
  val partRanges = mutableListOf<IntRange>()
  if (skeletons.isEmpty()) {
    parts += text
    partRanges += text.indices
  }
  var fragmentIndex = 0
  skeletons.forEach { skeleton ->
    var end = (skeleton.start + skeleton.length).coerceAtMost(text.length)
    var part = text.substring(skeleton.start.coerceAtMost(end), end)
    repeat(skeleton.fragmentCount) {
      val fragment = fragments.getOrNull(fragmentIndex++) ?: return@repeat
      val slice = text.substring(end, (end + fragment.length).coerceAtMost(text.length))
      val at = (fragment.insertPosition - skeleton.start).coerceIn(0, part.length)
      part = part.substring(0, at) + slice + part.substring(at)
      end += fragment.length
    }
    parts += part
    partRanges += skeleton.start until end
  }

  fun partAt(position: Int): Int = partRanges.indexOfFirst { position in it }

  val linkedAids = mutableSetOf<String>()

  fun idAt(
    part: Int,
    offset: Int,
  ): String {
    val html = parts[part]
    var end = offset.coerceIn(0, html.length)
    val close = html.indexOf('>', end)
    val open = html.indexOf('<', end)
    if (close != -1 && (open == end || open == -1 || close < open)) end = close + 1
    // the closest tag before the place that has an id, a name or Amazon's aid
    var tagEnd = end
    while (tagEnd > 0) {
      val tagStart = html.lastIndexOf('<', tagEnd - 1)
      if (tagStart == -1) break
      val tag = html.substring(tagStart, html.indexOf('>', tagStart).let { if (it == -1 || it >= tagEnd) tagEnd else it + 1 })
      tagEnd = tagStart
      if (tag.startsWith("<body ", ignoreCase = true) || tag.startsWith("<body>", ignoreCase = true)) return ""
      if (tag.startsWith("<meta ", ignoreCase = true)) continue
      (idInTag.find(tag) ?: nameInTag.find(tag))?.let { return it.groupValues[1] }
      aidInTag.find(tag)?.let {
        linkedAids += it.groupValues[1]
        return "aid-" + it.groupValues[1]
      }
    }
    return ""
  }

  fun target(
    fragmentNumber: Int,
    offset: Int,
  ): String? {
    val fragment = fragments.getOrNull(fragmentNumber) ?: return null
    var position = fragment.insertPosition + offset
    var part = partAt(position)
    if (part == -1) {
      // a link to a place that doesn't exist goes to the top of its file
      position = skeletons.getOrNull(fragment.fileNumber)?.start ?: return null
      part = partAt(position)
      if (part == -1) return null
    }
    val id = idAt(part, position - partRanges[part].first)
    val file = chapterPath(part).substringAfterLast('/')
    return if (id.isEmpty()) file else "$file#$id"
  }

  fun positionTarget(position: Int): String? {
    val part = partAt(position).takeIf { it >= 0 } ?: return null
    val id = idAt(part, position - partRanges[part].first)
    val file = chapterPath(part).substringAfterLast('/')
    return if (id.isEmpty()) file else "$file#$id"
  }

  val flowFiles = flows.drop(1).mapIndexed { index, flow -> flowFile(index + 1, flow) }
  fun flow(number: Int): Flow? = flowFiles.getOrNull(number - 1)

  fun embedded(html: String): String = html.replace(embedLink) { match ->
    resources.byNumber(fromBase32(match.groupValues[1]))?.let { "../" + it.path } ?: match.value
  }

  val linked = parts.map { part ->
    var html = part.replace(posFidLink) { match ->
      target(fromBase32(match.groupValues[1]), fromBase32(match.groupValues[2])) ?: "#"
    }
    html = html.replace(flowTag) { tag ->
      val flow = flow(fromBase32(tag.groupValues[1])) ?: return@replace tag.value
      if (flow.file == null) {
        flow.content
      } else {
        tag.value.replace(flowLink, "../" + flow.file.path)
      }
    }
    embedded(html)
  }

  val toc = readIndex(db, header.ncxIndex).let { ncx ->
    ncx.entries.mapNotNull { entry ->
      val label = entry.tag(3)?.let { ncx.strings[it] }?.let { String(it, header.charset).trim() }
        ?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
      val fid = entry.tag(6, 0)
      val href = if (fid != null) {
        target(fid, entry.tag(6, 1) ?: 0)
      } else {
        entry.tag(1)?.let(::positionTarget)
      } ?: return@mapNotNull null
      TocEntry(label, "text/$href", entry.tag(4) ?: 0)
    }
  }

  val documents = linked.mapIndexed { index, html ->
    val withIds = html.replace(aidAttribute) { match ->
      if (match.groupValues[1] in linkedAids) """ id="aid-${match.groupValues[1]}"""" else ""
    }
    EpubFile(chapterPath(index), XHTML, withIds.toByteArray(Charsets.ISO_8859_1))
  }
  val flowResources = flowFiles.mapNotNull { flow ->
    flow.file?.let { file ->
      EpubFile(file.path, file.mediaType, embedded(String(file.data, Charsets.ISO_8859_1)).toByteArray(Charsets.ISO_8859_1))
    }
  }
  return ConvertedText(documents, flowResources, toc)
}

/** The flows of the text: the first one holds the pages, the others style sheets and SVG images. */
private fun flows(
  db: PalmDb,
  header: MobiHeader,
  raw: ByteArray,
): List<String> {
  val text = String(raw, Charsets.ISO_8859_1)
  val fdst = db.record(header.fdstRecord)
  if (header.fdstRecord == NO_INDEX || fdst.ascii(0, 4) != "FDST") return listOf(text)
  val count = fdst.u32(8).toInt()
  return List(count) { index ->
    val start = fdst.u32(12 + index * 8).toInt().coerceIn(0, text.length)
    val end = fdst.u32(16 + index * 8).toInt().coerceIn(start, text.length)
    text.substring(start, end)
  }.ifEmpty { listOf(text) }
}

private fun flowFile(
  number: Int,
  content: String,
): Flow {
  val name = "flow" + number.toString().padStart(4, '0')
  val svgStart = Regex("""<svg\s""", RegexOption.IGNORE_CASE).find(content)?.range?.first
  return when {
    // an SVG around an image is a page of its own, like the cover, and is put where it's linked
    svgStart != null && Regex("""<image[^>]*>""", RegexOption.IGNORE_CASE).containsMatchIn(content) ->
      Flow(content.substring(svgStart), file = null)
    svgStart != null -> Flow(content, EpubFile("images/$name.svg", "image/svg+xml", content.toByteArray(Charsets.ISO_8859_1)))
    "[CDATA[" in content -> Flow("""<style type="text/css">$content</style>""", file = null)
    else -> Flow(content, EpubFile("styles/$name.css", "text/css", content.toByteArray(Charsets.ISO_8859_1)))
  }
}

internal fun fromBase32(text: String): Int = text.fold(0) { value, char ->
  value * 32 + if (char in '0'..'9') char - '0' else char - 'A' + 10
}
