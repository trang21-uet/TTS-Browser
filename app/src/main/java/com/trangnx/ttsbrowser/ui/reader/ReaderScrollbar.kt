package com.trangnx.ttsbrowser.ui.reader

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

private val SCROLLBAR_IDLE_WIDTH = 4.dp
private val SCROLLBAR_PRESSED_WIDTH = 12.dp
private val SCROLLBAR_TOUCH_WIDTH = 32.dp
private val MIN_THUMB_HEIGHT = 44.dp
private val THUMB_SLOP = 8.dp
private val END_MARGIN = 2.dp

private const val TRACK_ALPHA = 0.10f
private const val THUMB_IDLE_ALPHA = 0.45f
private const val THUMB_PRESSED_ALPHA = 1.0f

/**
 * Vertical fast-scroll scrollbar for reader mode:
 * - Adapts colors to the reader background and text ink.
 * - Narrow idle width, translucent track, brighter thumb, no arrows.
 * - Pressing and holding the thumb expands the scrollbar width and increases thumb opacity to 1.
 * - Dragging the thumb allows fast-scrolling through the chapter content.
 * - Tapping on the track piece above/below the thumb scrolls up/down one viewport height.
 */
@Composable
fun ReaderScrollbar(
    listState: LazyListState,
    onUserInteraction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    var trackHeightPx by remember { mutableFloatStateOf(0f) }
    var isThumbPressed by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableStateOf<Float?>(null) }

    val minThumbHeightPx = with(LocalDensity.current) { MIN_THUMB_HEIGHT.toPx() }
    val thumbSlopPx = with(LocalDensity.current) { THUMB_SLOP.toPx() }
    val endMarginPx = with(LocalDensity.current) { END_MARGIN.toPx() }

    val animatedWidth by animateDpAsState(
        targetValue = if (isThumbPressed) SCROLLBAR_PRESSED_WIDTH else SCROLLBAR_IDLE_WIDTH,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "scrollbar_width",
    )

    val animatedThumbAlpha by animateFloatAsState(
        targetValue = if (isThumbPressed) THUMB_PRESSED_ALPHA else THUMB_IDLE_ALPHA,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "scrollbar_thumb_alpha",
    )

    val thumbHeight by remember(listState, trackHeightPx, minThumbHeightPx) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val visibleItems = layoutInfo.visibleItemsInfo
            val viewportHeight = layoutInfo.viewportSize.height.toFloat()

            if (totalItems <= 1 || visibleItems.isEmpty()) return@derivedStateOf 0f

            val first = visibleItems.first()
            val last = visibleItems.last()
            val visibleCount = last.index - first.index + 1
            val visibleItemsHeight = (last.offset + last.size - first.offset).toFloat()

            computeThumbHeight(
                totalItems = totalItems,
                visibleItemsCount = visibleCount,
                visibleItemsHeight = visibleItemsHeight,
                viewportHeight = viewportHeight,
                trackHeight = trackHeightPx,
                minThumbHeight = minThumbHeightPx,
            )
        }
    }

    val availableTrack by remember(trackHeightPx, thumbHeight) {
        derivedStateOf { (trackHeightPx - thumbHeight).coerceAtLeast(0f) }
    }

    val computedProgress by remember(listState) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val visibleItems = layoutInfo.visibleItemsInfo
            if (totalItems <= 1 || visibleItems.isEmpty()) return@derivedStateOf 0f

            val first = visibleItems.first()
            val last = visibleItems.last()
            val viewportEnd = layoutInfo.viewportEndOffset - layoutInfo.afterContentPadding

            computeScrollProgress(
                totalItems = totalItems,
                firstIndex = first.index,
                firstOffset = first.offset,
                firstSize = first.size,
                lastIndex = last.index,
                lastOffset = last.offset,
                lastSize = last.size,
                viewportEnd = viewportEnd,
            )
        }
    }

    val currentProgress = (dragProgress ?: computedProgress).coerceIn(0f, 1f)
    val thumbTop = currentProgress * availableTrack

    val currentThumbTop by rememberUpdatedState(thumbTop)
    val currentThumbHeight by rememberUpdatedState(thumbHeight)
    val currentAvailableTrack by rememberUpdatedState(availableTrack)
    val currentOnUserInteraction by rememberUpdatedState(onUserInteraction)

    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = TRACK_ALPHA)
    val thumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = animatedThumbAlpha)

    var scrollJob by remember { mutableStateOf<Job?>(null) }
    var pageScrollJob by remember { mutableStateOf<Job?>(null) }
    var lastTargetIndex by remember { mutableStateOf(-1) }
    var lastTargetOffset by remember { mutableStateOf(-1) }

    val onDragToProgress: (Float) -> Unit = remember(listState) {
        { newProgress ->
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val visibleItems = layoutInfo.visibleItemsInfo
            if (totalItems > 1 && visibleItems.isNotEmpty()) {
                val first = visibleItems.first()
                val last = visibleItems.last()
                val visibleCount = last.index - first.index + 1
                val visibleItemsHeight = (last.offset + last.size - first.offset).toFloat()
                val avgItemHeight = if (visibleCount > 0) (visibleItemsHeight / visibleCount).coerceAtLeast(1f) else 100f

                val (targetIndex, targetOffset) = computeTargetScroll(
                    progress = newProgress,
                    totalItems = totalItems,
                    visibleCount = visibleCount,
                    avgItemHeight = avgItemHeight,
                )

                if (targetIndex != lastTargetIndex || abs(targetOffset - lastTargetOffset) > 8) {
                    lastTargetIndex = targetIndex
                    lastTargetOffset = targetOffset
                    scrollJob?.cancel()
                    scrollJob = coroutineScope.launch { listState.scrollToItem(targetIndex, targetOffset) }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .width(SCROLLBAR_TOUCH_WIDTH)
            .onSizeChanged { trackHeightPx = it.height.toFloat() }
            .pointerInput(listState) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val downY = down.position.y
                    val thumbTopNow = currentThumbTop
                    val thumbHeightNow = currentThumbHeight
                    val thumbBottomNow = thumbTopNow + thumbHeightNow
                    val isOverThumb = downY in (thumbTopNow - thumbSlopPx)..(thumbBottomNow + thumbSlopPx)

                    if (isOverThumb) {
                        down.consume()
                        currentOnUserInteraction()
                        isThumbPressed = true
                        val initialOffsetY = (downY - thumbTopNow).coerceIn(0f, thumbHeightNow)
                        val available = currentAvailableTrack

                        try {
                            drag(down.id) { change ->
                                change.consume()
                                if (available > 0f) {
                                    val newThumbTop = change.position.y - initialOffsetY
                                    val newProgress = (newThumbTop / available).coerceIn(0f, 1f)
                                    dragProgress = newProgress
                                    onDragToProgress(newProgress)
                                }
                            }
                        } finally {
                            isThumbPressed = false
                            dragProgress = null
                        }
                    } else {
                        val up = waitForUpOrCancellation()
                        if (up != null) {
                            up.consume()
                            currentOnUserInteraction()
                            val viewportHeight = listState.layoutInfo.viewportSize.height.toFloat()
                            if (viewportHeight > 0f) {
                                if (downY < thumbTopNow) {
                                    pageScrollJob?.cancel()
                                    pageScrollJob = coroutineScope.launch {
                                        listState.animateScrollBy(-viewportHeight)
                                    }
                                } else if (downY > thumbBottomNow) {
                                    pageScrollJob?.cancel()
                                    pageScrollJob = coroutineScope.launch {
                                        listState.animateScrollBy(viewportHeight)
                                    }
                                }
                            }
                        }
                    }
                }
            },
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            if (trackHeightPx <= 0f || thumbHeight <= 0f) return@Canvas

            val widthPx = animatedWidth.toPx()
            val left = size.width - widthPx - endMarginPx
            val cornerRadius = CornerRadius(widthPx / 2f, widthPx / 2f)

            // Draw track
            drawRoundRect(
                color = trackColor,
                topLeft = Offset(left, 0f),
                size = Size(widthPx, size.height),
                cornerRadius = cornerRadius,
            )

            // Draw thumb
            drawRoundRect(
                color = thumbColor,
                topLeft = Offset(left, thumbTop),
                size = Size(widthPx, thumbHeight),
                cornerRadius = cornerRadius,
            )
        }
    }
}

internal fun computeThumbHeight(
    totalItems: Int,
    visibleItemsCount: Int,
    visibleItemsHeight: Float,
    viewportHeight: Float,
    trackHeight: Float,
    minThumbHeight: Float,
): Float {
    if (totalItems <= 1 || visibleItemsCount <= 0 || viewportHeight <= 0f || trackHeight <= 0f) return 0f
    val avgItemHeight = (visibleItemsHeight / visibleItemsCount).coerceAtLeast(1f)
    val estimatedTotalHeight = (avgItemHeight * totalItems).coerceAtLeast(viewportHeight)
    val viewportRatio = (viewportHeight / estimatedTotalHeight).coerceIn(0f, 1f)
    val maxThumb = (trackHeight * 0.8f).coerceAtLeast(minThumbHeight)
    return (trackHeight * viewportRatio).coerceIn(minThumbHeight, maxThumb)
}

internal fun computeScrollProgress(
    totalItems: Int,
    firstIndex: Int,
    firstOffset: Int,
    firstSize: Int,
    lastIndex: Int,
    lastOffset: Int,
    lastSize: Int,
    viewportEnd: Int,
): Float {
    if (totalItems <= 1) return 0f
    val visibleCount = lastIndex - firstIndex + 1
    val maxFirstIndex = (totalItems - visibleCount).coerceAtLeast(1)
    val bottomEdge = lastOffset + lastSize

    return when {
        lastIndex == totalItems - 1 && bottomEdge <= viewportEnd -> 1f
        firstIndex == 0 && firstOffset >= 0 -> 0f
        else -> {
            val itemFraction = if (firstSize > 0) (-firstOffset.toFloat() / firstSize).coerceIn(0f, 1f) else 0f
            val currentFirstIndex = firstIndex + itemFraction
            (currentFirstIndex / maxFirstIndex).coerceIn(0f, 1f)
        }
    }
}

internal fun computeTargetScroll(
    progress: Float,
    totalItems: Int,
    visibleCount: Int,
    avgItemHeight: Float,
): Pair<Int, Int> {
    if (totalItems <= 1) return 0 to 0
    if (progress <= 0.001f) return 0 to 0
    if (progress >= 0.999f) return (totalItems - 1) to 0

    val maxFirstIndex = (totalItems - visibleCount).coerceAtLeast(1)
    val targetFloat = progress * maxFirstIndex
    val targetIndex = targetFloat.toInt().coerceIn(0, totalItems - 1)
    val fraction = targetFloat - targetIndex
    val targetOffset = (fraction * avgItemHeight).toInt()
    return targetIndex to targetOffset
}

