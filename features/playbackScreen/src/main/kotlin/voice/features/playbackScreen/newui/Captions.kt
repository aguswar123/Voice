package voice.features.playbackScreen.newui

import androidx.compose.runtime.Immutable

@Immutable
internal data class Cue(
  val startMs: Long,
  val endMs: Long,
  val text: String,
)

internal fun isCaptionFile(name: String): Boolean {
  return name.substringAfterLast('.', "").lowercase() in captionExtensions
}

private val captionExtensions = setOf("srt", "vtt", "lrc")

private val timePattern = Regex("""(?:(\d+):)?(\d+):(\d+)(?:[.,](\d+))?""")
private val lrcPattern = Regex("""\[(\d+):(\d+)(?:[.:](\d+))?]""")
private val tagPattern = Regex("""<[^>]+>|\{\\[^}]*}""")

internal fun parseCaptions(
  fileName: String,
  content: String,
): List<Cue> {
  val text = content.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
  val cues =
    if (fileName.substringAfterLast('.', "").lowercase() == "lrc") {
      parseLrc(text)
    } else {
      parseBlocks(text)
    }
  return cues.sortedBy { it.startMs }
}

private fun parseBlocks(text: String): List<Cue> {
  val cues = mutableListOf<Cue>()
  for (block in text.split(Regex("\n{2,}"))) {
    val lines = block.lines()
    val timingIndex = lines.indexOfFirst { "-->" in it }
    if (timingIndex == -1) continue
    val parts = lines[timingIndex].split("-->")
    val start = parseTime(parts[0]) ?: continue
    val end = parseTime(parts[1]) ?: continue
    val cueText =
      lines
        .drop(timingIndex + 1)
        .joinToString(" ") { it.trim() }
        .replace(tagPattern, "")
        .let(::decodeEntities)
        .replace(Regex("\\s+"), " ")
        .trim()
    if (cueText.isNotEmpty()) {
      cues += Cue(startMs = start, endMs = maxOf(end, start + 1), text = cueText)
    }
  }
  return cues
}

private fun parseLrc(text: String): List<Cue> {
  val entries = mutableListOf<Pair<Long, String>>()
  for (line in text.lines()) {
    val stamps = lrcPattern.findAll(line).toList()
    if (stamps.isEmpty()) continue
    val lyric = line.substring(stamps.last().range.last + 1).trim()
    if (lyric.isEmpty()) continue
    for (stamp in stamps) {
      val (minutes, seconds, fraction) = stamp.destructured
      val ms =
        minutes.toLong() * 60_000 + seconds.toLong() * 1_000 + fractionToMs(fraction)
      entries += ms to lyric
    }
  }
  val sorted = entries.sortedBy { it.first }
  return sorted.mapIndexed { index, (start, lyric) ->
    val end = sorted.getOrNull(index + 1)?.first ?: (start + 4_000)
    Cue(startMs = start, endMs = maxOf(end, start + 1), text = lyric)
  }
}

private fun parseTime(raw: String): Long? {
  val match = timePattern.find(raw.trim()) ?: return null
  val (hours, minutes, seconds, fraction) = match.destructured
  val h = if (hours.isEmpty()) 0L else hours.toLong()
  return h * 3_600_000 + minutes.toLong() * 60_000 + seconds.toLong() * 1_000 +
    fractionToMs(fraction)
}

private fun fractionToMs(fraction: String): Long {
  if (fraction.isEmpty()) return 0L
  return fraction.padEnd(3, '0').take(3).toLong()
}

private fun decodeEntities(text: String): String {
  return text
    .replace("&amp;", "&")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .replace("&nbsp;", " ")
}

internal fun List<Cue>.indexAt(positionMs: Long): Int {
  if (isEmpty()) return -1
  var low = 0
  var high = lastIndex
  var result = 0
  while (low <= high) {
    val mid = (low + high) ushr 1
    if (this[mid].startMs <= positionMs) {
      result = mid
      low = mid + 1
    } else {
      high = mid - 1
    }
  }
  return result
}

private val digits = Regex("""\d+""")

internal val naturalNameOrder: Comparator<String> =
  Comparator { a, b ->
    val numberA = digits.find(a)?.value?.toLongOrNull()
    val numberB = digits.find(b)?.value?.toLongOrNull()
    if (numberA != null && numberB != null && numberA != numberB) {
      numberA.compareTo(numberB)
    } else {
      a.compareTo(b, ignoreCase = true)
    }
  }
