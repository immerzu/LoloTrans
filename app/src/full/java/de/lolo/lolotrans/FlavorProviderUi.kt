package de.lolo.lolotrans

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

@Composable
internal fun FlavorProviderButtons(
    translationProvider: TranslationProvider,
    onProviderSelected: (TranslationProvider) -> Unit
) {
    ProviderButton(
        text = stringResource(R.string.provider_ml_kit),
        selected = translationProvider == TranslationProvider.ML_KIT,
        modifier = Modifier.fillMaxWidth(),
        onClick = { onProviderSelected(TranslationProvider.ML_KIT) }
    )
}

@Composable
internal fun FlavorProviderSettings(
    settingsRepository: SettingsRepository,
    translationProvider: TranslationProvider
) {
}
