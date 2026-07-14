package ru.lexiloop.app.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import ru.lexiloop.app.data.api.FlashcardDto
import ru.lexiloop.app.data.repo.CardImages
import ru.lexiloop.app.ui.theme.LocalPalette

/** The site's CardImageControls: preview, link fetch, file upload, remove. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CardImageControls(
    card: FlashcardDto,
    busy: Boolean,
    onLink: (String) -> Unit,
    onUpload: (Uri) -> Unit,
    onRemove: () -> Unit,
) {
    val p = LocalPalette.current
    var link by remember(card.id) { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let(onUpload) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (card.hasImage) {
            AsyncImage(
                model = CardImages.imageUrl(card),
                contentDescription = "Illustration for ${card.term}",
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, p.border, RoundedCornerShape(12.dp)),
                contentScale = ContentScale.FillWidth,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                LexiTextField(
                    value = link,
                    onValueChange = { link = it },
                    placeholder = "Image link, or an image page",
                )
            }
            LexiButton(
                if (busy) "Working…" else "Fetch",
                kind = ButtonKind.Secondary,
                enabled = !busy && link.isNotBlank(),
                onClick = {
                    onLink(link)
                    link = ""
                },
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            LexiButton(
                if (card.hasImage) "Replace file" else "Upload file",
                kind = ButtonKind.Secondary,
                leadingIcon = Icons.Filled.Image,
                enabled = !busy,
                onClick = {
                    picker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
            )
            if (card.hasImage) {
                LexiButton(
                    "Remove",
                    kind = ButtonKind.DangerText,
                    leadingIcon = Icons.Filled.Delete,
                    enabled = !busy,
                    onClick = onRemove,
                )
            }
        }
        Text(
            "Any page link works — even a copied Google or Yandex image-search page: the image assistant finds the best matching picture when the link isn't a file.",
            fontSize = 11.sp,
            lineHeight = 15.sp,
            color = p.muted2,
        )
    }
}
