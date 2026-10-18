package com.example.asmrplayer.ui.card

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.asmrplayer.R
import com.example.asmrplayer.core.card.CardLayerGeometry
import com.example.asmrplayer.core.card.CardStylePreset
import com.example.asmrplayer.core.card.CardTextFields

/**
 * The editable copy and material of a card, before it is built.
 *
 * Seven fields rather than a title alone, because the card face has six slots of its own
 * and a card whose every line came from a filename would look like a template. They are
 * pre-filled from the song, so building one takes no typing at all -- and are editable,
 * so it can take some.
 */
@Composable
fun CardComposeForm(
    fields: CardTextFields,
    onFieldsChange: (CardTextFields) -> Unit,
    preset: CardStylePreset,
    onPresetChange: (CardStylePreset) -> Unit,
    coverUri: String?,
    onCoverChange: (String) -> Unit,
    generating: Boolean,
    failed: Boolean,
    onGenerate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The photo picker needs no permission at any API level, and the contract falls back
    // to an open-document intent by itself on a device that has no picker -- so there is
    // nothing here to branch on.
    val pickCover = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { picked -> picked?.let { onCoverChange(it.toString()) } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        CoverPicker(
            coverUri = coverUri,
            onPick = {
                pickCover.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            onClear = { onCoverChange("") },
        )

        StyleRow(preset = preset, onPresetChange = onPresetChange)

        Field(
            label = stringResource(R.string.card_field_title),
            value = fields.title,
            onValueChange = { onFieldsChange(fields.copy(title = it)) },
        )
        Field(
            label = stringResource(R.string.card_field_subtitle),
            value = fields.subtitle,
            onValueChange = { onFieldsChange(fields.copy(subtitle = it)) },
        )
        Field(
            label = stringResource(R.string.card_field_collection),
            value = fields.collection,
            onValueChange = { onFieldsChange(fields.copy(collection = it)) },
        )
        Field(
            label = stringResource(R.string.card_field_technique),
            value = fields.technique,
            onValueChange = { onFieldsChange(fields.copy(technique = it)) },
        )
        Field(
            label = stringResource(R.string.card_field_tagline),
            value = fields.tagline,
            onValueChange = { onFieldsChange(fields.copy(tagline = it)) },
        )
        Field(
            label = stringResource(R.string.card_field_edition),
            value = fields.edition,
            onValueChange = { onFieldsChange(fields.copy(edition = it)) },
        )
        Field(
            label = stringResource(R.string.card_field_description),
            value = fields.description,
            onValueChange = { onFieldsChange(fields.copy(description = it)) },
        )

        if (failed) {
            Text(
                text = stringResource(R.string.card_generate_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Button(
            onClick = onGenerate,
            // A cleared cover is an empty string rather than null, and a card cannot be
            // struck from nothing: without this the button would be live and the build
            // would fail on the way in.
            enabled = !generating && !coverUri.isNullOrEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (generating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Text(
                    text = stringResource(R.string.card_generating),
                    modifier = Modifier.padding(start = 10.dp),
                )
            } else {
                Text(stringResource(R.string.card_generate))
            }
        }
    }
}

/** The cover the card is struck from, and the one thing about a card that is not copy. */
@Composable
private fun CoverPicker(
    coverUri: String?,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(84.dp)
                .aspectRatio(CardLayerGeometry.CanvasAspect)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (!coverUri.isNullOrEmpty()) {
                AsyncImage(
                    model = coverUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.card_cover),
                style = MaterialTheme.typography.titleSmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onPick) {
                    // "Change" only says what it does when there is something to change.
                    // The studio is reachable with no song behind it -- from the
                    // collection room in Settings -- and there the button is the only
                    // way to a cover, so it says what it is.
                    Text(
                        stringResource(
                            if (coverUri.isNullOrEmpty()) {
                                R.string.card_cover_choose
                            } else {
                                R.string.card_cover_change
                            },
                        ),
                    )
                }
                if (!coverUri.isNullOrEmpty()) {
                    Text(
                        text = stringResource(R.string.card_cover_clear),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clickable(onClick = onClear)
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                    )
                }
            }
            // No cover means Generate stays disabled, so the reason has to be on screen:
            // a greyed-out button with nothing said about it reads as a broken form.
            if (coverUri.isNullOrEmpty()) {
                Text(
                    text = stringResource(R.string.card_cover_missing),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/**
 * The four materials, as swatches.
 *
 * Each one writes both the finish and the camera geometry into the generated config, so
 * the choice is what the card opens wearing rather than a colour applied on top of it.
 */
@Composable
private fun StyleRow(preset: CardStylePreset, onPresetChange: (CardStylePreset) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.card_style),
            style = MaterialTheme.typography.titleSmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CardStylePreset.entries.forEach { entry ->
                val selected = entry == preset
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.clickable { onPresetChange(entry) },
                ) {
                    Box(
                        modifier = Modifier
                            .width(44.dp)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(22.dp))
                            .background(swatchColor(entry))
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                                shape = RoundedCornerShape(22.dp),
                            ),
                    )
                    Text(
                        text = stringResource(entry.labelRes),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

private val CardStylePreset.labelRes: Int
    get() = when (this) {
        CardStylePreset.Heirloom -> R.string.card_style_heirloom
        CardStylePreset.Pearl -> R.string.card_style_pearl
        CardStylePreset.Silver -> R.string.card_style_silver
        CardStylePreset.Original -> R.string.card_style_original
    }

/** A stand-in tone per material: the real thing is a foil sweep the viewer renders. */
private fun swatchColor(preset: CardStylePreset): Color = when (preset) {
    CardStylePreset.Heirloom -> Color(0xFFC9A24A)
    CardStylePreset.Pearl -> Color(0xFFE8E2DA)
    CardStylePreset.Silver -> Color(0xFFB9C0C7)
    CardStylePreset.Original -> Color(0xFF6E6A63)
}
