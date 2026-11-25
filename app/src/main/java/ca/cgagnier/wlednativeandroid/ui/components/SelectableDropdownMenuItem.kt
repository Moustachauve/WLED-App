package ca.cgagnier.wlednativeandroid.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MenuItemShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import ca.cgagnier.wlednativeandroid.R
import androidx.compose.material3.SelectableDropdownMenuItem as MaterialSelectableDropdownMenuItem

@Composable
fun SelectableDropdownMenuItem(
    selected: Boolean,
    onClick: () -> Unit,
    shapes: MenuItemShapes = MenuDefaults.itemShapes(),
    supportingText: (@Composable () -> Unit)? = null,
    text: @Composable () -> Unit,
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(selected) {
        if (selected) bringIntoViewRequester.bringIntoView()
    }
    MaterialSelectableDropdownMenuItem(
        selected = selected,
        onClick = onClick,
        text = text,
        shapes = shapes,
        modifier = Modifier.bringIntoViewRequester(bringIntoViewRequester),
        supportingText = supportingText,
        selectedLeadingIcon = {
            Icon(
                painter = painterResource(R.drawable.baseline_check_24),
                contentDescription = null,
                modifier = Modifier.size(MenuDefaults.LeadingIconSize),
            )
        },
        colors = MenuDefaults.selectableItemColors(),
    )
}
