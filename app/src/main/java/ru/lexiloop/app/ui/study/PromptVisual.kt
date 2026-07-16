package ru.lexiloop.app.ui.study

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Size
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The site's cinematic flashcard-image reveals (`.prompt-visual` +
 * `anim-*` keyframes in styles.css), shared by the Study page and the
 * Settings "Reveal animations" section.
 */
data class RevealChoice(val id: String, val label: String, val hint: String)

object RevealAnimations {
    const val FADE = "fade"

    val CHOICES = listOf(
        RevealChoice("mist", "Morning mist", "the memory sharpens out of a blur"),
        RevealChoice("ripple", "Ripple", "one drop lands and spreads from the middle"),
        RevealChoice("drift", "Slow drift", "a quiet cinematic pan settles into place"),
        RevealChoice("droplets", "Watercolor droplets", "soft drops slowly soak through the card and merge"),
    )

    val DEFAULT_SECONDS = mapOf("mist" to 2.5, "ripple" to 2.5, "drift" to 2.5, "droplets" to 8.0)

    /** A card keeps "its" animation between sessions, like on the site. */
    fun forCard(cardId: Int, enabled: List<String>): String =
        if (enabled.isEmpty()) FADE else enabled[Math.floorMod(cardId, enabled.size)]

    fun durationSeconds(name: String, durations: Map<String, Double>): Double =
        if (name == FADE) 0.7 else durations[name] ?: DEFAULT_SECONDS[name] ?: 0.7
}

/**
 * The image emerging behind the study prompt: the tiny blurred thumb paints
 * first with the text scrim, then the full image plays its reveal animation
 * once decoded — mirroring the site's mobile presentation. [onVisible] fires
 * when there is something on screen, so the prompt text can switch to the
 * over-image (white) styling.
 */
@Composable
fun PromptVisual(
    thumbUrl: String,
    fullUrl: String,
    animation: String,
    durationSeconds: Double,
    onVisible: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var thumb by remember(thumbUrl) { mutableStateOf<ImageBitmap?>(null) }
    var bright by remember(thumbUrl) { mutableStateOf(false) }
    var full by remember(fullUrl) { mutableStateOf<ImageBitmap?>(null) }
    // .prompt-visual-thumb / -scrim: opacity transition 1.1s ease
    val ambience = remember(thumbUrl) { Animatable(0f) }
    val reveal = remember(fullUrl) { Animatable(0f) }

    LaunchedEffect(thumbUrl) {
        val result = context.imageLoader.execute(
            ImageRequest.Builder(context)
                .data(thumbUrl)
                .allowHardware(false) // luminance needs pixel access
                .size(Size.ORIGINAL)
                .build(),
        )
        val bitmap = (result as? SuccessResult)?.drawable?.toBitmap() ?: return@LaunchedEffect
        // White prompt text over a bright image needs a darker veil.
        bright = averageLuminance(bitmap) > 0.58f
        // A tiny copy upscaled with bilinear filtering doubles as the blur-up
        // on devices below API 31 (where RenderEffect blur is unavailable).
        thumb = Bitmap.createScaledBitmap(bitmap, 16, 16, true).asImageBitmap()
        onVisible(true)
        ambience.animateTo(1f, tween(1100))
    }
    LaunchedEffect(fullUrl) {
        val result = context.imageLoader.execute(
            ImageRequest.Builder(context).data(fullUrl).size(Size.ORIGINAL).build(),
        )
        val drawable = (result as? SuccessResult)?.drawable ?: return@LaunchedEffect
        val bitmap = (drawable as? BitmapDrawable)?.bitmap ?: drawable.toBitmap()
        full = bitmap.asImageBitmap()
        onVisible(true)
        // The scrim must appear even when the thumb request failed.
        if (ambience.targetValue != 1f) launch { ambience.animateTo(1f, tween(1100)) }
        // The reveal starts only after the image is decoded, so it never
        // stutters — same as the site's probe.decode() gate.
        reveal.animateTo(
            1f,
            tween(
                durationMillis = (durationSeconds * 1000).roundToInt().coerceAtLeast(1),
                easing = easingFor(animation),
            ),
        )
    }

    Box(modifier) {
        thumb?.let { bitmap ->
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        alpha = ambience.value
                        scaleX = 1.15f
                        scaleY = 1.15f
                        blurLayer(30f)
                    },
            )
        }
        full?.let { bitmap -> RevealImage(bitmap, animation) { reveal.value } }
        // .prompt-visual-scrim keeps the prompt text readable.
        val scrim = if (bright) {
            Brush.verticalGradient(
                0f to Color(0xC2090B0F), 0.46f to Color(0x99090B0F), 1f to Color(0xCC090B0F),
            )
        } else {
            Brush.verticalGradient(
                0f to Color(0xA8090B0F), 0.46f to Color(0x61090B0F), 1f to Color(0xB3090B0F),
            )
        }
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { alpha = ambience.value }
                .background(scrim),
        )
    }
}

/** The full image with its reveal animation driven by the eased [progress]. */
@Composable
private fun BoxScope.RevealImage(
    bitmap: ImageBitmap,
    animation: String,
    progress: () -> Float,
) {
    val base = Modifier.matchParentSize()
    val animated = when (animation) {
        // @keyframes prompt-mist
        "mist" -> base.graphicsLayer {
            val p = progress()
            alpha = (p / 0.55f).coerceAtMost(1f)
            val scale = 1.14f - 0.14f * p
            scaleX = scale
            scaleY = scale
            blurLayer(26f * (1f - p))
        }
        // @keyframes prompt-drift
        "drift" -> base.graphicsLayer {
            val p = progress()
            alpha = (p / 0.17f).coerceAtMost(1f)
            val scale = 1.26f - 0.22f * p
            scaleX = scale
            scaleY = scale
            translationX = 0.022f * (1f - p) * size.width
            translationY = -0.016f * (1f - p) * size.height
            blurLayer(11f * (1f - p))
        }
        // @keyframes prompt-ripple: a feathered circle spreads from (50%, 44%)
        "ripple" -> base
            .graphicsLayer {
                alpha = (progress() / 0.07f).coerceAtMost(1f)
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawWithContent {
                drawContent()
                rippleMask(progress())
            }
        // @keyframes prompt-droplets-2: six feathered drops soak in and merge
        "droplets" -> base
            .graphicsLayer {
                val p = progress()
                alpha = (p / 0.06f).coerceAtMost(1f)
                compositingStrategy = CompositingStrategy.Offscreen
                blurLayer(stepValue(p, DROPLET_BLUR_FRAMES, DROPLET_BLUR_VALUES))
            }
            .drawWithContent {
                drawContent()
                dropletsMask(progress())
            }
        else -> base.graphicsLayer { alpha = progress() }
    }
    Image(bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = animated)
}

/** CSS `filter: blur(Npx)` — RenderEffect on API 31+, the blur-up thumb below. */
private fun GraphicsLayerScope.blurLayer(radiusDp: Float) {
    renderEffect = if (radiusDp > 0.05f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        BlurEffect(radiusDp.dp.toPx(), radiusDp.dp.toPx(), TileMode.Clamp)
    } else {
        null
    }
}

private fun DrawScope.rippleMask(p: Float) {
    if (p >= 1f) return
    // mask-size grows 0% -> 680%; circle closest-side with a soft rim to 98%.
    val tileW = 6.8f * p * size.width
    val tileH = 6.8f * p * size.height
    val radius = min(tileW, tileH) / 2f
    if (radius <= 0f) {
        drawRect(Color.Black, blendMode = BlendMode.Clear)
        return
    }
    val center = Offset(
        (size.width - tileW) * 0.5f + tileW / 2f,
        (size.height - tileH) * 0.44f + tileH / 2f,
    )
    drawRect(
        brush = featheredCircle(center, radius, feather = 0.58f),
        blendMode = BlendMode.DstIn,
    )
}

private class Drop(val x: Float, val y: Float, val feather: Float, val sizes: FloatArray)

// mask-position / mask-size keyframes of @keyframes prompt-droplets-2.
private val DROPLET_FRAMES = floatArrayOf(0f, 0.16f, 0.30f, 0.46f, 0.62f, 0.78f, 1f)
private val DROPS = listOf(
    Drop(0.22f, 0.26f, 0.55f, floatArrayOf(0f, 24f, 52f, 86f, 128f, 196f, 560f)),
    Drop(0.74f, 0.14f, 0.52f, floatArrayOf(0f, 9f, 31f, 60f, 98f, 158f, 530f)),
    Drop(0.45f, 0.71f, 0.58f, floatArrayOf(0f, 2f, 16f, 41f, 74f, 128f, 510f)),
    Drop(0.87f, 0.68f, 0.50f, floatArrayOf(0f, 0f, 6f, 24f, 52f, 98f, 490f)),
    Drop(0.12f, 0.79f, 0.56f, floatArrayOf(0f, 0f, 1f, 12f, 34f, 72f, 470f)),
    Drop(0.60f, 0.42f, 0.53f, floatArrayOf(0f, 0f, 0f, 4f, 18f, 50f, 450f)),
)
private val DROPLET_BLUR_FRAMES = floatArrayOf(0f, 0.46f, 0.78f, 1f)
private val DROPLET_BLUR_VALUES = floatArrayOf(15f, 7f, 2f, 0f)

private fun DrawScope.dropletsMask(p: Float) {
    if (p >= 1f) return
    // The union of the six feathered drops is built in its own layer, then
    // applied to the image with DstIn — overlapping soft rims merge instead
    // of cutting into each other.
    val canvas = drawContext.canvas
    canvas.saveLayer(
        Rect(0f, 0f, size.width, size.height),
        Paint().apply { blendMode = BlendMode.DstIn },
    )
    DROPS.forEach { drop ->
        val sizePct = stepValue(p, DROPLET_FRAMES, drop.sizes) / 100f
        if (sizePct > 0f) {
            val tileW = sizePct * size.width
            val tileH = sizePct * size.height
            val radius = min(tileW, tileH) / 2f
            if (radius > 0f) {
                val center = Offset(
                    (size.width - tileW) * drop.x + tileW / 2f,
                    (size.height - tileH) * drop.y + tileH / 2f,
                )
                drawRect(brush = featheredCircle(center, radius, drop.feather))
            }
        }
    }
    canvas.restore()
}

private fun featheredCircle(center: Offset, radius: Float, feather: Float): Brush =
    Brush.radialGradient(
        0f to Color.Black,
        feather to Color.Black,
        0.98f to Color.Transparent,
        1f to Color.Transparent,
        center = center,
        radius = radius,
    )

/** Piecewise-linear keyframe interpolation over the eased progress. */
private fun stepValue(p: Float, frames: FloatArray, values: FloatArray): Float {
    if (p <= frames.first()) return values.first()
    for (i in 1 until frames.size) {
        if (p <= frames[i]) {
            val t = (p - frames[i - 1]) / (frames[i] - frames[i - 1])
            return values[i - 1] + (values[i] - values[i - 1]) * t
        }
    }
    return values.last()
}

private fun easingFor(animation: String): Easing = when (animation) {
    "mist" -> CubicBezierEasing(0.22f, 0.82f, 0.26f, 1f)
    "ripple" -> CubicBezierEasing(0.32f, 0.62f, 0.28f, 1f)
    "drift" -> CubicBezierEasing(0.2f, 0.6f, 0.25f, 1f)
    "droplets" -> CubicBezierEasing(0.45f, 0.08f, 0.35f, 0.95f)
    else -> CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f) // CSS `ease`
}

private fun averageLuminance(bitmap: Bitmap): Float {
    val sample = Bitmap.createScaledBitmap(bitmap, 8, 8, true)
    var sum = 0f
    for (y in 0 until 8) {
        for (x in 0 until 8) {
            val pixel = sample.getPixel(x, y)
            sum += (
                0.2126f * android.graphics.Color.red(pixel) +
                    0.7152f * android.graphics.Color.green(pixel) +
                    0.0722f * android.graphics.Color.blue(pixel)
                ) / 255f
        }
    }
    return sum / 64f
}
