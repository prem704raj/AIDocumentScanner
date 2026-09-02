package com.example.aidocumentscanner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/** Compatibility first-run dialog with Phase-8 truthful wording. */
@Composable
fun PrivacyDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    "Privacy in DocuScan",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                PrivacyFeature(
                    Icons.Default.Lock,
                    "Local core processing",
                    "Scanning, PDF tools and bundled OCR are designed to run on this device."
                )
                PrivacyFeature(
                    Icons.Default.CameraAlt,
                    "Minimum access",
                    "Camera is optional. Images and PDFs can be selected with Android system pickers."
                )
                PrivacyFeature(
                    Icons.Default.PersonOff,
                    "No account required",
                    "Core features do not require sign-in."
                )
                PrivacyFeature(
                    Icons.Default.Share,
                    "Sharing is your action",
                    "When you choose Share or a public save location, a copy can leave app-private storage."
                )
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Text("Continue") }
            }
        }
    }
}

@Composable
private fun PrivacyFeature(icon: ImageVector, title: String, description: String) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Column {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Compatibility component retained for any old caller. */
@Composable
fun StudentModeCard(
    isEnabled: Boolean,
    subject: String,
    onToggle: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onToggle,
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (isEnabled) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            }
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.School, null, modifier = Modifier.size(30.dp))
            Column(Modifier.weight(1f)) {
                Text("Study mode", fontWeight = FontWeight.SemiBold)
                Text(
                    if (isEnabled && subject.isNotBlank()) subject
                    else if (isEnabled) "Enabled" else "Off",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Switch(checked = isEnabled, onCheckedChange = { onToggle() })
            IconButton(onClick = onSettings, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.Settings, contentDescription = "Study mode settings")
            }
        }
    }
}

@Composable
fun QuickActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    gradient: List<Color> = listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))
) {
    Card(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(16.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .background(Brush.linearGradient(gradient)).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(30.dp))
            Spacer(Modifier.height(8.dp))
            Text(
                label,
                color = Color.White,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
