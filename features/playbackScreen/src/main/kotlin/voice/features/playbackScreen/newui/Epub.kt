package voice.features.playbackScreen.newui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.net.Uri
import android.text.Html
import android.text.Spanned
import android.text.style.StyleSpan
import android.util.Xml
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import java.io.File
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser

internal fun isEpubFile(name: String): Boolean = name.lowercase().endsWith(".epub")

@Immutable
internal data class EpubChapter(
  val title: String,
  val path: String,
)

@Immutable
internal data class EpubBook(
  val file: File,
  val title: String?,
  val chapters: List<EpubChapter>,
  val spine: List<String>,
)

@Immutable
internal sealed interface EpubBlock {

  @Immutable
  data class Text(
    val text: AnnotatedString,
    val heading: Boolean,
  ) : EpubBlock

  @Immutable
  data class Image(val path: String) : EpubBlock
}

private class ManifestItem(
  val href: String,
  val mediaType: String,
  val properties: String,
)

internal suspend fun openEpub(file: File): EpubBook? = withContext(Dispatchers.IO) {
  try {
    ZipFile(file).use { zip ->
      val container = zip.readText("META-INF/container.xml") ?: return@use null
      val opfPath =
        Regex("""full-path\s*=\s*["']([^"']+)["']""").find(container)?.groupValues?.get(1)
          ?: return@use null
      val opf = zip.readText(opfPath) ?: return@use null
      parseOpf(zip, file, opfPath, opf)
    }
  } catch (e: Exception) {
    null
  }
}

private fun parseOpf(
  zip: ZipFile,
  file: File,
  opfPath: String,
  opf: String,
): EpubBook? {
  val manifest = mutableMapOf<String, ManifestItem>()
  val spineIds = mutableListOf<String>()
  var title: String? = null
  var tocId: String? = null
  val parser = Xml.newPullParser()
  parser.setInput(opf.reader())
  var event = parser.eventType
  while (event != XmlPullParser.END_DOCUMENT) {
    if (event == XmlPullParser.START_TAG) {
      when (parser.name) {
        "item" -> {
          val id = parser.getAttributeValue(null, "id")
          val href = parser.getAttributeValue(null, "href")
          if (id != null && href != null) {
            manifest[id] =
              ManifestItem(
                href = href,
                mediaType = parser.getAttributeValue(null, "media-type").orEmpty(),
                properties = parser.getAttributeValue(null, "properties").orEmpty(),
              )
          }
        }
        "itemref" -> parser.getAttributeValue(null, "idref")?.let { spineIds += it }
        "spine" -> tocId = parser.getAttributeValue(null, "toc")
        "dc:title" -> if (title == null) title = parser.nextText().trim()
      }
    }
    event = parser.next()
  }
  val spine = spineIds.mapNotNull { id -> manifest[id]?.let { resolvePath(opfPath, it.href) } }
  if (spine.isEmpty()) return null

  val navItem = manifest.values.firstOrNull { "nav" in it.properties.split(' ') }
  val ncxItem = tocId?.let { manifest[it] } ?: manifest.values.firstOrNull {
    it.mediaType == "application/x-dtbncx+xml"
  }
  val entries =
    when {
      navItem != null -> {
        val navPath = resolvePath(opfPath, navItem.href)
        zip.readText(navPath)?.let { parseNav(navPath, it) }.orEmpty()
      }
      ncxItem != null -> {
        val ncxPath = resolvePath(opfPath, ncxItem.href)
        zip.readText(ncxPath)?.let { parseNcx(ncxPath, it) }.orEmpty()
      }
      else -> emptyList()
    }
  val chapters =
    entries
      .filter { it.path in spine }
      .distinctBy { it.path }
      .sortedBy { spine.indexOf(it.path) }
      .ifEmpty {
        spine.mapIndexed { index, path -> EpubChapter("Section ${index + 1}", path) }
      }
  return EpubBook(file = file, title = title, chapters = chapters, spine = spine)
}

private fun parseNav(
  navPath: String,
  html: String,
): List<EpubChapter> {
  val nav =
    Regex("""(?is)<nav\b[^>]*epub:type\s*=\s*["'][^"']*toc[^"']*["'][^>]*>(.*?)</nav>""")
      .find(html)?.groupValues?.get(1) ?: return emptyList()
  return Regex("""(?is)<a\b[^>]*href\s*=\s*["']([^"']*)["'][^>]*>(.*?)</a>""")
    .findAll(nav)
    .map { match ->
      val href = match.groupValues[1].substringBefore('#')
      val label = htmlToPlain(match.groupValues[2])
      EpubChapter(label.ifBlank { href }, resolvePath(navPath, href))
    }
    .toList()
}

private fun parseNcx(
  ncxPath: String,
  xml: String,
): List<EpubChapter> {
  val result = mutableListOf<EpubChapter>()
  val parser = Xml.newPullParser()
  parser.setInput(xml.reader())
  var label: String? = null
  var event = parser.eventType
  while (event != XmlPullParser.END_DOCUMENT) {
    if (event == XmlPullParser.START_TAG) {
      when (parser.name) {
        "text" -> if (label == null) label = parser.nextText().trim()
        "content" -> {
          val src = parser.getAttributeValue(null, "src")
          if (src != null) {
            val path = resolvePath(ncxPath, src.substringBefore('#'))
            result += EpubChapter(label.orEmpty().ifBlank { path }, path)
          }
          label = null
        }
      }
    }
    event = parser.next()
  }
  return result
}

private fun ZipFile.readText(path: String): String? {
  val entry = getEntry(path) ?: getEntry(Uri.decode(path)) ?: return null
  return getInputStream(entry).use { it.readBytes() }.toString(Charsets.UTF_8)
}

private fun resolvePath(
  basePath: String,
  href: String,
): String {
  val decoded = Uri.decode(href)
  val parts = mutableListOf<String>()
  val baseDir = basePath.substringBeforeLast('/', "")
  val full = if (baseDir.isEmpty() || decoded.startsWith("/")) decoded else "$baseDir/$decoded"
  for (part in full.split('/')) {
    when (part) {
      "", "." -> Unit
      ".." -> if (parts.isNotEmpty()) parts.subList(parts.size - 1, parts.size).clear()
      else -> parts += part
    }
  }
  return parts.joinToString("/")
}

private fun htmlToPlain(html: String): String {
  return Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT).toString().replace(Regex("\\s+"), " ").trim()
}

/** The text of the documents that belong to one chapter of the table of contents. */
internal suspend fun readEpubChapter(
  book: EpubBook,
  chapterIndex: Int,
): List<EpubBlock> = withContext(Dispatchers.IO) {
  val chapter = book.chapters.getOrNull(chapterIndex) ?: return@withContext emptyList()
  val from = book.spine.indexOf(chapter.path)
  val to =
    book.chapters.getOrNull(chapterIndex + 1)?.let { book.spine.indexOf(it.path) }
      ?: book.spine.size
  if (from == -1) return@withContext emptyList()
  try {
    ZipFile(book.file).use { zip ->
      book.spine.subList(from, minOf(book.spine.size, maxOf(from + 1, to))).flatMap { path ->
        val html = zip.readText(path).orEmpty()
        parseBlocks(path, html)
      }
    }
  } catch (e: Exception) {
    emptyList()
  }
}

private val blockPattern =
  Regex("""(?is)<(p|h[1-6]|li|blockquote)\b[^>]*>(.*?)</\1\s*>|<img\b[^>]*>|<image\b[^>]*>""")
private val srcPattern = Regex("""(?is)(?:src|href)\s*=\s*["']([^"']+)["']""")
private val imageTagPattern = Regex("""(?is)<img\b[^>]*>|<image\b[^>]*>|<svg\b[^>]*>|</svg>""")

private fun parseBlocks(
  documentPath: String,
  html: String,
): List<EpubBlock> {
  val body =
    Regex("""(?is)<body\b[^>]*>(.*)</body>""").find(html)?.groupValues?.get(1) ?: html
  val cleaned =
    body
      .replace(Regex("""(?is)<script\b.*?</script>|<style\b.*?</style>|<!--.*?-->"""), "")
  val blocks = mutableListOf<EpubBlock>()
  for (match in blockPattern.findAll(cleaned)) {
    val whole = match.value
    if (whole.startsWith("<img", ignoreCase = true) || whole.startsWith("<image", ignoreCase = true)) {
      imageBlock(documentPath, whole)?.let { blocks += it }
      continue
    }
    val tag = match.groupValues[1].lowercase()
    val inner = match.groupValues[2]
    val images = imageTagPattern.findAll(inner).mapNotNull { imageBlock(documentPath, it.value) }.toList()
    val text = inner.replace(imageTagPattern, "").toAnnotated()
    if (text.text.isNotBlank()) {
      blocks += EpubBlock.Text(text = text, heading = tag.startsWith("h"))
    }
    blocks += images
  }
  return blocks
}

private fun imageBlock(
  documentPath: String,
  tag: String,
): EpubBlock.Image? {
  val src = srcPattern.find(tag)?.groupValues?.get(1) ?: return null
  if (src.startsWith("data:") || src.startsWith("http")) return null
  return EpubBlock.Image(resolvePath(documentPath, src))
}

private fun String.toAnnotated(): AnnotatedString {
  val spanned: Spanned = Html.fromHtml(this, Html.FROM_HTML_MODE_COMPACT)
  val raw = spanned.toString()
  val start = raw.length - raw.trimStart().length
  val trimmed = raw.trim().replace('\uFFFC', ' ')
  return buildAnnotatedString {
    append(trimmed)
    for (span in spanned.getSpans(0, spanned.length, StyleSpan::class.java)) {
      val from = (spanned.getSpanStart(span) - start).coerceIn(0, trimmed.length)
      val to = (spanned.getSpanEnd(span) - start).coerceIn(0, trimmed.length)
      if (to <= from) continue
      val style =
        when (span.style) {
          Typeface.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
          Typeface.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
          Typeface.BOLD_ITALIC ->
            SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
          else -> null
        }
      if (style != null) addStyle(style, from, to)
    }
  }
}

internal suspend fun loadEpubImage(
  book: EpubBook,
  path: String,
  maxWidthPx: Int,
): Bitmap? = withContext(Dispatchers.IO) {
  try {
    ZipFile(book.file).use { zip ->
      val entry = zip.getEntry(path) ?: zip.getEntry(Uri.decode(path)) ?: return@use null
      val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      zip.getInputStream(entry).use { stream ->
        BitmapFactory.decodeStream(stream, null, bounds)
        Unit
      }
      var sample = 1
      while (bounds.outWidth / (sample * 2) >= maxWidthPx) sample *= 2
      val options = BitmapFactory.Options().apply { inSampleSize = sample }
      zip.getInputStream(entry).use { BitmapFactory.decodeStream(it, null, options) }
    }
  } catch (e: Exception) {
    null
  }
}

/** Splits long text at the sentence end nearest to its middle, so it is easier to follow. */
internal fun List<EpubBlock>.splitLong(limit: Int): List<EpubBlock> {
  if (limit <= 0) return this
  return flatMap { block ->
    if (block is EpubBlock.Text && !block.heading) {
      splitText(block.text, limit).map { EpubBlock.Text(it, heading = false) }
    } else {
      listOf(block)
    }
  }
}

private fun splitText(
  text: AnnotatedString,
  limit: Int,
): List<AnnotatedString> {
  if (text.length <= limit) return listOf(text)
  val middle = text.length / 2
  val plain = text.text
  val cut =
    plain.indices
      .filter { it in 1 until plain.length - 1 && plain[it] in ".!?" && plain[it + 1] == ' ' }
      .minByOrNull { kotlin.math.abs(it - middle) }
      ?: return listOf(text)
  val first = text.subSequence(0, cut + 1)
  val second = text.subSequence(cut + 2, text.length)
  return splitText(first, limit) + splitText(second, limit)
}

private val wordPattern = Regex("""[\p{L}\p{N}]+""")

private fun words(text: String): List<String> {
  return wordPattern.findAll(text).map { it.value.lowercase() }.toList()
}

/** For every cue, the block of the EPUB text where its words are read. */
internal fun matchCuesToBlocks(
  cues: List<Cue>,
  blocks: List<EpubBlock>,
): IntArray {
  val flat = mutableListOf<String>()
  val blockOfWord = mutableListOf<Int>()
  blocks.forEachIndexed { index, block ->
    if (block is EpubBlock.Text) {
      for (word in words(block.text.text)) {
        flat += word
        blockOfWord += index
      }
    }
  }
  val result = IntArray(cues.size)
  var pointer = 0
  var lastBlock = blocks.indexOfFirst { it is EpubBlock.Text }.coerceAtLeast(0)
  cues.forEachIndexed { cueIndex, cue ->
    val cueWords = words(cue.text).take(5)
    val need = minOf(3, cueWords.size)
    if (need > 0) {
      val windowEnd = minOf(flat.size - need, pointer + 600)
      var found = -1
      var position = pointer
      while (position <= windowEnd && found == -1) {
        if ((0 until need).all { flat[position + it] == cueWords[it] }) found = position
        position++
      }
      if (found != -1) {
        pointer = found + cueWords.size
        lastBlock = blockOfWord[found]
      }
    }
    result[cueIndex] = lastBlock
  }
  return result
}
