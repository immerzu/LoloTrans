package de.lolo.lolotrans

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

@Composable
internal fun FlavorProviderButtons(
    translationProvider: TranslationProvider,
    onProviderSelected: (TranslationProvider) -> Unit
) {
    ProviderButton(
        text = stringResource(R.string.provider_free_translations),
        selected = translationProvider == TranslationProvider.FREETRANSLATIONS,
        modifier = Modifier.fillMaxWidth(),
        onClick = { onProviderSelected(TranslationProvider.FREETRANSLATIONS) }
    )
}

@Composable
internal fun FlavorProviderSettings(
    settingsRepository: SettingsRepository,
    translationProvider: TranslationProvider
) {
    if (translationProvider != TranslationProvider.FREETRANSLATIONS) return

    val scope = rememberCoroutineScope()
    val apiKey by settingsRepository.externalProviderApiKey.collectAsStateWithLifecycle(initialValue = "")

    Spacer(modifier = Modifier.height(1.dp))
    HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
    Spacer(modifier = Modifier.height(1.dp))

    DarkCard {
        SectionTitle(stringResource(R.string.section_free_translations))
        var apiKeyText by remember(apiKey) { mutableStateOf(apiKey) }

        OutlinedTextField(
            value = apiKeyText,
            onValueChange = { apiKeyText = it },
            label = { Text(stringResource(R.string.label_api_key), color = TextDim) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextWhite,
                unfocusedTextColor = TextWhite,
                focusedBorderColor = CardBorder,
                unfocusedBorderColor = CardBorder,
                focusedContainerColor = BgColor,
                unfocusedContainerColor = BgColor,
                focusedLabelColor = TextGray,
                unfocusedLabelColor = TextDim,
                cursorColor = TextWhite
            )
        )
        Spacer(modifier = Modifier.height(4.dp))
        DarkButton(stringResource(R.string.btn_save_api_key), onClick = {
            scope.launch { settingsRepository.setExternalProviderApiKey(apiKeyText.trim()) }
        }, modifier = Modifier.fillMaxWidth())

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            stringResource(R.string.free_translations_data_notice),
            color = TextDim,
            fontSize = 12.sp
        )
    }
}
