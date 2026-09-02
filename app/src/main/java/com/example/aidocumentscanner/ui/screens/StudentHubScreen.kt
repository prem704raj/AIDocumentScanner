package com.example.aidocumentscanner.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.aidocumentscanner.data.DocumentRepository
import com.example.aidocumentscanner.data.Folder
import com.example.aidocumentscanner.scanner.StudentModeManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentHubScreen(
    onBack: () -> Unit,
    onStartScan: () -> Unit,
    onOpenSubject: (Long) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { DocumentRepository(context) }

    val settings by StudentModeManager.getSettings(context)
        .collectAsState(
            initial = StudentModeManager.StudentModeSettings()
        )
    val subjects by repository.getStudentSubjects()
        .collectAsState(initial = emptyList())
    val documents by repository.getAllDocuments()
        .collectAsState(initial = emptyList())

    var showAddSubject by remember { mutableStateOf(false) }
    var newSubjectName by remember { mutableStateOf("") }

    val studentDocs = remember(documents) {
        documents.filter {
            it.documentType?.startsWith("student:") == true
        }
    }
    val subjectCounts = remember(documents) {
        documents.groupingBy { it.folderId }.eachCount()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Study mode", fontWeight = FontWeight.Bold)
                        Text(
                            "Subjects, presets and faster study scans",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.School,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Study mode",
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                if (settings.enabled) {
                                    buildString {
                                        append(settings.preset.label)
                                        if (settings.selectedSubjectName.isNotBlank()) {
                                            append(" • ")
                                            append(settings.selectedSubjectName)
                                        }
                                    }
                                } else {
                                    "Turn on preset-aware naming and organization"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.enabled,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    StudentModeManager.setEnabled(
                                        context,
                                        enabled
                                    )
                                }
                            }
                        )
                    }
                }
            }

            item {
                Text(
                    "Quick scan preset",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            items(
                StudentModeManager.StudentScanPreset.entries,
                key = { it.storageValue }
            ) { preset ->
                PresetCard(
                    preset = preset,
                    selected = settings.preset == preset,
                    onSelect = {
                        scope.launch {
                            StudentModeManager.setPreset(context, preset)
                            StudentModeManager.setEnabled(context, true)
                        }
                    },
                    onScan = {
                        scope.launch {
                            StudentModeManager.configureQuickScan(
                                context = context,
                                preset = preset,
                                subjectId = settings.selectedSubjectId,
                                subjectName = settings.selectedSubjectName
                            )
                            onStartScan()
                        }
                    }
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Subjects",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Scans can be saved directly into a subject.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = { showAddSubject = true }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Add")
                    }
                }
            }

            if (subjects.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                "Add your first subject",
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Examples: DBMS, DSA, Mathematics or Physics.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(10.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(
                                    StudentModeManager.suggestedSubjects.take(6)
                                ) { suggestion ->
                                    FilterChip(
                                        selected = false,
                                        onClick = {
                                            newSubjectName = suggestion
                                            showAddSubject = true
                                        },
                                        label = { Text(suggestion) }
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                items(subjects, key = { it.id }) { subject ->
                    SubjectCard(
                        subject = subject,
                        documentCount = subjectCounts[subject.id] ?: 0,
                        selected = settings.selectedSubjectId == subject.id,
                        onSelect = {
                            scope.launch {
                                StudentModeManager.selectSubject(
                                    context,
                                    subject.id,
                                    subject.name
                                )
                                StudentModeManager.setEnabled(context, true)
                            }
                        },
                        onOpen = { onOpenSubject(subject.id) }
                    )
                }
            }

            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.AutoFixHigh,
                                contentDescription = null
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Preset enhancement",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            "When Auto enhance is enabled, the Editor applies the selected preset's recommended filter once per page. Original remains available.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Auto enhance pages",
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = settings.autoEnhance,
                                onCheckedChange = { value ->
                                    scope.launch {
                                        StudentModeManager.setAutoEnhance(
                                            context,
                                            value
                                        )
                                    }
                                }
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Automatic study filename",
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = settings.autoFilename,
                                onCheckedChange = { value ->
                                    scope.launch {
                                        StudentModeManager.setAutoFilename(
                                            context,
                                            value
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    "${studentDocs.size} study document${if (studentDocs.size == 1) "" else "s"} organized so far",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showAddSubject) {
        AlertDialog(
            onDismissRequest = {
                showAddSubject = false
                newSubjectName = ""
            },
            title = { Text("Add subject") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newSubjectName,
                        onValueChange = { newSubjectName = it.take(60) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Subject name") },
                        singleLine = true
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "A subject is stored as a normal local folder, so documents stay compatible with the rest of DocuScan.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = newSubjectName.isNotBlank(),
                    onClick = {
                        val name = newSubjectName.trim()
                        scope.launch {
                            val id = repository.createStudentSubject(name)
                            StudentModeManager.selectSubject(
                                context,
                                id,
                                name
                            )
                            StudentModeManager.setEnabled(context, true)
                            newSubjectName = ""
                            showAddSubject = false
                        }
                    }
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAddSubject = false
                        newSubjectName = ""
                    }
                ) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun PresetCard(
    preset: StudentModeManager.StudentScanPreset,
    selected: Boolean,
    onSelect: () -> Unit,
    onScan: () -> Unit
) {
    Card(
        onClick = onSelect,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                presetIcon(preset),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    preset.label,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    preset.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(onClick = onScan) {
                Icon(
                    Icons.Default.CameraAlt,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("Scan")
            }
        }
    }
}

@Composable
private fun SubjectCard(
    subject: Folder,
    documentCount: Int,
    selected: Boolean,
    onSelect: () -> Unit,
    onOpen: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Folder,
                contentDescription = null,
                tint = Color(subject.color)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    subject.name,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "$documentCount document${if (documentCount == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onSelect) {
                Text(if (selected) "Active" else "Use")
            }
            IconButton(
                onClick = onOpen,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    Icons.Default.Tune,
                    contentDescription = "Open ${subject.name}"
                )
            }
        }
    }
}

private fun presetIcon(
    preset: StudentModeManager.StudentScanPreset
): ImageVector =
    when (preset) {
        StudentModeManager.StudentScanPreset.NOTES -> Icons.Default.School
        StudentModeManager.StudentScanPreset.ASSIGNMENT -> Icons.Default.Folder
        StudentModeManager.StudentScanPreset.LECTURE -> Icons.Default.School
        StudentModeManager.StudentScanPreset.WHITEBOARD -> Icons.Default.AutoFixHigh
        StudentModeManager.StudentScanPreset.BOOK -> Icons.Default.School
        StudentModeManager.StudentScanPreset.QUESTION_PAPER -> Icons.Default.Folder
    }
