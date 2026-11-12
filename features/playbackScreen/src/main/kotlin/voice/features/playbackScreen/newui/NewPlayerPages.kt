package voice.features.playbackScreen.newui

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import voice.core.ui.R as UiR
import kotlin.math.abs
import kotlin.math.min

private fun fontFamilyFor(name: String): FontFamily =
  when (name) {
    "serif" -> FontFamily.Serif
    "mono" -> FontFamily.Monospace
    "cursive" -> FontFamily.Cursive
    else -> FontFamily.SansSerif
  }

private fun alignFor(name: String): TextAlign =
  when (name) {
    "center" -> TextAlign.Center
    "left" -> TextAlign.Start
    else -> TextAlign.Justify
  }

/** How strongly a line at this distance from the current one is dimmed. */
private fun dimFor(distance: Int): Float =
  when (distance) {
    0 -> 1F
    1 -> 0.56F
    2 -> 0.4F
    3 -> 0.3F
    else -> 0.24F
  }

private fun blurFor(
  distance: Int,
  amount: Float,
): Float =
  when {
    distance <= 1 -> 0F
    distance == 2 -> amount / 3
    distance == 3 -> amount * 2 / 3
    else -> amount
  }

@Composable
internal fun CaptionsPage(
  cues: List<Cue>,
  current: Int,
  captionName: String?,
  recommended: String?,
  settings: PlayerSettings,
  onCueClick: (Cue) -> Unit,
  onChoose: () -> Unit,
  onUseRecommended: () -> Unit,
) {
  if (cues.isEmpty()) {
    EmptyPage(
      title = if (captionName == null) "No captions for this chapter" else "Captions could not be read",
      text =
        if (captionName == null) {
          "Pick the .srt, .vtt or .lrc file that belongs to this chapter."
        } else {
          "Check that $captionName is in the audiobook folder."
        },
      action = "Choose captions",
      recommended = recommended,
      onAction = onChoose,
      onUseRecommended = onUseRecommended,
    )
    return
  }
  val listState = rememberLazyListState()
  var following by remember { mutableStateOf(true) }
  val dragged by listState.interactionSource.collectIsDraggedAsState()
  LaunchedEffect(dragged) { if (dragged) following = false }
  BoxWithConstraints(Modifier.fillMaxSize()) {
    val topPadding = maxHeight * settings.anchor / 100
    LaunchedEffect(current, following) {
      if (following && current >= 0) listState.animateScrollToItem(current)
    }
    LazyColumn(
      state = listState,
      modifier = Modifier.fillMaxSize(),
      contentPadding =
        PaddingValues(start = 26.dp, end = 26.dp, top = topPadding, bottom = maxHeight),
    ) {
      itemsIndexed(cues) { index, cue ->
        val distance = abs(index - current)
        val outside = index < current - settings.linesBefore || index > current + settings.linesAfter
        val dim by animateFloatAsState(if (outside) 0.16F else dimFor(distance), label = "dim")
        val blurRadius by animateFloatAsState(
          if (settings.blur && distance > 0) {
            if (outside) settings.blurAmount else blurFor(distance, settings.blurAmount)
          } else {
            0F
          },
          label = "blur",
        )
        val active = index == current
        Text(
          text = cue.text,
          color =
            if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
          style =
            TextStyle(
              fontFamily = fontFamilyFor(settings.fontFamily),
              fontSize = settings.size.sp,
              lineHeight = (settings.size * settings.lineHeight).sp,
              fontWeight = FontWeight(if (active) maxOf(settings.weight, 600) else settings.weight),
              textAlign = alignFor(settings.align),
            ),
          modifier =
            Modifier.fillMaxWidth()
              .padding(bottom = 14.dp)
              .alpha(dim)
              .graphicsLayer {
                val scale = if (settings.sameSize || active) 1F else 0.94F
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0F, 0.5F)
              }
              .then(
                if (blurRadius > 0.05F) {
                  Modifier.blur(blurRadius.dp, BlurredEdgeTreatment.Unbounded)
                } else {
                  Modifier
                },
              )
              .pointerInput(cue) { detectTapGestures { onCueClick(cue) } },
        )
      }
    }
    if (!following) {
      TextButton(
        onClick = { following = true },
        modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
      ) {
        Text("Back to current line")
      }
    }
  }
}

@Composable
internal fun EpubPage(
  book: EpubBook?,
  blocks: List<EpubBlock>?,
  cues: List<Cue>,
  cueIndex: Int,
  chapterFraction: Float,
  hasEpubFile: Boolean,
  chapterAssigned: Boolean,
  recommended: Int?,
  settings: PlayerSettings,
  onChoose: () -> Unit,
  onUseRecommended: () -> Unit,
) {
  if (!hasEpubFile) {
    EmptyPage(
      title = "No EPUB found",
      text = "Put an .epub file in the same folder as the audiobook.",
      action = null,
      recommended = null,
      onAction = onChoose,
      onUseRecommended = onUseRecommended,
    )
    return
  }
  if (!chapterAssigned || blocks == null || book == null) {
    EmptyPage(
      title = if (chapterAssigned) "Reading the EPUB…" else "No EPUB chapter for this chapter",
      text = "Pick the EPUB chapter that belongs to this audiobook chapter.",
      action = "Choose EPUB chapter",
      recommended = recommended?.let { "Chapter ${it + 1} of the EPUB" },
      onAction = onChoose,
      onUseRecommended = onUseRecommended,
    )
    return
  }
  val cueBlocks = remember(cues, blocks) { matchCuesToBlocks(cues, blocks) }
  val current =
    if (cues.isNotEmpty() && cueIndex in cueBlocks.indices) {
      cueBlocks[cueIndex]
    } else {
      blockAtFraction(blocks, chapterFraction)
    }
  val listState = rememberLazyListState()
  var following by remember { mutableStateOf(true) }
  val dragged by listState.interactionSource.collectIsDraggedAsState()
  LaunchedEffect(dragged) { if (dragged) following = false }
  BoxWithConstraints(Modifier.fillMaxSize()) {
    val topPadding = maxHeight * settings.anchor / 100
    LaunchedEffect(current, following) {
      if (following && current in blocks.indices) listState.animateScrollToItem(current)
    }
    LazyColumn(
      state = listState,
      modifier = Modifier.fillMaxSize(),
      contentPadding =
        PaddingValues(start = 26.dp, end = 26.dp, top = topPadding, bottom = maxHeight),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      itemsIndexed(blocks) { index, block ->
        val distance = abs(index - current)
        val dim by animateFloatAsState(
          if (settings.epDim) min(1F, dimFor(distance) + 0.25F) else 1F,
          label = "epDim",
        )
        val blurRadius by animateFloatAsState(
          if (settings.blur && distance > 2) settings.blurAmount else 0F,
          label = "epBlur",
        )
        val modifier =
          Modifier.fillMaxWidth()
            .alpha(dim)
            .then(
              if (blurRadius > 0.05F) {
                Modifier.blur(blurRadius.dp, BlurredEdgeTreatment.Unbounded)
              } else {
                Modifier
              },
            )
        when (block) {
          is EpubBlock.Text ->
            EpubText(block, active = index == current, settings = settings, modifier = modifier)
          is EpubBlock.Image ->
            EpubImage(book = book, path = block.path, widthPercent = settings.epImageWidth)
        }
      }
    }
    if (!following) {
      TextButton(
        onClick = { following = true },
        modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
      ) {
        Text("Back to current line")
      }
    }
  }
}

private fun blockAtFraction(
  blocks: List<EpubBlock>,
  fraction: Float,
): Int {
  val lengths = blocks.map { (it as? EpubBlock.Text)?.text?.length ?: 0 }
  val total = lengths.sum()
  if (total == 0) return 0
  val target = (total * fraction).toInt()
  var sum = 0
  lengths.forEachIndexed { index, length ->
    sum += length
    if (sum >= target) return index
  }
  return blocks.lastIndex
}

@Composable
private fun EpubText(
  block: EpubBlock.Text,
  active: Boolean,
  settings: PlayerSettings,
  modifier: Modifier,
) {
  val primary = MaterialTheme.colorScheme.primary
  val highlight = settings.epHighlight
  val text =
    if (settings.epOwnStyles) {
      block.text
    } else {
      buildAnnotatedString { append(block.text.text) }
    }
  val styled =
    if (active && highlight != "none") {
      buildAnnotatedString {
        val style =
          when (highlight) {
            "bold" -> SpanStyle(fontWeight = FontWeight.Bold)
            "color" -> SpanStyle(color = primary)
            "underline" -> SpanStyle(textDecoration = TextDecoration.Underline)
            else -> SpanStyle(color = primary, fontWeight = FontWeight.Bold)
          }
        withStyle(style) { append(text) }
      }
    } else {
      text
    }
  Text(
    text = styled,
    color = MaterialTheme.colorScheme.onSurface,
    style =
      TextStyle(
        fontFamily = fontFamilyFor(settings.fontFamily),
        fontSize = (if (block.heading) settings.epSize + 4 else settings.epSize).sp,
        lineHeight = (settings.epSize * settings.lineHeight).sp,
        fontWeight = if (block.heading) FontWeight.Bold else FontWeight(settings.weight),
        textAlign = if (block.heading) TextAlign.Center else alignFor(settings.align),
        textIndent = if (block.heading) null else TextIndent(firstLine = 24.sp),
      ),
    modifier = modifier,
  )
}

@Composable
private fun EpubImage(
  book: EpubBook,
  path: String,
  widthPercent: Int,
) {
  val density = LocalDensity.current
  val maxWidthPx = with(density) { 600.dp.roundToPx() }
  val bitmap by produceState<Bitmap?>(null, book, path) {
    value = loadEpubImage(book, path, maxWidthPx)
  }
  val loaded = bitmap
  if (loaded != null) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
      Image(
        bitmap = loaded.asImageBitmap(),
        contentDescription = null,
        contentScale = ContentScale.FillWidth,
        modifier = Modifier.fillMaxWidth(widthPercent.coerceIn(10, 100) / 100F),
      )
    }
  }
}

@Composable
private fun EmptyPage(
  title: String,
  text: String,
  action: String?,
  recommended: String?,
  onAction: () -> Unit,
  onUseRecommended: () -> Unit,
) {
  Column(
    modifier = Modifier.fillMaxSize().padding(32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
  ) {
    Icon(
      painterResource(UiR.drawable.ic_new_ui_captions),
      contentDescription = null,
      modifier = Modifier.height(40.dp),
    )
    Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
    Text(
      text,
      style = MaterialTheme.typography.bodyMedium,
      textAlign = TextAlign.Center,
      color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75F),
    )
    if (action != null) Button(onClick = onAction) { Text(action) }
    if (recommended != null) TextButton(onClick = onUseRecommended) { Text("Use $recommended") }
  }
}

/** A seek bar for the whole book with a tick where every chapter starts. */
@Composable
internal fun BookSeekBar(
  fraction: Float,
  ticks: List<Float>,
  onSeek: (Float) -> Unit,
) {
  var dragFraction by remember { mutableFloatStateOf(-1F) }
  val shown = if (dragFraction >= 0F) dragFraction else fraction
  val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15F)
  val fill = MaterialTheme.colorScheme.primary
  val tick = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45F)
  Canvas(
    modifier =
      Modifier.fillMaxWidth()
        .height(32.dp)
        .pointerInput(Unit) {
          detectTapGestures { offset -> onSeek((offset.x / size.width).coerceIn(0F, 1F)) }
        }
        .pointerInput(Unit) {
          detectHorizontalDragGestures(
            onDragStart = { offset -> dragFraction = (offset.x / size.width).coerceIn(0F, 1F) },
            onDragEnd = {
              onSeek(dragFraction.coerceIn(0F, 1F))
              dragFraction = -1F
            },
            onDragCancel = { dragFraction = -1F },
            onHorizontalDrag = { change, _ ->
              dragFraction = (change.position.x / size.width).coerceIn(0F, 1F)
            },
          )
        },
  ) {
    val barHeight = 8.dp.toPx()
    val top = (size.height - barHeight) / 2
    val radius = CornerRadius(barHeight / 2)
    drawRoundRect(track, Offset(0F, top), Size(size.width, barHeight), radius)
    drawRoundRect(fill, Offset(0F, top), Size(size.width * shown.coerceIn(0F, 1F), barHeight), radius)
    ticks.forEach { t ->
      drawRect(tick, Offset(size.width * t - 1.dp.toPx(), top), Size(2.dp.toPx(), barHeight))
    }
  }
}
