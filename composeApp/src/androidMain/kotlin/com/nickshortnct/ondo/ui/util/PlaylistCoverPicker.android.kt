package com.nickshortnct.ondo.ui.util

import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ondo.composeapp.generated.resources.Res
import ondo.composeapp.generated.resources.action_cancel
import ondo.composeapp.generated.resources.action_use_crop
import ondo.composeapp.generated.resources.notice_drag_playlist_crop
import ondo.composeapp.generated.resources.title_crop_playlist_cover
import org.jetbrains.compose.resources.stringResource
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
actual fun rememberPlaylistCoverPicker(onPicked: (ByteArray?) -> Unit): () -> Unit {
	val context = LocalContext.current
	val currentOnPicked by rememberUpdatedState(onPicked)
	var bitmapToCrop by remember { mutableStateOf<Bitmap?>(null) }
	val scope = rememberCoroutineScope()
	var decodeJob by remember { mutableStateOf<Job?>(null) }
	var saving by remember { mutableStateOf(false) }
	val launcher = rememberLauncherForActivityResult(
		contract = ActivityResultContracts.PickVisualMedia(),
	) { uri: Uri? ->
		if (uri != null) {
			decodeJob?.cancel()
			decodeJob = scope.launch {
				bitmapToCrop = withContext(Dispatchers.Default) {
					runCatching {
						val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
						context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
						var sample = 1
						while (max(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2
						val options = BitmapFactory.Options().apply { inSampleSize = sample }
						val decoded = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
						val orientation = context.contentResolver.openInputStream(uri)?.use {
							ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
						} ?: ExifInterface.ORIENTATION_NORMAL
						decoded?.let { orientCover(it, orientation) }
					}.getOrNull()
				}
				if (bitmapToCrop == null) currentOnPicked(null)
			}
		}
	}

	bitmapToCrop?.let { bitmap ->
		PlaylistCoverCropDialog(
			bitmap = bitmap,
			saving = saving,
			onDismiss = {
				bitmapToCrop = null
				currentOnPicked(null)
			},
			onSave = { zoom, offset, frameSizePx ->
				if (!saving) {
					saving = true
					scope.launch {
						try {
							val cropped = withContext(Dispatchers.Default) { cropSquareImage(bitmap, zoom, offset, frameSizePx) }
							bitmapToCrop = null
							currentOnPicked(cropped)
						} finally { saving = false }
					}
				}
			}
		)
	}

	return {
		launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
	}
}

@Composable
private fun PlaylistCoverCropDialog(
	bitmap: Bitmap,
	saving: Boolean,
	onDismiss: () -> Unit,
	onSave: (zoom: Float, offset: Offset, frameSizePx: Float) -> Unit
) {
	var zoom by remember(bitmap) { mutableFloatStateOf(1f) }
	var offset by remember(bitmap) { mutableStateOf(Offset.Zero) }
	val density = LocalDensity.current
	Dialog(
		onDismissRequest = onDismiss,
		properties = DialogProperties(usePlatformDefaultWidth = false)
	) {
		Surface(
			modifier = Modifier.fillMaxWidth().widthIn(max = 440.dp).padding(horizontal = 20.dp),
			shape = MaterialTheme.shapes.extraLarge,
			color = MaterialTheme.colorScheme.surfaceContainerHigh,
			tonalElevation = 6.dp
		) {
			BoxWithConstraints(Modifier.fillMaxWidth()) {
				val previewSize = minOf(maxWidth - 40.dp, 340.dp)
				val frameSizePx = with(density) { previewSize.toPx() }
				Column(
					modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
					verticalArrangement = Arrangement.spacedBy(12.dp),
					horizontalAlignment = Alignment.CenterHorizontally
				) {
					Text(stringResource(Res.string.title_crop_playlist_cover), style = MaterialTheme.typography.titleLarge)
					Box(
						modifier = Modifier
							.size(previewSize)
							.clip(RoundedCornerShape(16.dp))
							.background(MaterialTheme.colorScheme.surface)
							.pointerInput(bitmap, zoom, frameSizePx) {
								detectDragGestures { change, dragAmount ->
									change.consume()
									offset = clampCropOffset(bitmap, zoom, frameSizePx, offset + dragAmount)
								}
							}
							.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp)),
						contentAlignment = Alignment.Center
					) {
						Image(
							bitmap.asImageBitmap(),
							contentDescription = null,
							contentScale = ContentScale.Crop,
							modifier = Modifier.fillMaxSize().graphicsLayer {
								scaleX = zoom
								scaleY = zoom
								translationX = offset.x
								translationY = offset.y
							}
						)
					}
					Text(stringResource(Res.string.notice_drag_playlist_crop), style = MaterialTheme.typography.bodyMedium)
					Slider(
						value = zoom,
						valueRange = 1f..3f,
						onValueChange = { value ->
							zoom = value
							offset = clampCropOffset(bitmap, value, frameSizePx, offset)
						}
					)
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
						verticalAlignment = Alignment.CenterVertically
					) {
						TextButton(enabled = !saving, onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
						Button(enabled = !saving, onClick = { onSave(zoom, offset, frameSizePx) }) {
							Text(stringResource(Res.string.action_use_crop))
						}
					}
				}
			}
		}
	}
}

private fun clampCropOffset(bitmap: Bitmap, zoom: Float, frameSizePx: Float, offset: Offset): Offset {
	val baseScale = frameSizePx / min(bitmap.width, bitmap.height).toFloat()
	val maxX = max(0f, (bitmap.width * baseScale * zoom - frameSizePx) / 2f)
	val maxY = max(0f, (bitmap.height * baseScale * zoom - frameSizePx) / 2f)
	return Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
}

private fun cropSquareImage(bitmap: Bitmap, zoom: Float, offset: Offset, frameSizePx: Float): ByteArray? = runCatching {
	val minSide = min(bitmap.width, bitmap.height).toFloat()
	val baseScale = frameSizePx / minSide
	val side = (minSide / zoom).roundToInt().coerceIn(1, minSide.roundToInt())
	val centerX = bitmap.width / 2f - offset.x / (baseScale * zoom)
	val centerY = bitmap.height / 2f - offset.y / (baseScale * zoom)
	val left = (centerX - side / 2f).roundToInt().coerceIn(0, bitmap.width - side)
	val top = (centerY - side / 2f).roundToInt().coerceIn(0, bitmap.height - side)
	val crop = Bitmap.createBitmap(bitmap, left, top, side, side)
	ByteArrayOutputStream().use { stream ->
		crop.compress(Bitmap.CompressFormat.JPEG, 94, stream)
		stream.toByteArray()
	}
}.getOrNull()

private fun orientCover(bitmap: Bitmap, orientation: Int): Bitmap {
	val matrix = Matrix()
	when (orientation) {
		ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
		ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
		ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
		ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
		ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
		ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(-90f); matrix.postScale(-1f, 1f) }
		ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270f)
		else -> return bitmap
	}
	return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}
