package tk.zwander.commonCompose.view.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import dev.zwander.compose.alertdialog.InWindowAlertDialog
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import tk.zwander.common.data.imei.IMEIGenerator
import tk.zwander.common.generated.resources.Res
import tk.zwander.common.generated.resources.*
import tk.zwander.common.util.BifrostSettings
import tk.zwander.commonCompose.model.BaseModel
import tk.zwander.commonCompose.model.DownloadModel
import tk.zwander.commonCompose.util.OffsetCorrectedIdentityTransformation
import tk.zwander.commonCompose.util.collectAsImmediateMutableState

/**
 * A common container for the model, region, and firmware text inputs used in
 * [tk.zwander.commonCompose.view.pages.DownloadView] and [tk.zwander.commonCompose.view.pages.DecryptView]
 * @param model the view model.
 * @param canChangeOption whether the model and region fields should be editable.
 * @param canChangeFirmware whether the firmware field should be editable.
 * @param showFirmware whether to show the firmware field.
 */
@Composable
internal fun MRFLayout(
    model: BaseModel,
    canChangeOption: Boolean,
    canChangeFirmware: Boolean,
    showFirmware: Boolean = true,
    showImeiSerial: Boolean = true,
) {
    var modelState by model.model.collectAsImmediateMutableState()
    var regionState by model.region.collectAsImmediateMutableState()
    var firmwareState by model.fw.collectAsImmediateMutableState()
    var imeiState by model.imeiSerial.collectAsImmediateMutableState()

    var showingCscChooser by remember {
        mutableStateOf(false)
    }
    var showingImeiHelp by remember {
        mutableStateOf(false)
    }
    var showingImeiEditor by remember {
        mutableStateOf(false)
    }

    val allowLowercase by BifrostSettings.Keys.allowLowercaseCharacters.asMutableStateFlow().collectAsImmediateMutableState()
    val hasRunningJobs by model.hasRunningJobs.collectAsState(false)

    IMEIGenerator.CollectImeisForModel(
        model = modelState,
        initialValue = imeiState,
        onValueChange = {
            imeiState = it.take(100)
                .joinToString("\n")
        },
    )

    SplitComponent(
        startComponent = { modifier ->
            OutlinedTextField(
                value = modelState,
                onValueChange = {
                    modelState = it.transformText(allowLowercase)
                    if (model is DownloadModel && !model.manual.value) {
                        model.fw.value = ""
                        model.osCode.value = ""
                    }
                },
                modifier = modifier,
                label = { Text(text = stringResource(Res.string.modelHint)) },
                readOnly = !canChangeOption,
                keyboardOptions = KeyboardOptions(KeyboardCapitalization.Characters),
                singleLine = true,
                visualTransformation = OffsetCorrectedIdentityTransformation(modelState),
            )
        },
        endComponent = { modifier ->
            OutlinedTextField(
                value = regionState,
                onValueChange = {
                    regionState = it.transformText(allowLowercase)
                    if (model is DownloadModel && !model.manual.value) {
                        model.fw.value = ""
                        model.osCode.value = ""
                    }
                },
                modifier = modifier,
                label = { Text(text = stringResource(Res.string.regionHint)) },
                readOnly = !canChangeOption,
                keyboardOptions = KeyboardOptions(KeyboardCapitalization.Characters),
                singleLine = true,
                trailingIcon = {
                    IconButton(
                        onClick = { showingCscChooser = true }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.List,
                            contentDescription = stringResource(Res.string.chooseCsc),
                        )
                    }
                },
                visualTransformation = OffsetCorrectedIdentityTransformation(regionState),
            )
        },
        startRatio = 0.6,
        endRatio = 0.4,
        modifier = Modifier.fillMaxWidth()
    )

    val firmwareImeiSerial by remember(canChangeFirmware, canChangeOption) {
        derivedStateOf {
            val items = mutableListOf<DynamicField>()

            if (showFirmware) {
                items.add(
                    DynamicField(
                        value = firmwareState,
                        onValueChange = { firmwareState = it },
                        labelRes = Res.string.firmware,
                        readOnly = !canChangeFirmware,
                        transform = {
                            it.transformText(allowLowercase)
                        },
                    ),
                )
            }

            if (showImeiSerial) {
                items.add(
                    DynamicField(
                        value = imeiState.replace("\n", ";"),
                        onValueChange = { imeiState = it.replace(";", "\n") },
                        labelRes = Res.string.imei_serial,
                        readOnly = !canChangeOption,
                        trailingIcon = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                IconButton(
                                    onClick = { showingImeiEditor = true }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = stringResource(Res.string.edit),
                                    )
                                }

                                IconButton(
                                    onClick = { showingImeiHelp = true }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = stringResource(Res.string.help),
                                    )
                                }
                            }
                        },
                    ),
                )
            }

            items
        }
    }

    if (firmwareImeiSerial.isNotEmpty()) {
        Spacer(Modifier.size(8.dp))

        if (firmwareImeiSerial.size == 1) {
            val first = firmwareImeiSerial.first()

            first.Render(modifier = Modifier.fillMaxWidth())
        } else {
            val first = firmwareImeiSerial.first()
            val second = firmwareImeiSerial[1]

            SplitComponent(
                startComponent = {
                    first.Render(it)
                },
                endComponent = {
                    second.Render(it)
                },
                startRatio = 0.6,
                endRatio = 0.4,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    CSCChooserDialog(
        showing = showingCscChooser,
        onDismissRequest = { showingCscChooser = false },
        onCscSelected = {
            if (!hasRunningJobs) {
                model.region.value = it
            }
        },
    )

    InWindowAlertDialog(
        showing = showingImeiHelp,
        onDismissRequest = { showingImeiHelp = false },
        title = { Text(text = stringResource(Res.string.imei_serial)) },
        text = { Text(text = stringResource(Res.string.imei_serial_help)) },
        buttons = {
            TextButton(onClick = { showingImeiHelp = false }) {
                Text(text = stringResource(Res.string.ok))
            }
        },
    )

    InWindowAlertDialog(
        showing = showingImeiEditor,
        onDismissRequest = { showingImeiEditor = false },
        title = { Text(text = stringResource(Res.string.edit)) },
        text = {
            TextField(
                value = imeiState,
                onValueChange = { imeiState = it },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        buttons = {
            TextButton(onClick = { showingImeiEditor = false }) {
                Text(text = stringResource(Res.string.close))
            }
        },
    )
}

private fun String.transformText(allowLowercase: Boolean): String {
    val trimmed = trim().replace(" ", "")

    return if (allowLowercase) trimmed else trimmed.uppercase()
}

private data class DynamicField(
    val value: String,
    val onValueChange: (String) -> Unit,
    val labelRes: StringResource,
    val readOnly: Boolean,
    val transform: (String) -> String = { it },
    val singleLine: Boolean = true,
    val maxLines: Int = Int.MAX_VALUE,
    val trailingIcon: (@Composable () -> Unit)? = null,
    val leadingIcon: (@Composable () -> Unit)? = null,
) {
    @Composable
    fun Render(modifier: Modifier = Modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(transform(it))
            },
            modifier = modifier,
            label = { Text(text = stringResource(labelRes)) },
            readOnly = readOnly,
            keyboardOptions = KeyboardOptions(KeyboardCapitalization.Characters),
            singleLine = singleLine,
            trailingIcon = trailingIcon,
            maxLines = maxLines,
            leadingIcon = leadingIcon,
            visualTransformation = OffsetCorrectedIdentityTransformation(value),
        )
    }
}
