package voice.features.playbackScreen.newui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import voice.core.ui.R as UiR
import voice.core.ui.formatTime

@Composable
internal fun NewSheets(
  showChapters: Boolean,
  showSettings: Boolean,
  picker: PickerRequest?,
  onDismissSheet: () -> Unit,
  onDismissPicker: () -> Unit,
  onPick: (PickerRequest) -> Unit,
  rows: List<ChapterRow>,
  currentNumber: Int,
  prefs: NewPlayerPrefs,
  bookId: String,
  captionFiles: List<FolderFile>,
  epubFiles: List<FolderFile>,
  epubFileName: String?,
  epub: EpubBook?,
  onChapterClick: (Int) -> Unit,
  onSpeedChangeClick: () -> Unit,
  onSleepTimerClick: () -> Unit,
) {
  if (showChapters) {
    SheetFrame(title = "Chapters", onDismiss = onDismissSheet) {
      ChaptersList(
        rows = rows,
        currentNumber = currentNumber,
        prefs = prefs,
        bookId = bookId,
        onChapterClick = onChapterClick,
        onPick = onPick,
      )
    }
  }
  if (showSettings) {
    SheetFrame(title = "Player settings", onDismiss = onDismissSheet) {
      SettingsList(
        prefs = prefs,
        bookId = bookId,
        onSpeedChangeClick = {
          onDismissSheet()
          onSpeedChangeClick()
        },
        onSleepTimerClick = {
          onDismissSheet()
          onSleepTimerClick()
        },
      )
    }
  }
  if (picker != null) {
    SheetFrame(
      title = if (picker.epub) "Choose EPUB chapter" else "Choose captions",
      onDismiss = onDismissPicker,
    ) {
      if (picker.epub) {
        EpubPicker(
          request = picker,
          prefs = prefs,
          bookId = bookId,
          epubFiles = epubFiles,
          epubFileName = epubFileName,
          epub = epub,
          onDone = onDismissPicker,
        )
      } else {
        CaptionPicker(
          request = picker,
          prefs = prefs,
          bookId = bookId,
          files = captionFiles,
          onDone = onDismissPicker,
        )
      }
    }
  }
}

@Composable
private fun SheetFrame(
  title: String,
  onDismiss: () -> Unit,
  content: @Composable () -> Unit,
) {
  Box(
    modifier =
      Modifier.fillMaxSize()
        .background(Color.Black.copy(alpha = 0.5F))
        .pointerInput(Unit) { detectTapGestures { onDismiss() } },
    contentAlignment = Alignment.BottomCenter,
  ) {
    Surface(
      modifier =
        Modifier.fillMaxWidth()
          .fillMaxHeight(0.82F)
          .pointerInput(Unit) { detectTapGestures { } },
      shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
      color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
      Column {
        Row(
          modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.weight(1F),
          )
          IconButton(onClick = onDismiss) {
            Icon(painterResource(UiR.drawable.ic_close), contentDescription = null)
          }
        }
        content()
      }
    }
  }
}

@Composable
private fun ChaptersList(
  rows: List<ChapterRow>,
  currentNumber: Int,
  prefs: NewPlayerPrefs,
  bookId: String,
  onChapterClick: (Int) -> Unit,
  onPick: (PickerRequest) -> Unit,
) {
  val settings = prefs.settings
  LazyColumn(Modifier.fillMaxSize()) {
    itemsIndexed(rows) { _, row ->
      val caption = prefs.captionFile(bookId, row.number)
      val epubChapter = prefs.epubChapter(bookId, row.number)
      val title =
        if (settings.useFileNames && caption != null) caption.substringBeforeLast('.') else row.title
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .clickable { onChapterClick(row.number) }
            .padding(start = 24.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(Modifier.weight(1F)) {
          Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color =
              if (row.number == currentNumber) {
                MaterialTheme.colorScheme.primary
              } else {
                MaterialTheme.colorScheme.onSurface
              },
            maxLines = 1,
          )
          Text(
            text = formatTime(row.durationMs),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7F),
          )
        }
        IconButton(onClick = { onPick(PickerRequest(epub = false, chapter = row)) }) {
          Icon(
            painterResource(UiR.drawable.ic_new_ui_cc),
            contentDescription = null,
            tint =
              if (caption != null) {
                MaterialTheme.colorScheme.primary
              } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5F)
              },
          )
        }
        IconButton(onClick = { onPick(PickerRequest(epub = true, chapter = row)) }) {
          Icon(
            painterResource(UiR.drawable.ic_new_ui_book_open),
            contentDescription = null,
            tint =
              if (epubChapter != null) {
                MaterialTheme.colorScheme.primary
              } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5F)
              },
          )
        }
      }
    }
  }
}

@Composable
private fun CaptionPicker(
  request: PickerRequest,
  prefs: NewPlayerPrefs,
  bookId: String,
  files: List<FolderFile>,
  onDone: () -> Unit,
) {
  val chosen = prefs.captionFile(bookId, request.chapter.number)
  Column(Modifier.fillMaxSize()) {
    Text(
      text = "${request.chapter.title} · .srt, .vtt and .lrc files in the audiobook folder",
      style = MaterialTheme.typography.bodySmall,
      modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
    )
    if (files.isEmpty()) {
      Text(
        text = "No caption files found next to the audio files.",
        modifier = Modifier.padding(24.dp),
      )
    }
    LazyColumn(Modifier.weight(1F)) {
      itemsIndexed(files) { _, file ->
        PickRow(name = file.name, selected = file.name == chosen) {
          prefs.setCaptionFile(bookId, request.chapter.number, file.name)
          onDone()
        }
      }
    }
    if (chosen != null) {
      TextButton(
        onClick = {
          prefs.setCaptionFile(bookId, request.chapter.number, null)
          onDone()
        },
        modifier = Modifier.padding(horizontal = 16.dp),
      ) {
        Text("Remove captions from this chapter")
      }
    }
  }
}

@Composable
private fun EpubPicker(
  request: PickerRequest,
  prefs: NewPlayerPrefs,
  bookId: String,
  epubFiles: List<FolderFile>,
  epubFileName: String?,
  epub: EpubBook?,
  onDone: () -> Unit,
) {
  val chosen = prefs.epubChapter(bookId, request.chapter.number)
  Column(Modifier.fillMaxSize()) {
    Text(
      text = "${request.chapter.title} · ${epubFileName ?: "no EPUB found"}",
      style = MaterialTheme.typography.bodySmall,
      modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
    )
    if (epubFiles.size > 1) {
      Row(
        modifier =
          Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        epubFiles.forEach { file ->
          FilterChip(
            selected = file.name == epubFileName,
            onClick = { prefs.setEpubFile(bookId, file.name) },
            label = { Text(file.name, maxLines = 1) },
          )
        }
      }
    }
    if (epub == null) {
      Text(
        text =
          if (epubFiles.isEmpty()) {
            "No EPUB file found next to the audio files."
          } else {
            "Reading the EPUB…"
          },
        modifier = Modifier.padding(24.dp),
      )
    } else {
      LazyColumn(Modifier.weight(1F)) {
        itemsIndexed(epub.chapters) { index, chapter ->
          PickRow(name = chapter.title, selected = index == chosen) {
            prefs.setEpubChapter(bookId, request.chapter.number, index)
            onDone()
          }
        }
      }
    }
    if (chosen != null) {
      TextButton(
        onClick = {
          prefs.setEpubChapter(bookId, request.chapter.number, null)
          onDone()
        },
        modifier = Modifier.padding(horizontal = 16.dp),
      ) {
        Text("Remove EPUB chapter from this chapter")
      }
    }
  }
}

@Composable
private fun PickRow(
  name: String,
  selected: Boolean,
  onClick: () -> Unit,
) {
  Row(
    modifier =
      Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = name,
      style = MaterialTheme.typography.bodyLarge,
      color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.weight(1F),
    )
    if (selected) {
      Icon(
        painterResource(UiR.drawable.ic_check),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(20.dp),
      )
    }
  }
}

@Composable
private fun SettingsList(
  prefs: NewPlayerPrefs,
  bookId: String,
  onSpeedChangeClick: () -> Unit,
  onSleepTimerClick: () -> Unit,
) {
  val s = prefs.settings
  Column(
    modifier =
      Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    SectionTitle("Text and fonts")
    Choice(
      "Font",
      listOf("sans" to "Sans", "serif" to "Serif", "mono" to "Mono", "cursive" to "Cursive"),
      s.fontFamily,
    ) { prefs.update { old -> old.copy(fontFamily = it) } }
    SliderRow("Font size", "${s.size} sp", s.size.toFloat(), 12F..40F) {
      prefs.update { old -> old.copy(size = it.roundToInt()) }
    }
    SliderRow("Line spacing", "%.2f".format(s.lineHeight), s.lineHeight, 1F..2F) {
      prefs.update { old -> old.copy(lineHeight = (it * 20).roundToInt() / 20F) }
    }
    SliderRow("Font thickness", "${s.weight}", s.weight.toFloat(), 300F..700F, steps = 3) {
      prefs.update { old -> old.copy(weight = (it / 100).roundToInt() * 100) }
    }
    Choice(
      "Alignment",
      listOf("left" to "Left", "center" to "Center", "justify" to "Justify"),
      s.align,
    ) { prefs.update { old -> old.copy(align = it) } }
    SliderRow("Current line position", "${s.anchor}%", s.anchor.toFloat(), 5F..60F) {
      prefs.update { old -> old.copy(anchor = it.roundToInt()) }
    }
    SwitchRow("Keep all lines the same size", s.sameSize) {
      prefs.update { old -> old.copy(sameSize = it) }
    }

    SectionTitle("Focus and blur")
    SwitchRow("Blur lines far from the current one", s.blur) {
      prefs.update { old -> old.copy(blur = it) }
    }
    SliderRow("Blur amount", "%.1f".format(s.blurAmount), s.blurAmount, 0F..8F) {
      prefs.update { old -> old.copy(blurAmount = (it * 2).roundToInt() / 2F) }
    }
    SliderRow("Lines kept before", "${s.linesBefore}", s.linesBefore.toFloat(), 0F..6F, steps = 5) {
      prefs.update { old -> old.copy(linesBefore = it.roundToInt()) }
    }
    SliderRow("Lines kept after", "${s.linesAfter}", s.linesAfter.toFloat(), 2F..14F, steps = 11) {
      prefs.update { old -> old.copy(linesAfter = it.roundToInt()) }
    }

    SectionTitle("EPUB text")
    Choice(
      "Highlight",
      listOf(
        "colorbold" to "Color and bold",
        "bold" to "Bold",
        "color" to "Color",
        "underline" to "Underline",
        "none" to "None",
      ),
      s.epHighlight,
    ) { prefs.update { old -> old.copy(epHighlight = it) } }
    SwitchRow("Dim the other paragraphs", s.epDim) { prefs.update { old -> old.copy(epDim = it) } }
    SwitchRow("Keep the book's italics and bold", s.epOwnStyles) {
      prefs.update { old -> old.copy(epOwnStyles = it) }
    }
    SliderRow("EPUB text size", "${s.epSize} sp", s.epSize.toFloat(), 12F..36F) {
      prefs.update { old -> old.copy(epSize = it.roundToInt()) }
    }
    SliderRow("Picture width", "${s.epImageWidth}%", s.epImageWidth.toFloat(), 10F..100F) {
      prefs.update { old -> old.copy(epImageWidth = it.roundToInt()) }
    }
    Choice(
      "Split long paragraphs",
      listOf("0" to "Off", "500" to "Long", "300" to "Short"),
      s.epSplit.toString(),
    ) { prefs.update { old -> old.copy(epSplit = it.toInt()) } }

    SectionTitle("Captions")
    val offsetMs = prefs.offsetMs(bookId)
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text("Timing offset", Modifier.weight(1F))
      TextButton(onClick = { prefs.setOffsetMs(bookId, offsetMs - 500) }) { Text("−0.5 s") }
      Text("%.1f s".format(offsetMs / 1000F))
      TextButton(onClick = { prefs.setOffsetMs(bookId, offsetMs + 500) }) { Text("+0.5 s") }
    }
    SwitchRow("Use caption file names as chapter names", s.useFileNames) {
      prefs.update { old -> old.copy(useFileNames = it) }
    }
    SwitchRow("Pick the next files for the next chapter", s.autoPickNext) {
      prefs.update { old -> old.copy(autoPickNext = it) }
    }

    SectionTitle("Playback")
    TextButton(onClick = onSpeedChangeClick) { Text("Playback speed") }
    TextButton(onClick = onSleepTimerClick) { Text("Sleep timer") }
    Box(Modifier.padding(bottom = 24.dp))
  }
}

@Composable
private fun SectionTitle(text: String) {
  Text(
    text = text,
    style = MaterialTheme.typography.titleMedium,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(top = 16.dp),
  )
}

@Composable
private fun SwitchRow(
  label: String,
  checked: Boolean,
  onChange: (Boolean) -> Unit,
) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Text(label, Modifier.weight(1F))
    Switch(checked = checked, onCheckedChange = onChange)
  }
}

@Composable
private fun SliderRow(
  label: String,
  valueText: String,
  value: Float,
  range: ClosedFloatingPointRange<Float>,
  steps: Int = 0,
  onChange: (Float) -> Unit,
) {
  Column {
    Row {
      Text(label, Modifier.weight(1F))
      Text(valueText, color = MaterialTheme.colorScheme.primary)
    }
    Slider(value = value.coerceIn(range), onValueChange = onChange, valueRange = range, steps = steps)
  }
}

@Composable
private fun Choice(
  label: String,
  options: List<Pair<String, String>>,
  selected: String,
  onSelect: (String) -> Unit,
) {
  Column {
    Text(label)
    Row(
      modifier = Modifier.horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      options.forEach { (value, text) ->
        FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(text) })
      }
    }
  }
}
