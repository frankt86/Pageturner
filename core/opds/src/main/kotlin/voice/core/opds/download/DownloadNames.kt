package voice.core.opds.download

import voice.core.data.folders.FolderType

internal fun safeFileName(
  name: String,
  fallback: String = "Audiobook",
): String {
  return name.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
    .trim()
    .trimStart('.')
    .take(80)
    .trim()
    .ifEmpty { fallback }
}

/**
 * [name] with [extension], numbered like `Persuasion (2).epub` when a file in the folder has that name. Many file
 * systems on Android don't tell upper and lower case apart, so neither does this.
 */
internal fun uniqueName(
  name: String,
  extension: String?,
  taken: Collection<String>,
): String {
  val lowercase = taken.mapTo(HashSet()) { it.lowercase() }
  val suffix = extension?.let { ".$it" }.orEmpty()
  var candidate = name + suffix
  var number = 2
  while (candidate.lowercase() in lowercase) {
    candidate = "$name ($number)$suffix"
    number++
  }
  return candidate
}

/** The folders of the library that hold several books and that Pageturner may write to, in the order of [folders]. */
internal fun <T> downloadFolderCandidates(
  folders: Map<FolderType, List<T>>,
  writable: Set<T>,
): List<Pair<T, FolderType>> {
  return listOf(FolderType.Root, FolderType.Author).flatMap { type ->
    folders[type].orEmpty().filter { it in writable }.map { it to type }
  }
}

/** Where in a folder of the library the book goes: in a folder of its author when the folders there are authors. */
internal fun bookFolderPath(
  type: FolderType,
  author: String?,
): List<String> {
  return when (type) {
    FolderType.Author -> listOf(safeFileName(author.orEmpty(), fallback = "Unknown author"))
    else -> emptyList()
  }
}
