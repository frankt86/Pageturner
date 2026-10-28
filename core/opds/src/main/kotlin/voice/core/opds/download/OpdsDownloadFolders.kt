package voice.core.opds.download

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import voice.core.data.folders.AudiobookFolders
import voice.core.data.folders.FolderType
import voice.core.datastore.VoiceDataStoreFactory
import voice.core.documentfile.nameFromPath
import kotlin.time.Duration.Companion.seconds

/** A folder of the library that holds several books. [uri] is the folder as the library keeps it. */
public data class DownloadFolder(
  val uri: Uri,
  val name: String,
  val type: FolderType,
)

@SingleIn(AppScope::class)
internal class DownloadFolderStore(store: DataStore<String?>) : DataStore<String?> by store {

  @Inject
  constructor(factory: VoiceDataStoreFactory) : this(
    factory.create(
      serializer = String.serializer().nullable,
      defaultValue = null,
      fileName = "opdsDownloadFolder",
    ),
  )
}

/**
 * The folder of the library that books from the catalogs download into, so the scan adds them like every other book
 * there.
 */
@SingleIn(AppScope::class)
@Inject
public class OpdsDownloadFolders internal constructor(
  private val store: DownloadFolderStore,
  private val audiobookFolders: AudiobookFolders,
  private val context: Context,
) {

  /** The folders downloads can go to: those of several books that Pageturner may write to. */
  public val choices: Flow<List<DownloadFolder>> = audiobookFolders.all()
    .map { folders ->
      val writable = context.contentResolver.persistedUriPermissions
        .filter { it.isWritePermission }
        .mapTo(HashSet()) { it.uri }
      val byUri = folders.values.flatten().associateBy { it.uri }
      downloadFolderCandidates(folders.mapValues { (_, files) -> files.map { it.uri } }, writable)
        .map { (uri, type) ->
          DownloadFolder(
            uri = uri,
            name = byUri[uri]?.documentFile?.name ?: uri.nameFromPath(),
            type = type,
          )
        }
    }
    // the names come from the system
    .flowOn(Dispatchers.IO)
    .distinctUntilChanged()

  /** The chosen folder, or null while none is chosen or it left the library or Pageturner lost access to it. */
  public val current: Flow<DownloadFolder?> = combine(store.data, choices) { chosen, folders ->
    folders.firstOrNull { it.uri.toString() == chosen }
  }.distinctUntilChanged()

  public suspend fun choose(uri: Uri) {
    store.updateData { uri.toString() }
  }

  /**
   * Adds a folder picked with the system's picker to the library as a folder of books and chooses it.
   * @return whether downloads can go there, which needs the permission to write
   */
  public suspend fun addAndChoose(uri: Uri): Boolean {
    // a folder already in the library keeps how its books are found, and a folder of one book can't take more
    val type = typeInLibrary(uri) ?: FolderType.Root
    if (type != FolderType.Root && type != FolderType.Author) return false
    audiobookFolders.add(uri, type)
    choose(uri)
    // the library stores the folder in the background
    return withTimeoutOrNull(5.seconds) { current.first { it?.uri == uri } } != null
  }

  internal suspend fun current(): DownloadFolder? = current.first()

  private suspend fun typeInLibrary(uri: Uri): FolderType? {
    val withAccess = audiobookFolders.all().first()
    val withoutAccess = audiobookFolders.withoutAccess().first()
    return FolderType.entries.firstOrNull { type ->
      withAccess[type].orEmpty().any { it.uri == uri } || uri in withoutAccess[type].orEmpty()
    }
  }
}
