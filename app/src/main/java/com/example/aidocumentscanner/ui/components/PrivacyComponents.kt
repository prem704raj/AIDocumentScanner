package com.example.aidocumentscanner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * Privacy dialog shown on first launch.
 * Uses truthful Phase-6 on-device privacy copy.
 */
@Composable
fun PrivacyDialog(
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(
                            brush = Brush.linearGradient(
                                listOf(Color(0xFF10B981), Color(0xFF34D399))
                            ),
                            shape = RoundedCornerShape(20.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Shield,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = Color.White
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    "Private by Design",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                PrivacyFeature(
                    icon = Icons.Default.Lock,
                    title = "On-Device Processing",
                    description = "Core scanning, PDF tools and bundled OCR run locally on your device."
                )
                
                PrivacyFeature(
                    icon = Icons.Default.PrivacyTip,
                    title = "Local App Data",
                    description = "Document records and OCR text are stored in the app's local data."
                )
                
                PrivacyFeature(
                    icon = Icons.Default.PersonOff,
                    title = "No Account Required",
                    description = "DocuScan does not require sign-in to use its features."
                )
                
                PrivacyFeature(
                    icon = Icons.Default.DeleteSweep,
                    title = "Your Data, Your Control",
                    description = "Delete documents and thumbnails permanently at any time."
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Got it!", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun PrivacyFeature(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
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
            Text(
                title,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Student Mode toggle card for quick access
 */
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
            containerColor = if (isEnabled)
                MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.School,
                contentDescription = null,
                tint = if (isEnabled) 
                    MaterialTheme.colorScheme.primary 
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp)
            )
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Student Mode",
                    fontWeight = FontWeight.Bold,
                    color = if (isEnabled) 
                        MaterialTheme.colorScheme.primary 
                    else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    if (isEnabled && subject.isNotBlank()) subject 
                    else if (isEnabled) "Enabled" 
                    else "Tap to enable",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Switch(
                checked = isEnabled,
                onCheckedChange = { onToggle() }
            )
            
            IconButton(onClick = onSettings) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Quick action button for common operations
 */
@Composable
fun QuickActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    gradient: List<Color> = listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(gradient))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                label,
                color = Color.White,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
