package ca.cgagnier.wlednativeandroid.ui.homeScreen.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.model.Version
import ca.cgagnier.wlednativeandroid.model.VersionWithAssets
import ca.cgagnier.wlednativeandroid.ui.components.WledMarkdown
import ca.cgagnier.wlednativeandroid.ui.theme.WLEDNativeTheme
import ca.cgagnier.wlednativeandroid.util.stripVersionPrefix

@Composable
fun UpdateDisclaimerDialog(
    version: VersionWithAssets,
    installEnabled: Boolean = true,
    onDismiss: () -> Unit = {},
    onAccept: () -> Unit = {},
) {
    val displayTag = "v${version.version.tagName.stripVersionPrefix()}"
    AlertDialog(
        title = {
            Text(stringResource(R.string.beta_update_disclaimer_title, displayTag))
        },
        text = {
            UpdateDisclaimerDialogContent(version = version)
        },
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = onAccept,
                enabled = installEnabled,
            ) {
                Text(
                    stringResource(R.string.install_update),
                    color = if (installEnabled) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    },
                    fontWeight = FontWeight.Bold,
                )
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
                imageVector = Icons.Filled.Warning,
                contentDescription = stringResource(R.string.are_you_sure),
                tint = MaterialTheme.colorScheme.error,
            )
        },
    )
}

@Composable
private fun UpdateDisclaimerDialogContent(version: VersionWithAssets) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
        ) {
            Text(
                text = stringResource(R.string.beta_update_disclaimer_warning),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(12.dp),
            )
        }
        val description = version.version.description.trimIndent()
        if (description.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            WledMarkdown(
                content = description,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun UpdateDisclaimerDialogPreview() {
    WLEDNativeTheme(darkTheme = true) {
        UpdateDisclaimerDialog(
            version = VersionWithAssets(
                version = Version.getPreviewVersion(),
                assets = emptyList(),
            ),
        )
    }
}
