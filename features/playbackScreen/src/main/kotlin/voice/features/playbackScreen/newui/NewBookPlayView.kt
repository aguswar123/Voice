package voice.features.playbackScreen.newui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import voice.core.data.Book
import voice.core.data.BookId
import voice.core.data.displayName
import voice.core.data.durationMs
import voice.core.data.toUri
import voice.core.ui.R as UiR
import voice.core.ui.formatTime
import voice.features.playbackScreen.BookPlayViewState

internal data class ChapterRow(
  val number: Int,
  val title: String,
  val startMs: Long,
  val durationMs: Long,
)

internal fun Book.chapterRows(): List<ChapterRow> {
  val count = chapters.sumOf { it.chapterMarks.size }
  var number = 0
  var offset = 0L
  val rows = mutableListOf<ChapterRow>()
  for (chapter in chapters) {
    for (mark in chapter.chapterMarks) {
      number++
      rows +=
        ChapterRow(
          number = number,
          title = mark.displayName(number, count) ?: "Chapter $number",
          startMs = offset + mark.startMs,
          durationMs = mark.durationMs,
        )
    }
    offset += chapter.duration
  }
  return rows
}

internal data class PickerRequest(
  val epub: Boolean,
  val chapter: ChapterRow,
)

private enum class Page { Player, Captions, Epub }

private enum class Sheet { None, Chapters, Settings }

@Composable
internal fun NewBookPlayView(
  viewState: BookPlayViewState,
  bookId: BookId,
  book: Book?,
  onPlayClick: () -> Unit,
  onRewindClick: () -> Unit,
  onFastForwardClick: () -> Unit,
  onSeekInChapter: (Duration) -> Unit,
  onSeekInBook: (Duration) -> Unit,
  onSleepTimerClick: () -> Unit,
  onBookmarkClick: () -> Unit,
  onBookmarkLongClick: () -> Unit,
  onSpeedChangeClick: () -> Unit,
  onSkipToNext: () -> Unit,
  onSkipToPrevious: () -> Unit,
  onCloseClick: () -> Unit,
  onChapterClick: (Int) -> Unit,
  snackbarHostState: SnackbarHostState,
) {
  val context = LocalContext.current
  val prefs = remember {
    NewPlayerPrefs(context.getSharedPreferences("new_player", Context.MODE_PRIVATE))
  }
  val settings = prefs.settings
  val id = bookId.value
  val number = viewState.chapterNumber
  var page by rememberSaveable { mutableIntStateOf(0) }
  var sheet by remember { mutableStateOf(Sheet.None) }
  var picker by remember { mutableStateOf<PickerRequest?>(null) }
  val rows = remember(book) { book?.chapterRows().orEmpty() }

  val files by produceState<List<FolderFile>?>(null, book?.id, book?.content?.currentChapter) {
    value =
      book?.let {
        BookFolder.list(
          context,
          listOf(it.chapters.first().id.toUri(), it.currentChapter.id.toUri()),
        )
      } ?: emptyList()
  }
  val captionFiles = remember(files) { files.orEmpty().filter { isCaptionFile(it.name) } }
  val epubFiles = remember(files) { files.orEmpty().filter { isEpubFile(it.name) } }
  val epubFileName = prefs.epubFile(id) ?: epubFiles.firstOrNull()?.name
  val captionName = prefs.captionFile(id, number)
  val epubIndex = prefs.epubChapter(id, number)

  val cues by produceState<List<Cue>>(emptyList(), captionName, files) {
    val file = files?.firstOrNull { it.name == captionName }
    value =
      if (file == null) {
        emptyList()
      } else {
        BookFolder.readText(context, file.uri)?.let { parseCaptions(file.name, it) }.orEmpty()
      }
  }
  val epub by produceState<EpubBook?>(null, epubFileName, files) {
    val file = files?.firstOrNull { it.name == epubFileName }
    value =
      if (file == null) {
        null
      } else {
        BookFolder.copyToCache(context, file.uri, file.name)?.let { openEpub(it) }
      }
  }
  val blocks by produceState<List<EpubBlock>?>(null, epub, epubIndex, settings.epSplit) {
    val opened = epub
    value =
      if (opened == null || epubIndex == null) {
        null
      } else {
        readEpubChapter(opened, epubIndex).splitLong(settings.epSplit)
      }
  }

  val recommendedCaption =
    remember(files, number, captionName, rows.size, prefs.settings) {
      recommendCaption(prefs, id, number, rows.size, captionFiles)
    }
  val openedEpub = epub
  val previousEpubIndex = prefs.epubChapter(id, number - 1)
  val recommendedEpub =
    if (previousEpubIndex != null && openedEpub != null &&
      previousEpubIndex + 1 < openedEpub.chapters.size
    ) {
      previousEpubIndex + 1
    } else {
      null
    }
  LaunchedEffect(files, number, settings.autoPickNext, recommendedCaption, recommendedEpub) {
    if (files != null && settings.autoPickNext) {
      if (captionName == null && recommendedCaption != null) {
        prefs.setCaptionFile(id, number, recommendedCaption)
      }
      if (epubIndex == null && recommendedEpub != null) {
        prefs.setEpubChapter(id, number, recommendedEpub)
      }
    }
  }

  val offsetMs = prefs.offsetMs(id)
  val positionMs = viewState.playedTime.inWholeMilliseconds
  val cueIndex = cues.indexAt(positionMs + offsetMs)
  val chapterTitle =
    if (settings.useFileNames && captionName != null) {
      captionName.substringBeforeLast('.')
    } else {
      viewState.chapterName ?: viewState.title
    }
  val colors = MaterialTheme.colorScheme

  Box(Modifier.fillMaxSize().background(colors.surface)) {
    if (viewState.cover != null) {
      AsyncImage(
        model = viewState.cover,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize().blur(60.dp).alpha(0.45F),
      )
    }
    Box(Modifier.fillMaxSize().background(colors.surface.copy(alpha = 0.35F)))
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
      NewTopBar(
        title = chapterTitle,
        subtitle =
          "${viewState.chapterNumber} of ${viewState.chapterCount} chapters · " +
            "${(chapterFraction(viewState) * 100).toInt() / 5 * 5}% completed",
        fraction = chapterFraction(viewState),
        onBack = onCloseClick,
        onTitleClick = { sheet = Sheet.Chapters },
        onMore = { sheet = Sheet.Settings },
      )
      Box(Modifier.weight(1F).fillMaxWidth()) {
        when (Page.entries[page]) {
          Page.Player ->
            PlayerPage(
              viewState = viewState,
              chapterTitle = chapterTitle,
              onChapterClick = { sheet = Sheet.Chapters },
              onSpeedChangeClick = onSpeedChangeClick,
            )
          Page.Captions ->
            CaptionsPage(
              cues = cues,
              current = cueIndex,
              captionName = captionName,
              recommended = recommendedCaption,
              settings = settings,
              onCueClick = { cue ->
                onSeekInChapter((cue.startMs - offsetMs).coerceAtLeast(0L).milliseconds)
              },
              onChoose = { rows.getOrNull(number - 1)?.let { picker = PickerRequest(false, it) } },
              onUseRecommended = { recommendedCaption?.let { prefs.setCaptionFile(id, number, it) } },
            )
          Page.Epub ->
            EpubPage(
              book = epub,
              blocks = blocks,
              cues = cues,
              cueIndex = cueIndex,
              chapterFraction = chapterFraction(viewState),
              hasEpubFile = epubFileName != null,
              chapterAssigned = epubIndex != null,
              recommended = recommendedEpub,
              settings = settings,
              onChoose = { rows.getOrNull(number - 1)?.let { picker = PickerRequest(true, it) } },
              onUseRecommended = { recommendedEpub?.let { prefs.setEpubChapter(id, number, it) } },
            )
        }
        SnackbarHost(
          hostState = snackbarHostState,
          modifier = Modifier.align(Alignment.BottomCenter),
        )
      }
      NewDock(
        viewState = viewState,
        page = page,
        onPageChange = { page = it },
        onPlayClick = onPlayClick,
        onRewindClick = onRewindClick,
        onFastForwardClick = onFastForwardClick,
        onSeekInBook = onSeekInBook,
        onSkipToNext = onSkipToNext,
        onSkipToPrevious = onSkipToPrevious,
        onSleepTimerClick = onSleepTimerClick,
        onBookmarkClick = onBookmarkClick,
        onBookmarkLongClick = onBookmarkLongClick,
      )
    }
    NewSheets(
      showChapters = sheet == Sheet.Chapters,
      showSettings = sheet == Sheet.Settings,
      picker = picker,
      onDismissSheet = { sheet = Sheet.None },
      onDismissPicker = { picker = null },
      onPick = { picker = it },
      rows = rows,
      currentNumber = number,
      prefs = prefs,
      bookId = id,
      captionFiles = captionFiles,
      epubFiles = epubFiles,
      epubFileName = epubFileName,
      epub = epub,
      onChapterClick = {
        sheet = Sheet.None
        onChapterClick(it)
      },
      onSpeedChangeClick = onSpeedChangeClick,
      onSleepTimerClick = onSleepTimerClick,
    )
  }
}

private fun chapterFraction(viewState: BookPlayViewState): Float {
  val duration = viewState.duration.inWholeMilliseconds
  if (duration <= 0L) return 0F
  return (viewState.playedTime.inWholeMilliseconds.toFloat() / duration).coerceIn(0F, 1F)
}

private fun recommendCaption(
  prefs: NewPlayerPrefs,
  bookId: String,
  number: Int,
  chapterCount: Int,
  files: List<FolderFile>,
): String? {
  if (files.isEmpty() || prefs.captionFile(bookId, number) != null) return null
  val names = files.map { it.name }
  val used = (1..chapterCount).mapNotNull { prefs.captionFile(bookId, it) }.toSet()
  val start =
    if (number == 1) {
      0
    } else {
      val previous = prefs.captionFile(bookId, number - 1) ?: return null
      names.indexOf(previous) + 1
    }
  return names.drop(start).firstOrNull { it !in used }
}

@Composable
private fun NewTopBar(
  title: String,
  subtitle: String,
  fraction: Float,
  onBack: () -> Unit,
  onTitleClick: () -> Unit,
  onMore: () -> Unit,
) {
  Column {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(onClick = onBack) {
        Icon(painterResource(UiR.drawable.ic_arrow_back), contentDescription = null)
      }
      Column(
        modifier = Modifier.weight(1F).clickable(onClick = onTitleClick),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleLarge,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7F),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      IconButton(onClick = onMore) {
        Icon(painterResource(UiR.drawable.ic_more_vert), contentDescription = null)
      }
    }
    Box(
      Modifier.padding(horizontal = 24.dp)
        .fillMaxWidth()
        .height(4.dp)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15F)),
    ) {
      Box(
        Modifier.fillMaxWidth(fraction.coerceIn(0.01F, 1F))
          .height(4.dp)
          .clip(CircleShape)
          .background(MaterialTheme.colorScheme.primary),
      )
    }
  }
}

@Composable
private fun PlayerPage(
  viewState: BookPlayViewState,
  chapterTitle: String,
  onChapterClick: () -> Unit,
  onSpeedChangeClick: () -> Unit,
) {
  Column(
    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
  ) {
    AsyncImage(
      model = viewState.cover,
      contentDescription = null,
      contentScale = ContentScale.Crop,
      placeholder = painterResource(UiR.drawable.album_art),
      error = painterResource(UiR.drawable.album_art),
      modifier =
        Modifier.widthIn(max = 320.dp)
          .fillMaxWidth(0.8F)
          .aspectRatio(1F)
          .clip(RoundedCornerShape(28.dp)),
    )
    Spacer(Modifier.height(10.dp))
    Text(
      text = viewState.title,
      style = MaterialTheme.typography.headlineSmall,
      textAlign = TextAlign.Center,
    )
    viewState.author?.let {
      Text(
        text = it,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7F),
      )
    }
    Spacer(Modifier.height(6.dp))
    GlassPill(onClick = onChapterClick) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(chapterTitle, style = MaterialTheme.typography.titleMedium, maxLines = 1)
        val left = (viewState.duration - viewState.playedTime).inWholeMilliseconds.coerceAtLeast(0L)
        Text(
          "${formatTime(left)} left in chapter ${viewState.chapterNumber} of ${viewState.chapterCount}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75F),
        )
      }
    }
    GlassPill(onClick = onSpeedChangeClick) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          painterResource(UiR.drawable.ic_speed),
          contentDescription = null,
          modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.size(8.dp))
        Text("Playback speed  ${viewState.playbackSpeed.toString().removeSuffix(".0")}x")
      }
    }
  }
}

@Composable
internal fun GlassPill(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  onLongClick: (() -> Unit)? = null,
  highlighted: Boolean = false,
  content: @Composable () -> Unit,
) {
  val shape = RoundedCornerShape(22.dp)
  val onSurface = MaterialTheme.colorScheme.onSurface
  Box(
    modifier =
      modifier
        .clip(shape)
        .background(if (highlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.3F) else onSurface.copy(alpha = 0.10F))
        .combinedClickable(onClick = onClick, onLongClick = onLongClick)
        .padding(horizontal = 18.dp, vertical = 10.dp),
    contentAlignment = Alignment.Center,
  ) {
    content()
  }
}

@Composable
private fun NewDock(
  viewState: BookPlayViewState,
  page: Int,
  onPageChange: (Int) -> Unit,
  onPlayClick: () -> Unit,
  onRewindClick: () -> Unit,
  onFastForwardClick: () -> Unit,
  onSeekInBook: (Duration) -> Unit,
  onSkipToNext: () -> Unit,
  onSkipToPrevious: () -> Unit,
  onSleepTimerClick: () -> Unit,
  onBookmarkClick: () -> Unit,
  onBookmarkLongClick: () -> Unit,
) {
  val onSurface = MaterialTheme.colorScheme.onSurface
  val shape = RoundedCornerShape(30.dp)
  val bookMs = viewState.bookDuration.inWholeMilliseconds
  val playedMs = viewState.bookPlayedTime.inWholeMilliseconds
  val ticks =
    remember(viewState.chapterSegments) {
      var sum = 0F
      viewState.chapterSegments.dropLast(1).map {
        sum += it
        sum
      }
    }
  Column(
    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .clip(shape)
          .border(1.dp, onSurface.copy(alpha = 0.12F), shape)
          .padding(10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      GlassPill(
        onClick = onSleepTimerClick,
        highlighted = viewState.sleepTimerState is BookPlayViewState.SleepTimerViewState.Enabled,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            painterResource(UiR.drawable.ic_schedule),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(Modifier.size(6.dp))
          Text("Timer", style = MaterialTheme.typography.labelLarge)
        }
      }
      ViewSwitch(page = page, onPageChange = onPageChange)
      GlassPill(onClick = onBookmarkClick, onLongClick = onBookmarkLongClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            painterResource(UiR.drawable.ic_bookmark),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(Modifier.size(6.dp))
          Text("Bookmark", style = MaterialTheme.typography.labelLarge)
        }
      }
    }
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .clip(shape)
          .border(1.dp, onSurface.copy(alpha = 0.12F), shape)
          .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
      BookSeekBar(
        fraction = if (bookMs > 0) playedMs.toFloat() / bookMs else 0F,
        ticks = ticks,
        onSeek = { onSeekInBook((it * bookMs).toLong().milliseconds) },
      )
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(formatTime(playedMs, bookMs), style = MaterialTheme.typography.labelMedium)
        TransportIcon(UiR.drawable.ic_skip_previous, onSkipToPrevious)
        TransportIcon(UiR.drawable.ic_fast_rewind, onRewindClick)
        IconButton(onClick = onPlayClick, modifier = Modifier.size(56.dp)) {
          Icon(
            painterResource(
              if (viewState.playing) UiR.drawable.ic_pause else UiR.drawable.ic_play_arrow,
            ),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp),
          )
        }
        TransportIcon(UiR.drawable.ic_fast_forward, onFastForwardClick)
        TransportIcon(UiR.drawable.ic_skip_next, onSkipToNext)
        Text("-" + formatTime((bookMs - playedMs).coerceAtLeast(0L), bookMs), style = MaterialTheme.typography.labelMedium)
      }
    }
  }
}

@Composable
private fun TransportIcon(
  icon: Int,
  onClick: () -> Unit,
) {
  IconButton(onClick = onClick) {
    Icon(
      painterResource(icon),
      contentDescription = null,
      tint = MaterialTheme.colorScheme.primary,
    )
  }
}

@Composable
private fun ViewSwitch(
  page: Int,
  onPageChange: (Int) -> Unit,
) {
  val icons =
    listOf(
      UiR.drawable.ic_new_ui_waveform,
      UiR.drawable.ic_new_ui_captions,
      UiR.drawable.ic_new_ui_book_open,
    )
  Row(
    modifier =
      Modifier.clip(CircleShape)
        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10F))
        .padding(4.dp),
  ) {
    icons.forEachIndexed { index, icon ->
      val selected = index == page
      Box(
        modifier =
          Modifier.size(width = 44.dp, height = 40.dp)
            .clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable { onPageChange(index) },
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          painterResource(icon),
          contentDescription = null,
          modifier = Modifier.size(22.dp),
          tint =
            if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        )
      }
    }
  }
}
