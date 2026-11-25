package ca.cgagnier.wlednativeandroid.ui.homeScreen.deviceAdd

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.ui.components.blurredDialogProperties
import ca.cgagnier.wlednativeandroid.ui.components.deviceName
import org.koin.androidx.compose.koinViewModel

@Composable
fun DeviceAdd(
    modifier: Modifier = Modifier,
    onDismissRequest: () -> Unit,
    viewModel: DeviceAddViewModel = koinViewModel(),
) {
    val state = viewModel.state

    val dismissRequest = {
        onDismissRequest()
        viewModel.clear()
    }

    Dialog(
        onDismissRequest = dismissRequest,
        properties = blurredDialogProperties(),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = modifier
                    .padding(16.dp)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .fillMaxSize()
                    .height(IntrinsicSize.Max),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.add_a_device),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp),
                )

                when (state.step) {
                    is DeviceAddStep.Form -> step1AddressForm(
                        state = state,
                        step = state.step,
                        viewModel = viewModel,
                        onDismissRequest = dismissRequest,
                    )

                    is DeviceAddStep.Adding -> step2Loading(state)

                    is DeviceAddStep.Success -> step3Complete(
                        step = state.step,
                        onDismissRequest = dismissRequest,
                    )
                }
            }
        }
    }
}

@Composable
private fun step1AddressForm(
    state: DeviceAddState,
    step: DeviceAddStep.Form,
    viewModel: DeviceAddViewModel,
    onDismissRequest: () -> Unit,
) {
    val focusRequester = remember {
        FocusRequester()
    }
    val focusManager = LocalFocusManager.current
    val addressState = rememberTextFieldState(initialText = state.address)
    val currentAddress by rememberUpdatedState(state.address)
    val currentViewModel by rememberUpdatedState(viewModel)

    LaunchedEffect("initialFocus") {
        focusRequester.requestFocus()
    }
    LaunchedEffect(addressState) {
        snapshotFlow { addressState.text.toString() }
            .collect { address ->
                if (address != currentAddress) currentViewModel.setAddress(address)
            }
    }
    OutlinedTextField(
        state = addressState,
        label = { Text(stringResource(R.string.ip_address_or_url)) },
        isError = step.addressError != null,
        supportingText = {
            if (step.addressError != null) {
                Text(stringResource(step.addressError))
            }
        },
        lineLimits = TextFieldLineLimits.SingleLine,
        keyboardOptions = KeyboardOptions(
            imeAction = ImeAction.Next,
            keyboardType = KeyboardType.Uri,
        ),
        onKeyboardAction = {
            focusManager.moveFocus(FocusDirection.Down)
        },
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
    )

    AddressFormActions(
        onCancel = onDismissRequest,
        onAdd = {
            viewModel.setAddress(addressState.text.toString())
            viewModel.submitCreateDevice()
        },
    )
}

@Composable
private fun AddressFormActions(onCancel: () -> Unit, onAdd: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        TextButton(onClick = onCancel) {
            Text(stringResource(R.string.cancel))
        }
        Button(onClick = onAdd) {
            Icon(
                painter = painterResource(R.drawable.baseline_add_24),
                contentDescription = stringResource(R.string.add_a_device),
            )
            Text(stringResource(R.string.add))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun step2Loading(state: DeviceAddState) {
    LoadingIndicator(
        modifier = Modifier.size(48.dp),
    )
    Text(
        stringResource(R.string.add_adding_device, state.address),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(12.dp),
        maxLines = 1,
        overflow = TextOverflow.MiddleEllipsis,
    )
}

@Composable
private fun step3Complete(step: DeviceAddStep.Success, onDismissRequest: () -> Unit) {
    Icon(
        modifier = Modifier
            .height(48.dp)
            .width(48.dp),
        painter = painterResource(id = R.drawable.baseline_add_task_24),
        contentDescription = stringResource(R.string.success),
        tint = Color(0xFF00b300), // Green
    )
    Text(
        stringResource(R.string.success),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 12.dp),
    )
    Text(
        stringResource(R.string.add_device_added, deviceName(step.device)),
        maxLines = 1,
        overflow = TextOverflow.MiddleEllipsis,
    )

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        TextButton(
            onClick = {
                onDismissRequest()
            },
        ) {
            Text(stringResource(R.string.done))
        }
    }
}
