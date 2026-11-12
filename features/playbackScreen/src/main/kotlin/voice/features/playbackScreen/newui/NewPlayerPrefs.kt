package voice.features.playbackScreen.newui

import android.content.SharedPreferences
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** The settings of the new player view. */
@Immutable
internal data class PlayerSettings(
  // text and fonts
  val fontFamily: String = "sans",
  val size: Int = 18,
  val lineHeight: Float = 1.3F,
  val weight: Int = 400,
  val align: String = "justify",
  val anchor: Int = 20,
  val sameSize: Boolean = true,
  // focus and blur
  val blur: Boolean = true,
  val blurAmount: Float = 2.5F,
  val linesBefore: Int = 2,
  val linesAfter: Int = 8,
  // EPUB text
  val epHighlight: String = "colorbold",
  val epDim: Boolean = true,
  val epOwnStyles: Boolean = true,
  val epSize: Int = 18,
  val epImageWidth: Int = 25,
  val epSplit: Int = 0,
  // captions
  val useFileNames: Boolean = true,
  val autoPickNext: Boolean = true,
)

internal class NewPlayerPrefs(private val sp: SharedPreferences) {

  var settings by mutableStateOf(load())
    private set

  private val values = mutableStateMapOf<String, String>()

  init {
    for ((key, value) in sp.all) {
      if (value is String && key.startsWith(assignmentPrefix)) values[key] = value
    }
  }

  fun update(change: (PlayerSettings) -> PlayerSettings) {
    val updated = change(settings)
    settings = updated
    sp.edit()
      .putString("fontFamily", updated.fontFamily)
      .putInt("size", updated.size)
      .putFloat("lineHeight", updated.lineHeight)
      .putInt("weight", updated.weight)
      .putString("align", updated.align)
      .putInt("anchor", updated.anchor)
      .putBoolean("sameSize", updated.sameSize)
      .putBoolean("blur", updated.blur)
      .putFloat("blurAmount", updated.blurAmount)
      .putInt("linesBefore", updated.linesBefore)
      .putInt("linesAfter", updated.linesAfter)
      .putString("epHighlight", updated.epHighlight)
      .putBoolean("epDim", updated.epDim)
      .putBoolean("epOwnStyles", updated.epOwnStyles)
      .putInt("epSize", updated.epSize)
      .putInt("epImageWidth", updated.epImageWidth)
      .putInt("epSplit", updated.epSplit)
      .putBoolean("useFileNames", updated.useFileNames)
      .putBoolean("autoPickNext", updated.autoPickNext)
      .apply()
  }

  private fun load(): PlayerSettings {
    val d = PlayerSettings()
    return PlayerSettings(
      fontFamily = sp.getString("fontFamily", d.fontFamily) ?: d.fontFamily,
      size = sp.getInt("size", d.size),
      lineHeight = sp.getFloat("lineHeight", d.lineHeight),
      weight = sp.getInt("weight", d.weight),
      align = sp.getString("align", d.align) ?: d.align,
      anchor = sp.getInt("anchor", d.anchor),
      sameSize = sp.getBoolean("sameSize", d.sameSize),
      blur = sp.getBoolean("blur", d.blur),
      blurAmount = sp.getFloat("blurAmount", d.blurAmount),
      linesBefore = sp.getInt("linesBefore", d.linesBefore),
      linesAfter = sp.getInt("linesAfter", d.linesAfter),
      epHighlight = sp.getString("epHighlight", d.epHighlight) ?: d.epHighlight,
      epDim = sp.getBoolean("epDim", d.epDim),
      epOwnStyles = sp.getBoolean("epOwnStyles", d.epOwnStyles),
      epSize = sp.getInt("epSize", d.epSize),
      epImageWidth = sp.getInt("epImageWidth", d.epImageWidth),
      epSplit = sp.getInt("epSplit", d.epSplit),
      useFileNames = sp.getBoolean("useFileNames", d.useFileNames),
      autoPickNext = sp.getBoolean("autoPickNext", d.autoPickNext),
    )
  }

  // per book values: the caption file and EPUB chapter of every chapter, the EPUB file, the offset

  fun captionFile(bookId: String, chapter: Int): String? = values["$assignmentPrefix$bookId:cap:$chapter"]

  fun epubChapter(bookId: String, chapter: Int): Int? =
    values["$assignmentPrefix$bookId:ep:$chapter"]?.toIntOrNull()

  fun epubFile(bookId: String): String? = values["$assignmentPrefix$bookId:epub"]

  fun offsetMs(bookId: String): Long = values["$assignmentPrefix$bookId:offset"]?.toLongOrNull() ?: 0L

  fun setCaptionFile(bookId: String, chapter: Int, file: String?) {
    put("$assignmentPrefix$bookId:cap:$chapter", file)
  }

  fun setEpubChapter(bookId: String, chapter: Int, index: Int?) {
    put("$assignmentPrefix$bookId:ep:$chapter", index?.toString())
  }

  fun setEpubFile(bookId: String, file: String?) {
    put("$assignmentPrefix$bookId:epub", file)
  }

  fun setOffsetMs(bookId: String, offset: Long) {
    put("$assignmentPrefix$bookId:offset", offset.takeIf { it != 0L }?.toString())
  }

  private fun put(key: String, value: String?) {
    if (value == null) {
      values -= key
      sp.edit().remove(key).apply()
    } else {
      values[key] = value
      sp.edit().putString(key, value).apply()
    }
  }

  private companion object {
    const val assignmentPrefix = "book:"
  }
}
