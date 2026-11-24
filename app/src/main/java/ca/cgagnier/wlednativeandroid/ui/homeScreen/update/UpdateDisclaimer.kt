package ca.cgagnier.wlednativeandroid.ui.homeScreen.update

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.ui.components.blurredDialogProperties
import ca.cgagnier.wlednativeandroid.ui.theme.WLEDNativeTheme

@Composable
fun UpdateDisclaimerDialog(
    onDismiss: () -> Unit = {},
    onAccept: () -> Unit = {},
    switchingToSourceName: String? = null,
) {
    AlertDialog(
        properties = blurredDialogProperties(),
        title = {
            Text(stringResource(R.string.are_you_sure))
        },
        text = {
            UpdateDisclaimerDialogContent(switchingToSourceName = switchingToSourceName)
        },
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = onAccept,
            ) {
                Text(stringResource(R.string.install_update))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
            ) {
                Text(stringResource(R.string.cancel))
            }
        },
        icon = {
            Icon(
                painter = painterResource(R.drawable.baseline_warning_24),
                contentDescription = stringResource(R.string.are_you_sure),
            )
        },
    )
}

@Composable
private fun UpdateDisclaimerDialogContent(switchingToSourceName: String? = null) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
    ) {
        if (!switchingToSourceName.isNullOrBlank()) {
            Text(
                text = stringResource(R.string.switching_source_disclaimer, switchingToSourceName),
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        Text(
            stringResource(R.string.device_update_disclaimer),
        )
    }
}

@Preview(showBackground = true)
@Composable
fun UpdateDisclaimerDialogPreview() {
    WLEDNativeTheme(darkTheme = isSystemInDarkTheme()) {
        UpdateDisclaimerDialog()
    }
}
