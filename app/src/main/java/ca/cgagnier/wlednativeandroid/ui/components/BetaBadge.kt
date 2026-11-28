package ca.cgagnier.wlednativeandroid.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.cgagnier.wlednativeandroid.R

private val CompactBadgeFontSize = 9.sp
private val CompactBadgeLineHeight = 11.sp

fun isBetaVersion(versionName: String): Boolean = versionName.contains("-beta", ignoreCase = true)

@Composable
fun BetaBadge(modifier: Modifier = Modifier, compact: Boolean = false) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        border = if (compact) BorderStroke(1.dp, MaterialTheme.colorScheme.surface) else null,
    ) {
        Text(
            text = stringResource(R.string.beta_badge),
            style = if (compact) {
                MaterialTheme.typography.labelSmall.copy(
                    fontSize = CompactBadgeFontSize,
                    lineHeight = CompactBadgeLineHeight,
                    fontWeight = FontWeight.Bold,
                )
            } else {
                MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
            },
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(
                horizontal = if (compact) 5.dp else 8.dp,
                vertical = if (compact) 1.dp else 2.dp,
            ),
        )
    }
}
