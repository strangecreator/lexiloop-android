package ru.lexiloop.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ru.lexiloop.app.ui.theme.LocalPalette

/** The web app's modal: surface panel, border, title + subtitle + close. */
@Composable
fun LexiModal(
    title: String,
    subtitle: String? = null,
    onClose: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val p = LocalPalette.current
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(p.surface, RoundedCornerShape(16.dp))
                .border(1.dp, p.border2, RoundedCornerShape(16.dp)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 17.dp, vertical = 17.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontSize = 18.sp)
                    if (subtitle != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(subtitle, fontSize = 11.sp, color = p.muted, lineHeight = 16.sp)
                    }
                }
                LexiIconButton(Icons.Filled.Close, contentDescription = "Close", onClick = onClose)
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(p.border),
            )
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(17.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                content = content,
            )
        }
    }
}

@Composable
fun ModalActions(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** A `<select>`-styled dropdown field. */
@Composable
fun LexiSelect(
    value: String,
    options: List<Pair<String, String>>, // value to label
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Choose…",
) {
    val p = LocalPalette.current
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    val selectedLabel = options.firstOrNull { it.first == value }?.second
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(p.surface2, shape)
                .border(1.dp, if (open) p.primary else p.border, shape)
                .clickable { open = true }
                .defaultMinSize(minHeight = 42.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                selectedLabel ?: placeholder,
                modifier = Modifier.weight(1f),
                color = if (selectedLabel != null) p.text else p.muted2,
                fontSize = 14.sp,
                fontWeight = if (selectedLabel != null) FontWeight.W600 else FontWeight.W400,
                maxLines = 1,
            )
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = p.muted,
                modifier = Modifier.size(18.dp),
            )
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.background(p.surface2),
        ) {
            options.forEach { (optionValue, label) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            label,
                            fontSize = 13.sp,
                            color = if (optionValue == value) p.primary2 else p.text,
                        )
                    },
                    onClick = {
                        open = false
                        onSelect(optionValue)
                    },
                )
            }
        }
    }
}

/**
 * The web NumberField: free text while typing, clamps and snaps back on blur —
 * here simplified to clamp on every parsable change.
 */
@Composable
fun NumberInput(
    value: Double,
    onValue: (Double) -> Unit,
    modifier: Modifier = Modifier,
    min: Double? = null,
    max: Double? = null,
    round: Boolean = false,
) {
    var draft by remember(value) { mutableStateOf(formatNumber(value)) }
    LexiTextField(
        value = draft,
        onValueChange = { raw ->
            draft = raw
            val parsed = raw.trim().toDoubleOrNull() ?: return@LexiTextField
            var next = if (round) kotlin.math.round(parsed) else parsed
            if (min != null) next = maxOf(min, next)
            if (max != null) next = minOf(max, next)
            onValue(next)
        },
        modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}

fun formatNumber(value: Double): String =
    if (value == kotlin.math.floor(value) && !value.isInfinite()) {
        value.toLong().toString()
    } else {
        value.toString()
    }
