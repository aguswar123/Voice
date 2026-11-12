package voice.features.playbackScreen.newui

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class FolderFile(
  val name: String,
  val uri: Uri,
)

/** Lists and reads the files that lie next to the audio files of a book. */
internal object BookFolder {

  suspend fun list(
    context: Context,
    audioFiles: List<Uri>,
  ): List<FolderFile> = withContext(Dispatchers.IO) {
    audioFiles
      .distinctBy { parentKey(it) }
      .flatMap { listParent(context, it) }
      .distinctBy { it.uri }
      .sortedWith(compareBy(naturalNameOrder) { it.name })
  }

  suspend fun readText(
    context: Context,
    uri: Uri,
  ): String? = withContext(Dispatchers.IO) {
    try {
      context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
    } catch (e: Exception) {
      null
    }
  }

  suspend fun copyToCache(
    context: Context,
    uri: Uri,
    name: String,
  ): File? = withContext(Dispatchers.IO) {
    try {
      val target = File(context.cacheDir, "newui_${uri.hashCode()}_${name.hashCode()}.epub")
      if (!target.exists()) {
        val input = context.contentResolver.openInputStream(uri) ?: return@withContext null
        input.use { source ->
          target.outputStream().use { sink ->
            check(source.copyTo(sink) >= 0L)
          }
        }
      }
      target
    } catch (e: Exception) {
      null
    }
  }

  private fun parentKey(uri: Uri): String {
    return if (uri.scheme == "file") {
      uri.path.orEmpty().substringBeforeLast('/')
    } else {
      try {
        DocumentsContract.getDocumentId(uri).substringBeforeLast('/')
      } catch (e: IllegalArgumentException) {
        uri.toString()
      }
    }
  }

  private fun listParent(
    context: Context,
    audioFile: Uri,
  ): List<FolderFile> {
    return try {
      if (audioFile.scheme == "file") {
        val parent = File(audioFile.path.orEmpty()).parentFile
        parent?.listFiles().orEmpty()
          .filter { it.isFile }
          .map { FolderFile(it.name, Uri.fromFile(it)) }
      } else {
        listDocuments(context, audioFile)
      }
    } catch (e: Exception) {
      emptyList()
    }
  }

  private fun listDocuments(
    context: Context,
    audioFile: Uri,
  ): List<FolderFile> {
    val parentId = DocumentsContract.getDocumentId(audioFile).substringBeforeLast('/', "")
    if (parentId.isEmpty()) return emptyList()
    val children = DocumentsContract.buildChildDocumentsUriUsingTree(audioFile, parentId)
    val result = mutableListOf<FolderFile>()
    val projection =
      arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
      )
    context.contentResolver.query(children, projection, null, null, null)?.use { cursor ->
      while (cursor.moveToNext()) {
        val id = cursor.getString(0)
        val name = cursor.getString(1)
        val mime = cursor.getString(2)
        if (id != null && name != null && mime != DocumentsContract.Document.MIME_TYPE_DIR) {
          result += FolderFile(name, DocumentsContract.buildDocumentUriUsingTree(audioFile, id))
        }
      }
    }
    return result
  }
}
