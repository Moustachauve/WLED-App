package ca.cgagnier.wlednativeandroid.ui.components

import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics

// TODO: Migrate to Material 3 1.5.0+'s selectable DropdownMenuItem(selected = ...) and
//  MenuDefaults.selectableItemColors() once Compose Material 3 is upgraded from 1.4.0.
@Composable
fun SelectableDropdownMenuItem(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: @Composable () -> Unit,
) {
    val colors = if (selected) {
        MenuDefaults.itemColors(
            textColor = MaterialTheme.colorScheme.onSecondaryContainer,
            trailingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    } else {
        MenuDefaults.itemColors()
    }
    DropdownMenuItem(
        text = text,
        onClick = onClick,
        modifier = modifier
            .then(if (selected) Modifier.background(MaterialTheme.colorScheme.secondaryContainer) else Modifier)
            .semantics { this.selected = selected },
        trailingIcon = if (selected) {
            { Icon(imageVector = Icons.Filled.Check, contentDescription = null) }
        } else {
            null
        },
        colors = colors,
        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
    )
}
