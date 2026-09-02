package com.example.aidocumentscanner.ui.screens

import android.content.Context
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.aidocumentscanner.BuildConfig
import com.example.aidocumentscanner.DocuScanApplication
import com.example.aidocumentscanner.billing.MonetizationConfig
import com.example.aidocumentscanner.ui.theme.ThemeMode

enum class StorageLocation(
    val label: String,
    val description: String,
    val icon: ImageVector
) {
    INTERNAL(
        "App storage",
        "Private local storage used by DocuScan",
        Icons.Default.PhoneAndroid
    ),
    DOCUMENTS(
        "Documents folder",
        "Also create a copy in the public Documents folder",
        Icons.Default.Folder
    ),
    DOWNLOADS(
        "Downloads folder",
        "Also create a copy in the public Downloads folder",
        Icons.Default.Download
    )
}

/**
 * Compatibility API retained for PdfGenerator and older screens.
 *
 * Phase 6 changes the fresh-install default to INTERNAL so the UI/privacy model matches
 * scoped-storage behavior. Existing users keep the value already saved in preferences.
 */
object SettingsPreferences {
    private const val PREFS_NAME = "app_settings"
    private const val KEY_STORAGE_LOCATION = "storage_location"
    private const val KEY_DEFAULT_PAGE_SIZE = "default_page_size"
    private const val KEY_DEFAULT_QUALITY = "default_quality"
    private const val KEY_AUTO_DETECT = "auto_detect"

    fun getStorageLocation(context: Context): StorageLocation {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val value = prefs.getString(
            KEY_STORAGE_LOCATION,
            StorageLocation.INTERNAL.name
        )
        return runCatching {
            StorageLocation.valueOf(value ?: StorageLocation.INTERNAL.name)
        }.getOrDefault(StorageLocation.INTERNAL)
    }

    fun setStorageLocation(context: Context, location: StorageLocation) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_STORAGE_LOCATION, location.name)
            .apply()
    }

    fun getDefaultPageSize(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_DEFAULT_PAGE_SIZE, "A4") ?: "A4"

    fun setDefaultPageSize(context: Context, size: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DEFAULT_PAGE_SIZE, size)
            .apply()
    }

    fun getDefaultQuality(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_DEFAULT_QUALITY, "High") ?: "High"

    fun setDefaultQuality(context: Context, quality: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DEFAULT_QUALITY, quality)
            .apply()
    }

    fun getAutoDetect(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_DETECT, true)

    fun setAutoDetect(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_DETECT, enabled)
            .apply()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentThemeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onBack: () -> Unit,
    onPrivacyClick: () -> Unit = {},
    onProClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val app = context.applicationContext as DocuScanApplication
    val billingState by app.container.billingManager.state.collectAsState()
    var storage by remember {
        mutableStateOf(SettingsPreferences.getStorageLocation(context))
    }
    var showStorage by remember { mutableStateOf(false) }
    var showTheme by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Settings", fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = androidx.compose.ui.Modifier
                            .height(48.dp)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            if (MonetizationConfig.ENABLED || billingState.isPro) {
                SettingsSection("DocuScan Pro") {
                    SettingsRow(
                        title = if (billingState.isPro) "Lifetime Pro active" else "Unlock DocuScan Pro",
                        subtitle = if (billingState.isPro) {
                            "Advanced PDF editing is unlocked"
                        } else {
                            buildString {
                                append("One-time advanced PDF tools")
                                billingState.formattedPrice?.let {
                                    append(" • ")
                                    append(it)
                                }
                            }
                        },
                        icon = Icons.Default.WorkspacePremium,
                        onClick = onProClick
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            SettingsSection("Storage") {
                SettingsRow(
                    title = "Save location",
                    subtitle = storage.label,
                    icon = storage.icon,
                    onClick = { showStorage = true }
                )
            }

            Spacer(Modifier.height(16.dp))

            SettingsSection("Appearance") {
                SettingsRow(
                    title = "Theme",
                    subtitle = when (currentThemeMode) {
                        ThemeMode.SYSTEM -> "Follow system"
                        ThemeMode.LIGHT -> "Light"
                        ThemeMode.DARK -> "Dark"
                    },
                    icon = when (currentThemeMode) {
                        ThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
                        ThemeMode.LIGHT -> Icons.Default.LightMode
                        ThemeMode.DARK -> Icons.Default.DarkMode
                    },
                    onClick = { showTheme = true }
                )
            }

            Spacer(Modifier.height(16.dp))

            SettingsSection("Privacy & processing") {
                SettingsRow(
                    title = "Privacy & data",
                    subtitle = "Permissions, local storage, exports and erase controls",
                    icon = Icons.Default.PrivacyTip,
                    onClick = onPrivacyClick
                )
                HorizontalDivider()
                StaticSettingsRow(
                    title = "On-device processing",
                    subtitle = "Scanning, PDF tools and bundled OCR run locally on this device.",
                    icon = Icons.Default.Lock
                )
                HorizontalDivider()
                StaticSettingsRow(
                    title = "No account required",
                    subtitle = "DocuScan does not require sign-in to use its core features.",
                    icon = Icons.Default.PersonOff
                )
                HorizontalDivider()
                StaticSettingsRow(
                    title = "Local app data",
                    subtitle = "Document records and OCR text are stored in the app's local data.",
                    icon = Icons.Default.PrivacyTip
                )
            }

            Spacer(Modifier.height(16.dp))

            SettingsSection("About") {
                StaticSettingsRow(
                    title = "Version",
                    subtitle = BuildConfig.VERSION_NAME,
                    icon = Icons.Default.Info
                )
            }

            Spacer(Modifier.height(28.dp))
        }
    }

    if (showStorage) {
        AlertDialog(
            onDismissRequest = { showStorage = false },
            title = { Text("Save location") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val locations = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        StorageLocation.entries
                    } else {
                        listOf(StorageLocation.INTERNAL)
                    }

                    locations.forEach { location ->
                        Card(
                            onClick = {
                                storage = location
                                SettingsPreferences.setStorageLocation(
                                    context,
                                    location
                                )
                                showStorage = false
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor =
                                    if (location == storage) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    }
                            )
                        ) {
                            ListItem(
                                headlineContent = { Text(location.label) },
                                supportingContent = {
                                    Text(location.description)
                                },
                                leadingContent = {
                                    Icon(
                                        location.icon,
                                        contentDescription = null
                                    )
                                },
                                trailingContent = {
                                    if (location == storage) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = "Selected"
                                        )
                                    }
                                }
                            )
                        }
                    }

                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        Text(
                            "On Android 8–9, public-folder copies are hidden here because DocuScan no longer requests broad storage permission. Use Share/Export instead.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showStorage = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (showTheme) {
        AlertDialog(
            onDismissRequest = { showTheme = false },
            title = { Text("Theme") },
            text = {
                Column {
                    ThemeChoice(
                        label = "Follow system",
                        selected = currentThemeMode == ThemeMode.SYSTEM,
                        onClick = {
                            onThemeModeChange(ThemeMode.SYSTEM)
                            showTheme = false
                        }
                    )
                    ThemeChoice(
                        label = "Light",
                        selected = currentThemeMode == ThemeMode.LIGHT,
                        onClick = {
                            onThemeModeChange(ThemeMode.LIGHT)
                            showTheme = false
                        }
                    )
                    ThemeChoice(
                        label = "Dark",
                        selected = currentThemeMode == ThemeMode.DARK,
                        onClick = {
                            onThemeModeChange(ThemeMode.DARK)
                            showTheme = false
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showTheme = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            title,
            modifier = Modifier.padding(
                horizontal = 16.dp,
                vertical = 8.dp
            ),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(title, fontWeight = FontWeight.Medium)
        },
        supportingContent = { Text(subtitle) },
        leadingContent = {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    )
}

@Composable
private fun StaticSettingsRow(
    title: String,
    subtitle: String,
    icon: ImageVector
) {
    ListItem(
        headlineContent = {
            Text(title, fontWeight = FontWeight.Medium)
        },
        supportingContent = { Text(subtitle) },
        leadingContent = {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    )
}

@Composable
private fun ThemeChoice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = {
            RadioButton(
                selected = selected,
                onClick = onClick
            )
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}
