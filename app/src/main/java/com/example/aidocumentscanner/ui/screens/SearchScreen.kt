package com.example.aidocumentscanner.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.aidocumentscanner.data.Document
import com.example.aidocumentscanner.data.DocumentRepository
import com.example.aidocumentscanner.ocr.OcrEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SearchResultItem(
    val documentId: Long,
    val documentName: String,
    val pageIndex: Int,
    val context: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onResultClick: (Long, Int) -> Unit
) {
    val context = LocalContext.current
    val repository = remember { DocumentRepository(context) }
    val documents by repository.getAllDocuments().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchResultItem>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var progressText by remember { mutableStateOf<String?>(null) }
    var hasSearched by remember { mutableStateOf(false) }

    fun startSearch() {
        val needle = query.trim()
        if (needle.isEmpty() || searching) return

        scope.launch {
            searching = true
            hasSearched = true
            try {
                results = searchAndPersistMissingOcr(
                    context = context,
                    repository = repository,
                    documents = documents,
                    query = needle,
                    onProgress = { progressText = it }
                )
            } catch (error: Throwable) {
                snackbar.showSnackbar(error.message ?: "Search failed")
            } finally {
                searching = false
                progressText = null
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Search documents", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                placeholder = { Text("Search document names and scanned text") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                query = ""
                                results = emptyList()
                                hasSearched = false
                            }
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { startSearch() })
            )

            Button(
                onClick = { startSearch() },
                enabled = query.isNotBlank() && !searching,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(50.dp)
            ) {
                if (searching) {
                    CircularProgressIndicator(
                        Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(progressText ?: "Searching…")
                } else {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Search all documents")
                }
            }

            Spacer(Modifier.height(12.dp))

            when {
                searching && results.isEmpty() -> Unit

                results.isNotEmpty() -> {
                    Text(
                        "${results.size} match${if (results.size == 1) "" else "es"}",
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(
                            items = results,
                            key = {
                                "${it.documentId}:${it.pageIndex}:${it.context.hashCode()}"
                            }
                        ) { result ->
                            SearchResultCard(
                                result = result,
                                query = query.trim(),
                                onClick = {
                                    onResultClick(
                                        result.documentId,
                                        result.pageIndex
                                    )
                                }
                            )
                        }
                    }
                }

                hasSearched -> {
                    EmptySearchState(
                        icon = Icons.Default.SearchOff,
                        title = "No matches",
                        subtitle = "Try another word or phrase."
                    )
                }

                else -> {
                    EmptySearchState(
                        icon = Icons.Default.FindInPage,
                        title = "Search inside scanned PDFs",
                        subtitle = "OCR runs locally. A document is indexed only once unless you re-run OCR."
                    )
                }
            }
        }
    }
}

private suspend fun searchAndPersistMissingOcr(
    context: android.content.Context,
    repository: DocumentRepository,
    documents: List<Document>,
    query: String,
    onProgress: (String) -> Unit
): List<SearchResultItem> = withContext(Dispatchers.IO) {
    val output = mutableListOf<SearchResultItem>()

    documents.forEachIndexed { documentIndex, document ->
        val nameMatch = document.name.contains(query, ignoreCase = true)
        if (nameMatch) {
            output += SearchResultItem(
                documentId = document.id,
                documentName = document.name,
                pageIndex = 0,
                context = "Document name: ${document.name}"
            )
        }

        val pages = if (document.isOcrProcessed) {
            OcrEngine.decodePersisted(document.extractedText)
        } else {
            onProgress("Indexing ${documentIndex + 1}/${documents.size}")
            val extracted = OcrEngine.extractTextFromPdf(
                context,
                document.pdfPath
            ) { current, total ->
                onProgress(
                    "Indexing ${documentIndex + 1}/${documents.size} • page $current/$total"
                )
            }
            repository.updateOcrText(
                document.id,
                OcrEngine.encodeForPersistence(extracted)
            )
            extracted
        }

        OcrEngine.searchKeyword(
            pagesText = pages,
            keyword = query,
            caseSensitive = false
        ).forEach { match ->
            output += SearchResultItem(
                documentId = document.id,
                documentName = document.name,
                pageIndex = match.pageIndex,
                context = match.context
            )
        }
    }

    output
}

@Composable
private fun SearchResultCard(
    result: SearchResultItem,
    query: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.PictureAsPdf,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    result.documentName,
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        "Page ${result.pageIndex + 1}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                buildAnnotatedString {
                    val lower = result.context.lowercase()
                    val needle = query.lowercase()
                    if (needle.isBlank()) {
                        append(result.context)
                    } else {
                        var cursor = 0
                        while (cursor < result.context.length) {
                            val hit = lower.indexOf(needle, cursor)
                            if (hit < 0) {
                                append(result.context.substring(cursor))
                                break
                            }
                            append(result.context.substring(cursor, hit))
                            withStyle(
                                SpanStyle(
                                    background = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                                    fontWeight = FontWeight.Bold
                                )
                            ) {
                                append(
                                    result.context.substring(
                                        hit,
                                        (hit + needle.length).coerceAtMost(
                                            result.context.length
                                        )
                                    )
                                )
                            }
                            cursor = hit + needle.length
                        }
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EmptySearchState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Box(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(28.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(60.dp),
                tint = MaterialTheme.colorScheme.outline
            )
            Spacer(Modifier.height(12.dp))
            Text(title, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
