package com.trangnx.ttsbrowser.ui.reader

import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trangnx.ttsbrowser.R
import com.trangnx.ttsbrowser.playback.LoadedChapter

private val TEXT_PADDING_H = 24.dp
private val PARAGRAPH_PADDING_V = 8.dp
/** Lines longer than ~70 characters tire the eye, so wide screens get a centered column. */
private val MAX_TEXT_WIDTH = 680.dp
private const val HEADER_ITEMS = 1
private const val LINE_HEIGHT_RATIO = 1.7f
private const val MAX_LAYOUT_WAIT_FRAMES = 3

/**
 * Chapter text, one item per paragraph, with the sentence being read highlighted; tapping a sentence
 * reads from it. Follows the current sentence until the user drags the list; a button turns it back on.
 */
@Composable
fun ReaderText(
    loaded: LoadedChapter,
    currentIndex: Int,
    fontSizeSp: Int,
    fontFamily: FontFamily?,
    note: String?,
    onSentenceClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val chapter = loaded.chapter
    val spans = remember(loaded) { sentenceSpansByParagraph(chapter.paragraphs, loaded.sentences) }
    val current = loaded.sentences.getOrNull(currentIndex)
    val listState = rememberLazyListState()
    LaunchedEffect(chapter.url) { listState.scrollToItem(0) }
    // Not state: read by the scroll effect only, written during layout. A new font size starts an
    // empty map, so the effect never measures with layouts of the old size.
    val layouts = remember(loaded, fontSizeSp, fontFamily) { HashMap<Int, TextLayoutResult>() }
    var follow by remember(loaded) { mutableStateOf(true) }
    var animate by remember(loaded) { mutableStateOf(false) }
    val paddingPx = with(LocalDensity.current) { PARAGRAPH_PADDING_V.roundToPx() }
    val onClick by rememberUpdatedState(onSentenceClick)
    val style = MaterialTheme.typography.bodyLarge.copy(
        fontSize = fontSizeSp.sp,
        lineHeight = (fontSizeSp * LINE_HEIGHT_RATIO).sp,
        fontFamily = fontFamily ?: MaterialTheme.typography.bodyLarge.fontFamily,
        lineBreak = LineBreak.Paragraph,
    )
    val highlight = MaterialTheme.colorScheme.primaryContainer

    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { if (it is DragInteraction.Start) follow = false }
    }
    LaunchedEffect(loaded, current, follow, fontSizeSp, fontFamily) {
        if (!follow || current == null) return@LaunchedEffect
        val paragraph = current.paragraphIndex
        val span = spans.getOrNull(paragraph)?.firstOrNull { it.index == current.index } ?: return@LaunchedEffect
        listState.reveal(paragraph + HEADER_ITEMS, span, { layouts[paragraph] }, paddingPx, animate)
        animate = true // first reveal (opening the reader) jumps, later ones scroll
    }

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 88.dp),
            modifier = Modifier.fillMaxHeight().widthIn(max = MAX_TEXT_WIDTH),
        ) {
            item {
                Column(Modifier.padding(horizontal = TEXT_PADDING_H, vertical = 12.dp)) {
                    Text(
                        chapter.title,
                        style = MaterialTheme.typography.headlineSmall.copy(fontFamily = style.fontFamily, lineBreak = LineBreak.Heading),
                    )
                    if (note != null) {
                        Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            itemsIndexed(chapter.paragraphs) { index, paragraph ->
                ParagraphText(
                    paragraph = paragraph,
                    spans = spans[index],
                    highlighted = if (current?.paragraphIndex == index) current.index else -1,
                    highlight = highlight,
                    style = style,
                    onSentenceClick = {
                        follow = true
                        onClick(it)
                    },
                    onLayout = { layouts[index] = it },
                )
            }
        }
        if (!follow) {
            SmallFloatingActionButton(
                onClick = { follow = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
            ) {
                Icon(Icons.Outlined.MyLocation, contentDescription = stringResource(R.string.reader_follow))
            }
        }
    }
}

@Composable
private fun ParagraphText(
    paragraph: String,
    spans: List<SentenceSpan>,
    highlighted: Int,
    highlight: Color,
    style: TextStyle,
    onSentenceClick: (Int) -> Unit,
    onLayout: (TextLayoutResult) -> Unit,
) {
    val onClick by rememberUpdatedState(onSentenceClick)
    val text = remember(paragraph, spans, highlighted, highlight) {
        buildAnnotatedString {
            append(paragraph)
            for (span in spans) {
                // Explicit empty style: without it Compose paints links blue and underlined.
                val link = LinkAnnotation.Clickable(
                    tag = span.index.toString(),
                    styles = TextLinkStyles(SpanStyle()),
                    linkInteractionListener = { onClick(span.index) },
                )
                addLink(link, span.start, span.end)
                if (span.index == highlighted) addStyle(SpanStyle(background = highlight), span.start, span.end)
            }
        }
    }
    Text(
        text,
        style = style,
        onTextLayout = onLayout,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = TEXT_PADDING_H, vertical = PARAGRAPH_PADDING_V),
    )
}

/**
 * Scrolls so [span] of paragraph item [item] sits a quarter down the viewport, unless it is fully
 * visible already (above the bottom content padding). [layout] gives the paragraph's text layout.
 */
private suspend fun LazyListState.reveal(
    item: Int,
    span: SentenceSpan,
    layout: () -> TextLayoutResult?,
    paddingPx: Int,
    animate: Boolean,
) {
    if (layoutInfo.visibleItemsInfo.none { it.index == item }) {
        if (animate) animateScrollToItem(item) else scrollToItem(item)
    }
    // After a font change the paragraph is laid out again on the next frame(s).
    var text = layout()
    var frames = 0
    while (text == null && frames++ < MAX_LAYOUT_WAIT_FRAMES) {
        withFrameNanos { }
        text = layout()
    }
    if (text == null) return
    val info = layoutInfo.visibleItemsInfo.firstOrNull { it.index == item } ?: return
    val textTop = info.offset + paddingPx
    val top = textTop + text.getLineTop(text.getLineForOffset(span.start)).toInt()
    val bottom = textTop + text.getLineBottom(text.getLineForOffset(maxOf(span.start, span.end - 1))).toInt()
    val height = layoutInfo.viewportSize.height
    if (top >= 0 && bottom <= height - layoutInfo.afterContentPadding) return
    val delta = (top - height / 4).toFloat()
    if (animate) animateScrollBy(delta) else scrollBy(delta)
}
