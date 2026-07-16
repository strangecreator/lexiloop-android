package ru.lexiloop.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import ru.lexiloop.app.ui.theme.LexiPalette
import ru.lexiloop.app.ui.theme.LocalPalette
import ru.lexiloop.app.ui.theme.Manrope

enum class ButtonKind { Primary, Secondary, Ghost, DangerText, TextLink, Danger }

@Composable
fun LexiButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Primary,
    big: Boolean = false,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(11.dp)
    val background = when (kind) {
        ButtonKind.Primary -> p.primary
        ButtonKind.Secondary -> p.surface2
        ButtonKind.Danger -> p.redBg
        else -> Color.Transparent
    }
    val borderColor = when (kind) {
        ButtonKind.Secondary, ButtonKind.Ghost -> p.border
        ButtonKind.Danger -> p.red.copy(alpha = 0.45f)
        else -> Color.Transparent
    }
    val contentColor = when (kind) {
        ButtonKind.Primary -> Color.White
        ButtonKind.Secondary -> p.text
        ButtonKind.Ghost -> p.muted
        ButtonKind.DangerText, ButtonKind.Danger -> p.red
        ButtonKind.TextLink -> p.primary2
    }
    Row(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.55f)
            .clip(shape)
            .background(background, shape)
            .border(1.dp, borderColor, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .defaultMinSize(minHeight = if (big) 52.dp else 48.dp)
            .padding(
                horizontal = when {
                    kind == ButtonKind.TextLink -> 2.dp
                    big -> 20.dp
                    else -> 15.dp
                },
            ),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leadingIcon?.let { Icon(it, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp)) }
        Text(
            text,
            color = contentColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.W700,
            maxLines = 1,
        )
        trailingIcon?.let { Icon(it, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp)) }
    }
}

@Composable
fun LexiIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = null,
) {
    val p = LocalPalette.current
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(p.surface, RoundedCornerShape(12.dp))
            .border(1.dp, p.border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = tint ?: p.text, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun FieldLabel(text: String, sub: String? = null) {
    val p = LocalPalette.current
    Column {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.W600, color = p.muted)
        if (sub != null) {
            Text(sub, fontSize = 11.sp, color = p.muted2)
        }
    }
}

@Composable
fun LexiTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    minHeight: Int = 50,
    password: Boolean = false,
    monospace: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    enabled: Boolean = true,
) {
    val p = LocalPalette.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(10.dp)
    val textStyle = TextStyle(
        color = p.text,
        fontSize = 16.sp,
        fontFamily = if (monospace) androidx.compose.ui.text.font.FontFamily.Monospace else MaterialTheme.typography.bodyMedium.fontFamily,
    )
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        textStyle = textStyle,
        singleLine = singleLine,
        enabled = enabled,
        cursorBrush = SolidColor(p.primary),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        interactionSource = interaction,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        decorationBox = { inner ->
            Box(
                modifier = Modifier
                    .background(p.surface2, shape)
                    .border(1.dp, if (focused) p.primary else p.border, shape)
                    .defaultMinSize(minHeight = minHeight.dp)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart,
            ) {
                if (value.isEmpty()) {
                    Text(placeholder, color = p.muted2, fontSize = 16.sp, maxLines = if (singleLine) 1 else 3)
                }
                inner()
            }
        },
    )
}

@Composable
fun LexiTextArea(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    minHeight: Int = 100,
    onSubmit: (() -> Unit)? = null,
) {
    LexiTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = placeholder,
        singleLine = false,
        minHeight = minHeight,
        // Enter on the soft keyboard submits instead of inserting a newline,
        // matching the site where Enter checks the answer.
        keyboardOptions = if (onSubmit != null) {
            KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Send)
        } else {
            KeyboardOptions.Default
        },
        keyboardActions = if (onSubmit != null) {
            KeyboardActions(onSend = { onSubmit() })
        } else {
            KeyboardActions.Default
        },
    )
}

@Composable
fun Badge(text: String, icon: ImageVector? = null) {
    val p = LocalPalette.current
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(p.primaryBg, CircleShape)
            .border(1.dp, p.primary.copy(alpha = 0.35f), CircleShape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { Icon(it, contentDescription = null, tint = p.primary2, modifier = Modifier.size(14.dp)) }
        Text(
            text.uppercase(),
            color = p.primary2,
            fontSize = 12.sp,
            fontWeight = FontWeight.W700,
            letterSpacing = 0.7.sp,
        )
    }
}

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    padding: Int = 20,
    radius: Int = 18,
    background: Color? = null,
    borderColor: Color? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(radius.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(background ?: p.surface, shape)
            .border(1.dp, borderColor ?: p.border, shape)
            .padding(padding.dp),
        content = content,
    )
}

@Composable
fun Eyebrow(text: String) {
    val p = LocalPalette.current
    Text(
        text.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.W700,
        letterSpacing = 1.5.sp,
        color = p.muted,
    )
}

@Composable
fun SectionHeading(
    eyebrow: String,
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f)) {
            Eyebrow(eyebrow)
            Spacer(Modifier.height(4.dp))
            Text(title, style = MaterialTheme.typography.titleLarge)
        }
        trailing?.invoke()
    }
}

@Composable
fun StatCard(
    icon: ImageVector,
    value: String,
    label: String,
    hint: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .background(if (accent) p.primaryBg.compositeOverSurface(p) else p.surface, shape)
            .border(1.dp, if (accent) p.primary.copy(alpha = 0.28f) else p.border, shape)
            .padding(16.dp)
            .heightIn(min = 100.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            value,
            fontFamily = Manrope,
            fontSize = 21.sp,
            fontWeight = FontWeight.W800,
            color = p.text,
        )
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.W700, color = p.text)
        Text(hint, fontSize = 12.sp, color = p.muted)
    }
}

private fun Color.compositeOverSurface(p: LexiPalette): Color =
    this.copy(alpha = 1f).let { accent ->
        // linear-gradient(145deg, primary-bg, surface) approximation
        Color(
            red = accent.red * 0.13f + p.surface.red * 0.87f,
            green = accent.green * 0.13f + p.surface.green * 0.87f,
            blue = accent.blue * 0.13f + p.surface.blue * 0.87f,
        )
    }

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    text: String,
    modifier: Modifier = Modifier,
) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, p.border2, shape) // dashed in CSS; solid subtle here
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .background(p.surface2, RoundedCornerShape(15.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = p.primary2, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.W700, color = p.text, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            text,
            fontSize = 13.sp,
            color = p.muted,
            textAlign = TextAlign.Center,
            lineHeight = 19.sp,
        )
    }
}

@Composable
fun Spinner(size: Int = 34) {
    val p = LocalPalette.current
    val transition = rememberInfiniteTransition(label = "spinner")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(800, easing = androidx.compose.animation.core.LinearEasing)),
        label = "angle",
    )
    androidx.compose.foundation.Canvas(Modifier.size(size.dp).rotate(angle)) {
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
        drawArc(color = p.surface3, startAngle = 0f, sweepAngle = 360f, useCenter = false, style = stroke)
        drawArc(color = p.primary, startAngle = -90f, sweepAngle = 80f, useCenter = false, style = stroke)
    }
}

@Composable
fun LoaderView(text: String, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 300.dp)
            .wrapContentSize(Alignment.Center),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spinner()
        Text(text, fontSize = 13.sp, color = p.muted)
    }
}

@Composable
fun FormError(text: String, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Text(
        text,
        modifier = modifier
            .fillMaxWidth()
            .background(p.red.copy(alpha = 0.10f), RoundedCornerShape(9.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        color = p.red,
        fontSize = 13.sp,
    )
}

@Composable
fun PoolDot(color: Color, size: Int = 9) {
    Box(
        Modifier
            .size(size.dp)
            .background(color, RoundedCornerShape(3.dp)),
    )
}

@Composable
fun StatusPill(text: String, color: Color, background: Color) {
    Text(
        text.uppercase(),
        modifier = Modifier
            .background(background, RoundedCornerShape(7.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        color = color,
        fontSize = 10.sp,
        fontWeight = FontWeight.W700,
        letterSpacing = 0.8.sp,
    )
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ChipRow(items: List<String>, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items.forEach { item ->
            Text(
                item,
                modifier = Modifier
                    .background(p.surface3, RoundedCornerShape(7.dp))
                    .border(1.dp, p.border, RoundedCornerShape(7.dp))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                fontSize = 12.sp,
                color = p.text,
            )
        }
    }
}

@Composable
fun LexiCheckRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    sub: String? = null,
) {
    val p = LocalPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onCheckedChange(!checked) }
            .defaultMinSize(minHeight = 44.dp)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(24.dp)
                .background(if (checked) p.primary else p.surface2, RoundedCornerShape(6.dp))
                .border(1.dp, if (checked) p.primary else p.border2, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Column {
            Text(label, fontSize = 14.sp, color = p.text)
            if (sub != null) {
                Text(sub, fontSize = 11.sp, color = p.muted2)
            }
        }
    }
}

@Composable
fun LexiSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val p = LocalPalette.current
    Box(
        modifier = Modifier
            .width(52.dp)
            .height(30.dp)
            .clip(CircleShape)
            .background(if (checked) p.primary else p.surface3, CircleShape)
            .border(1.dp, if (checked) p.primary else p.border2, CircleShape)
            .clickable { onCheckedChange(!checked) }
            .padding(3.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .size(24.dp)
                .background(Color.White, CircleShape),
        )
    }
}

/** A progress track like `.study-progress i` / `.bulk-progress-track`. */
@Composable
fun ProgressTrack(progress: Float, modifier: Modifier = Modifier, height: Int = 6) {
    val p = LocalPalette.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(CircleShape)
            .background(p.surface3),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction = progress.coerceIn(0f, 1f))
                .background(
                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                        listOf(p.primary, p.primary2),
                    ),
                    CircleShape,
                ),
        )
    }
}
