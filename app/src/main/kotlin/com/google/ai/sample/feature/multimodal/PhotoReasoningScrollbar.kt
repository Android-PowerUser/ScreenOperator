package com.google.ai.sample.feature.multimodal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun VerticalScrollbar(
    modifier: Modifier = Modifier,
    listState: LazyListState
) {
    val scrollbarState by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            if (totalItems == 0) return@derivedStateOf null

            val viewportHeight = layoutInfo.viewportSize.height.toFloat()
            val firstVisibleItemIndex = listState.firstVisibleItemIndex
            val visibleItemCount = layoutInfo.visibleItemsInfo.size

            if (visibleItemCount >= totalItems) return@derivedStateOf null // All items visible, no scrollbar

            val thumbHeight = (visibleItemCount.toFloat() / totalItems * viewportHeight)
                .coerceAtLeast(20f)
                .coerceAtMost(viewportHeight)

            val maxScrollOffset = (viewportHeight - thumbHeight).coerceAtLeast(0f)
            if (maxScrollOffset == 0f) return@derivedStateOf null

            val firstItemOffset = if (layoutInfo.visibleItemsInfo.isNotEmpty()) {
                val firstItem = layoutInfo.visibleItemsInfo.first()
                if (firstItem.size > 0) listState.firstVisibleItemScrollOffset.toFloat() / firstItem.size else 0f
            } else 0f

            val scrollProgress = (firstVisibleItemIndex + firstItemOffset) /
                (totalItems - visibleItemCount).coerceAtLeast(1)
            val scrollOffset = (scrollProgress * maxScrollOffset).coerceIn(0f, maxScrollOffset)

            Pair(scrollOffset, thumbHeight)
        }
    }

    if (scrollbarState != null) {
        val (offset, height) = checkNotNull(scrollbarState)
         Canvas(modifier = modifier.width(4.dp)) {
            drawRoundRect(
                color = Color.Gray,
                topLeft = Offset(0f, offset),
                size = Size(size.width, height),
                cornerRadius = CornerRadius(4.dp.toPx())
            )
        }
    }
}
