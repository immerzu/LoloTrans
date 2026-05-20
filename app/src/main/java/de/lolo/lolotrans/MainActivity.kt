package de.lolo.lolotrans

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.lolo.lolotrans.ui.theme.TranslatorAppTheme
import kotlinx.coroutines.launch

private const val TAG = "TranslatorApp"

// Farbkonstanten für nicht-Theme-Elemente
private val BgColor = Color(0xFF000000)
private val CardBg = Color(0xFF0A0A0A)
private val CardBorder = Color(0xFF333333)
private val TextWhite = Color(0xFFFFFFFF)
private val TextGray = Color(0xFFB8B8B8)
private val TextDim = Color(0xFF666666)
private val BtnBg = Color(0xFF111111)
private val BtnBgActive = Color(0xFF222222)
private val BtnBorder = Color(0xFF666666)

class MainActivity : ComponentActivity() {

    private lateinit var settingsRepository: SettingsRepository
    private var hasOverlayPermission by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsRepository = SettingsRepository(this)
        hasOverlayPermission = Settings.canDrawOverlays(this)
        Log.d(TAG, "onCreate: Overlay=$hasOverlayPermission")
        enableEdgeToEdge()

        setContent {
            TranslatorAppTheme {
                MainScreen(
                    settingsRepository = settingsRepository,
                    hasOverlayPermission = hasOverlayPermission,
                    onGrantOverlayPermission = { openOverlaySettings() },
                    onStartBubble = { startBubbleService() },
                    onStopBubble = { stopBubbleService() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val newOverlay = Settings.canDrawOverlays(this)
        Log.d(TAG, "onResume: Overlay=$hasOverlayPermission→$newOverlay")
        hasOverlayPermission = newOverlay
    }

    private fun startBubbleService() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.permission_required, Toast.LENGTH_LONG).show()
            return
        }
        val intent = Intent(this, FloatingBubbleService::class.java)
        startService(intent)
    }

    private fun openOverlaySettings() {
        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")))
    }

    private fun stopBubbleService() {
        stopService(Intent(this, FloatingBubbleService::class.java))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    settingsRepository: SettingsRepository,
    hasOverlayPermission: Boolean,
    onGrantOverlayPermission: () -> Unit,
    onStartBubble: () -> Unit,
    onStopBubble: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val targetLanguage by settingsRepository.targetLanguage.collectAsStateWithLifecycle(initialValue = "de")
    val sourceLanguage by settingsRepository.sourceLanguage.collectAsStateWithLifecycle(initialValue = "auto")
    val bubbleSize by settingsRepository.bubbleSize.collectAsStateWithLifecycle(initialValue = BubbleSize.M)
    val bubbleEnabled by settingsRepository.bubbleEnabled.collectAsStateWithLifecycle(initialValue = false)

    Scaffold(
        containerColor = BgColor,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.lolo_soft_logo),
                            contentDescription = "Logo",
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            contentScale = ContentScale.Fit
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            stringResource(R.string.title),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextWhite
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgColor)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BgColor)
                .padding(padding)
                .padding(horizontal = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // --- Erklärung ---
            DarkCard {
                Text(stringResource(R.string.description), color = TextGray, fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.height(1.dp))
            HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(1.dp))

            // --- Berechtigungsstatus ---
            DarkCard {
                SectionTitle(stringResource(R.string.permission_status))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (hasOverlayPermission) "✓ ${stringResource(R.string.permission_granted)}"
                        else "✗ ${stringResource(R.string.permission_denied)}",
                        color = if (hasOverlayPermission) TextWhite else TextDim,
                        fontSize = 15.sp
                    )
                }
                if (!hasOverlayPermission) {
                    Spacer(modifier = Modifier.height(10.dp))
                    DarkButton(stringResource(R.string.grant_permission), onClick = onGrantOverlayPermission)
                }
            }

            Spacer(modifier = Modifier.height(1.dp))
            HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(1.dp))

            // --- Bubble-Steuerung ---
            DarkCard {
                SectionTitle("Bubble")
                Text(
                    text = if (bubbleEnabled) "● ${stringResource(R.string.bubble_active)}"
                    else "○ ${stringResource(R.string.bubble_inactive)}",
                    color = if (bubbleEnabled) TextWhite else TextDim,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DarkButton(
                        stringResource(R.string.start_bubble),
                        enabled = hasOverlayPermission && !bubbleEnabled,
                        onClick = {
                            onStartBubble()
                            scope.launch { settingsRepository.setBubbleEnabled(true) }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    DarkButton(
                        stringResource(R.string.stop_bubble),
                        enabled = bubbleEnabled,
                        onClick = {
                            onStopBubble()
                            scope.launch { settingsRepository.setBubbleEnabled(false) }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(1.dp))
            HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(1.dp))

            // --- Spracheinstellungen ---
            DarkCard {
                SectionTitle(stringResource(R.string.source_language))
                LanguageDropdown(selectedCode = sourceLanguage,
                    languages = TranslationManager.supportedLanguages,
                    onLanguageSelected = { scope.launch { settingsRepository.setSourceLanguage(it.code) } })
                Spacer(modifier = Modifier.height(12.dp))
                SectionTitle(stringResource(R.string.target_language))
                LanguageDropdown(selectedCode = targetLanguage,
                    languages = TranslationManager.targetLanguages,
                    onLanguageSelected = { scope.launch { settingsRepository.setTargetLanguage(it.code) } })
            }

            Spacer(modifier = Modifier.height(1.dp))
            HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(1.dp))

            // --- Bubble-Größe ---
            DarkCard {
                SectionTitle(stringResource(R.string.bubble_size))
                BubbleSizeSelector(selectedSize = bubbleSize,
                    onSizeSelected = { scope.launch { settingsRepository.setBubbleSize(it) } })
            }

            Spacer(modifier = Modifier.height(1.dp))
            HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(1.dp))

            // --- Diagnose ---
            var clipboardTestResult by remember { mutableStateOf("") }
            DarkCard {
                SectionTitle("Diagnose")
                DarkButton(stringResource(R.string.clipboard_test), onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = cm.primaryClip
                    clipboardTestResult = if (clip == null || clip.itemCount == 0) "Kein Text in der Zwischenablage"
                    else {
                        val txt = clip.getItemAt(0).coerceToText(context)?.toString()
                        if (txt.isNullOrEmpty()) "Clipboard enthält keinen Text"
                        else "Text gefunden (${txt.length} Zeichen): \"${txt.take(80)}…\""
                    }
                }, modifier = Modifier.fillMaxWidth())
                if (clipboardTestResult.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(clipboardTestResult, color = TextGray, fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(1.dp))
            HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(1.dp))

            // --- Datenschutzhinweis ---
            DarkCard {
                SectionTitle("Datenschutz")
                Text(stringResource(R.string.privacy_notice), color = TextDim, fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ── wiederverwendbare Dark-UI-Elemente ──

@Composable
fun DarkCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(0.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) { content() }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, color = TextWhite, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(6.dp))
}

@Composable
fun DarkButton(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (enabled) BtnBg else Color(0xFF050505),
            contentColor = if (enabled) TextWhite else TextDim,
            disabledContainerColor = Color(0xFF050505),
            disabledContentColor = TextDim
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (enabled) BtnBorder else Color(0xFF333333))
    ) {
        Text(text, fontSize = 13.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageDropdown(
    selectedCode: String,
    languages: List<TranslationManager.LanguageOption>,
    onLanguageSelected: (TranslationManager.LanguageOption) -> Unit
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = languages.find { it.code == selectedCode }?.let {
        try { context.getString(it.labelResId) } catch (_: Exception) { it.code }
    } ?: selectedCode

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextWhite,
                unfocusedTextColor = TextWhite,
                focusedBorderColor = CardBorder,
                unfocusedBorderColor = CardBorder,
                focusedContainerColor = BgColor,
                unfocusedContainerColor = BgColor
            )
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false },
            containerColor = BgColor) {
            languages.forEach { lang ->
                val label = try { context.getString(lang.labelResId) } catch (_: Exception) { lang.code }
                DropdownMenuItem(text = { Text(label, color = TextWhite) }, onClick = {
                    onLanguageSelected(lang); expanded = false
                })
            }
        }
    }
}

@Composable
fun BubbleSizeSelector(selectedSize: BubbleSize, onSizeSelected: (BubbleSize) -> Unit) {
    val context = LocalContext.current
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        BubbleSize.entries.forEach { size ->
            val isSelected = size == selectedSize
            Button(
                onClick = { onSizeSelected(size) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSelected) TextWhite else BtnBg,
                    contentColor = if (isSelected) BgColor else TextWhite
                ),
                border = if (!isSelected) androidx.compose.foundation.BorderStroke(1.dp, CardBorder) else null
            ) {
                Text(
                    text = try { context.getString(size.labelResId) } catch (_: Exception) { size.name },
                    fontSize = 12.sp
                )
            }
        }
    }
}
