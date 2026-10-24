package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.theme.BrandGradientEnd
import com.example.ui.theme.BrandGradientStart
import com.example.ui.theme.UiSpacing
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ClipboardPaste
import com.composables.icons.lucide.CloudDownload
import com.composables.icons.lucide.FolderOpen
import com.composables.icons.lucide.X

@Composable
fun LinkIntakeCard(
    value: String,
    onValueChange: (String) -> Unit,
    onPaste: () -> Unit,
    onSubmit: () -> Unit,
    errorMessage: String?,
    modifier: Modifier = Modifier
) {
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val fontScale = LocalDensity.current.fontScale

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(UiSpacing.large)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(colors = listOf(BrandGradientStart, BrandGradientEnd))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Lucide.CloudDownload,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(UiSpacing.medium))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Открыть публичную ссылку",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "cloud.mail.ru/public/...  •  disk.yandex.ru/d/...  •  mega.nz/...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(UiSpacing.medium))
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = {
                    Text(
                        text = "Ссылка Mail.ru или Яндекс.Диска",
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = {
                    onSubmit()
                    keyboardController?.hide()
                }),
                isError = errorMessage != null,
                supportingText = errorMessage?.let { message ->
                    { Text(text = message, color = MaterialTheme.colorScheme.error) }
                },
                trailingIcon = {
                    if (value.isNotEmpty()) {
                        IconButton(onClick = { onValueChange("") }) {
                            Icon(Lucide.X, contentDescription = "Очистить ссылку")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("link_input_field"),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                )
            )

            Spacer(modifier = Modifier.height(UiSpacing.small))
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                if (maxWidth < 280.dp || fontScale >= 1.3f) {
                    Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                        PasteLinkButton(onClick = onPaste, modifier = Modifier.fillMaxWidth())
                        OpenLinkButton(onClick = onSubmit, modifier = Modifier.fillMaxWidth())
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)
                    ) {
                        PasteLinkButton(onClick = onPaste, modifier = Modifier.weight(1f))
                        OpenLinkButton(onClick = onSubmit, modifier = Modifier.weight(1.3f))
                    }
                }
            }
        }
    }
}

@Composable
private fun PasteLinkButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, modifier = modifier.testTag("paste_button")) {
        Icon(Lucide.ClipboardPaste, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(UiSpacing.iconGap))
        Text("Вставить", style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun OpenLinkButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.testTag("open_folder_button"),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Icon(Lucide.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(UiSpacing.iconGap))
        Text("Открыть", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}
