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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aidocumentscanner.DocuScanApplication
import com.example.aidocumentscanner.domain.search.DocumentSearchResult
import com.example.aidocumentscanner.ui.search.SearchViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onResultClick: (Long, Int) -> Unit
) {
    val app = LocalContext.current.applicationContext as DocuScanApplication
    val viewModel: SearchViewModel = viewModel(
        factory = SearchViewModel.Factory(
            app.container.searchDocumentsUseCase
        )
    )
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbar.showSnackbar(it)
            viewModel.dismissError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Text("Search documents", fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                value = state.query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                placeholder = { Text("Search names and scanned text") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = viewModel::clearQuery) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.search() })
            )

            Button(
                onClick = viewModel::search,
                enabled = state.query.isNotBlank() && !state.isSearching,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(50.dp)
            ) {
                if (state.isSearching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(state.progress ?: "Searching…")
                } else {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Search all documents")
                }
            }

            Spacer(Modifier.height(12.dp))

            when {
                state.results.isNotEmpty() -> {
                    Text(
                        "${state.results.size} match${if (state.results.size == 1) "" else "es"}",
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )

                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(
                            state.results,
                            key = {
                                "${it.documentId}:${it.pageIndex}:${it.source}:${it.context.hashCode()}"
                            }
                        ) { result ->
                            SearchResultCardPhase9(
                                result = result,
                                query = state.query.trim(),
                                onClick = {
                                    onResultClick(result.documentId, result.pageIndex)
                                }
                            )
                        }
                    }
                }

                state.hasSearched && !state.isSearching -> {
                    SearchEmptyPhase9(
                        icon = Icons.Default.SearchOff,
                        title = "No matches",
                        subtitle = "Try another word or phrase."
                    )
                }

                else -> {
                    SearchEmptyPhase9(
                        icon = Icons.Default.FindInPage,
                        title = "Search inside PDFs",
                        subtitle = "OCR indexing is local and persisted so indexed documents are reused after restart."
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultCardPhase9(
    result: DocumentSearchResult,
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
                        if (result.source == DocumentSearchResult.Source.DOCUMENT_NAME) {
                            "Name"
                        } else {
                            "Page ${result.pageIndex + 1}"
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                buildAnnotatedString {
                    if (query.isBlank()) {
                        append(result.context)
                    } else {
                        val lower = result.context.lowercase()
                        val needle = query.lowercase()
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
                                        (hit + needle.length).coerceAtMost(result.context.length)
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
private fun SearchEmptyPhase9(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Box(
        modifier = Modifier.fillMaxSize(),
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
