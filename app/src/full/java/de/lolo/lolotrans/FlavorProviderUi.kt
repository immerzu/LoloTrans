package de.lolo.lolotrans

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
        text = stringResource(R.string.provider_ml_kit),
        selected = translationProvider == TranslationProvider.ML_KIT,
        modifier = Modifier.fillMaxWidth(),
        onClick = { onProviderSelected(TranslationProvider.ML_KIT) }
    )
    ProviderButton(
        text = stringResource(R.string.provider_telegram_bridge),
        selected = translationProvider == TranslationProvider.TELEGRAM,
        modifier = Modifier.fillMaxWidth(),
        onClick = { onProviderSelected(TranslationProvider.TELEGRAM) }
    )
}

@Composable
internal fun FlavorProviderSettings(
    settingsRepository: SettingsRepository,
    translationProvider: TranslationProvider
) {
    if (translationProvider != TranslationProvider.TELEGRAM) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Spacer(modifier = Modifier.height(1.dp))
    HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
    Spacer(modifier = Modifier.height(1.dp))

    DarkCard {
        SectionTitle(stringResource(R.string.section_telegram_login))
        remember { TelegramTdlibClient.start(); true }
        val authStatus by TelegramTdlibClient.authStatus.collectAsStateWithLifecycle(
            initialValue = TelegramAuthStatus.Starting
        )
        var phoneText by remember { mutableStateOf("") }
        var codeText by remember { mutableStateOf("") }
        var passwordText by remember { mutableStateOf("") }
        var loginResult by remember { mutableStateOf("") }

        Text(
            text = when (authStatus) {
                TelegramAuthStatus.Starting -> stringResource(R.string.telegram_status_starting)
                TelegramAuthStatus.WaitPhoneNumber -> stringResource(R.string.telegram_status_wait_phone)
                TelegramAuthStatus.WaitCode -> stringResource(R.string.telegram_status_wait_code)
                TelegramAuthStatus.WaitPassword -> stringResource(R.string.telegram_status_wait_password)
                TelegramAuthStatus.Ready -> stringResource(R.string.telegram_status_ready)
                is TelegramAuthStatus.Error -> (authStatus as TelegramAuthStatus.Error).message
            },
            color = TextGray,
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = phoneText,
            onValueChange = { phoneText = it },
            label = { Text(stringResource(R.string.label_telegram_phone), color = TextDim) },
            placeholder = { Text("+49123456789", color = TextDim) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
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
        DarkButton(stringResource(R.string.btn_telegram_send_code), onClick = {
            scope.launch {
                loginResult = context.getString(R.string.telegram_status_sending)
                val result = TelegramTdlibClient.submitPhoneNumber(phoneText)
                loginResult = result.exceptionOrNull()?.message
                    ?: context.getString(R.string.telegram_code_sent)
            }
        }, modifier = Modifier.fillMaxWidth())

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = codeText,
            onValueChange = { codeText = it },
            label = { Text(stringResource(R.string.label_telegram_code), color = TextDim) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
        DarkButton(stringResource(R.string.btn_telegram_confirm_code), onClick = {
            scope.launch {
                val result = TelegramTdlibClient.submitCode(codeText)
                loginResult = result.exceptionOrNull()?.message
                    ?: context.getString(R.string.telegram_code_confirmed)
            }
        }, modifier = Modifier.fillMaxWidth())

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = passwordText,
            onValueChange = { passwordText = it },
            label = { Text(stringResource(R.string.label_telegram_password), color = TextDim) },
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
        DarkButton(stringResource(R.string.btn_telegram_confirm_password), onClick = {
            scope.launch {
                val result = TelegramTdlibClient.submitPassword(passwordText)
                loginResult = result.exceptionOrNull()?.message
                    ?: context.getString(R.string.telegram_password_confirmed)
            }
        }, modifier = Modifier.fillMaxWidth())

        if (loginResult.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(loginResult, color = TextGray, fontSize = 13.sp)
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            stringResource(R.string.telegram_login_data_notice),
            color = TextDim,
            fontSize = 12.sp
        )
    }
}
