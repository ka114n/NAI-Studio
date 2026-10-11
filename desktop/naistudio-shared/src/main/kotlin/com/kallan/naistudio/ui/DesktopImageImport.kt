package com.kallan.naistudio.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.kallan.naistudio.i18n.RuntimeText
import com.kallan.naistudio.platform.LocalUiHost
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.dnd.DropTargetDragEvent
import java.awt.dnd.DropTargetDropEvent
import java.io.File

private val imageExtensions = setOf("png", "jpg", "jpeg", "webp", "bmp", "gif")

@OptIn(ExperimentalComposeUiApi::class)
private fun DragAndDropEvent.transferable(): Transferable? = when (val native = nativeEvent) {
    is DropTargetDropEvent -> native.transferable
    is DropTargetDragEvent -> native.transferable
    else -> null
}

@OptIn(ExperimentalComposeUiApi::class)
private fun DragAndDropEvent.isFileDrag(): Boolean = runCatching {
    when (val native = nativeEvent) {
        is DropTargetDragEvent -> native.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
        is DropTargetDropEvent -> native.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
        else -> transferable()?.isDataFlavorSupported(DataFlavor.javaFileListFlavor) == true
    }
}.getOrDefault(false)

private fun DragAndDropEvent.firstFile(): File? = runCatching {
    (transferable()?.getTransferData(DataFlavor.javaFileListFlavor) as? List<*>)?.firstOrNull() as? File
}.getOrNull()

private class ImageRegionDropTarget(
    val onImage: (String) -> Unit,
    val onNotImage: () -> Unit,
) : DragAndDropTarget {
    var hovering by mutableStateOf(false)
        private set
    override fun onEntered(event: DragAndDropEvent) { hovering = true }
    override fun onExited(event: DragAndDropEvent) { hovering = false }
    override fun onEnded(event: DragAndDropEvent) { hovering = false }
    override fun onDrop(event: DragAndDropEvent): Boolean {
        hovering = false
        val image = event.firstFile()?.takeIf { it.isFile && it.extension.lowercase() in imageExtensions }
        if (image == null) { onNotImage(); return false }
        onImage(image.absolutePath)
        return true
    }
}

/** Each region owns its native drop target; dropping on another panel never imports to the canvas. */
@Composable
internal fun Modifier.desktopImageDropTarget(language: String, onImage: (String) -> Unit): Modifier {
    val host = LocalUiHost.current
    val currentImage by rememberUpdatedState(onImage)
    val currentLanguage by rememberUpdatedState(language)
    val target = remember(host) {
        ImageRegionDropTarget({ currentImage(it) }, {
            host.toast(RuntimeText.text(currentLanguage, "drop.notImage"), long = true)
        })
    }
    val accent = NaiSkinTokens.accent()
    return this.dragAndDropTarget(shouldStartDragAndDrop = { it.isFileDrag() }, target = target)
        .drawWithContent {
            drawContent()
            if (target.hovering) {
                val inset = 3.dp.toPx()
                drawRoundRect(accent, Offset(inset, inset),
                    Size((size.width - 2 * inset).coerceAtLeast(0f), (size.height - 2 * inset).coerceAtLeast(0f)),
                    CornerRadius(10.dp.toPx()), style = Stroke(2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 5.dp.toPx()))))
            }
        }
}

/** A bounded, clickable preview always fits the complete image rather than cropping it. */
@Composable
internal fun ToolImageImportPreview(path: String?, hint: String, onPick: () -> Unit) {
    val border = NaiSkinTokens.controlBorder()
    Box(Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(12.dp))
        .background(NaiSkinTokens.field()).clickable(onClick = onPick).drawWithContent {
            drawContent()
            val inset = 1.dp.toPx()
            drawRoundRect(border, Offset(inset, inset),
                Size((size.width - 2 * inset).coerceAtLeast(0f), (size.height - 2 * inset).coerceAtLeast(0f)),
                CornerRadius(12.dp.toPx()), style = Stroke(1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))))
        }, contentAlignment = Alignment.Center) {
        if (path != null) {
            FileImage(path = path, maxDimension = 1024, modifier = Modifier.fillMaxSize().padding(10.dp),
                contentScale = ContentScale.Fit, alignment = Alignment.Center)
        } else {
            Text(hint, modifier = Modifier.padding(16.dp), color = NaiSkinTokens.muted(),
                style = MaterialTheme.typography.bodySmall)
        }
    }
}
