package com.example.aidocumentscanner.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onResultClick: (Long, Int) -> Unit  // documentId, pageIndex
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { DocumentRepository(context) }
    val documents by repository.getAllDocuments().collectAsState(initial = emptyList())
    
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<SearchResultItem>>(emptyList()) }
    var selectedDocument by remember { mutableStateOf<Document?>(null) }
    var ocrProgress by remember { mutableStateOf<Pair<Int, Int>?>(null) }  // current, total
    
    // Initialize OCR engine
    LaunchedEffect(Unit) {
        OcrEngine.initialize(context)
    }
    
    // Cache for OCR results
    var cachedOcrResults by remember { mutableStateOf<Map<Long, List<OcrEngine.PageOcrResult>>>(emptyMap()) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Search Documents", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                placeholder = { Text("Search for text in your documents...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        if (searchQuery.isNotBlank()) {
                            isSearching = true
                            scope.launch {
                                searchResults = performSearch(
                                    context = context,
                                    documents = documents,
                                    query = searchQuery,
                                    cachedResults = cachedOcrResults,
                                    onProgress = { current, total ->
                                        ocrProgress = current to total
                                    },
                                    onCacheUpdate = { docId, results ->
                                        cachedOcrResults = cachedOcrResults + (docId to results)
                                    }
                                )
                                isSearching = false
                                ocrProgress = null
                            }
                        }
                    }
                )
            )
            
            // Search button
            Button(
                onClick = {
                    if (searchQuery.isNotBlank()) {
                        isSearching = true
                        scope.launch {
                            searchResults = performSearch(
                                context = context,
                                documents = documents,
                                query = searchQuery,
                                cachedResults = cachedOcrResults,
                                onProgress = { current, total ->
                                    ocrProgress = current to total
                                },
                                onCacheUpdate = { docId, results ->
                                    cachedOcrResults = cachedOcrResults + (docId to results)
                                }
                            )
                            isSearching = false
                            ocrProgress = null
                        }
                    }
                },
                enabled = searchQuery.isNotBlank() && !isSearching,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isSearching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    ocrProgress?.let { (current, total) ->
                        Text("Scanning document $current of $total...")
                    } ?: Text("Searching...")
                } else {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Search All Documents")
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Results
            if (searchResults.isNotEmpty()) {
                Text(
                    "${searchResults.size} result${if (searchResults.size > 1) "s" else ""} found",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(searchResults) { result ->
                        SearchResultCard(
                            result = result,
                            searchQuery = searchQuery,
                            onClick = {
                                onResultClick(result.documentId, result.pageIndex)
                            }
                        )
                    }
                }
            } else if (!isSearching && searchQuery.isNotEmpty()) {
                // No results
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.SearchOff,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("No results found", fontWeight = FontWeight.Medium)
                        Text(
                            "Try a different search term",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (!isSearching) {
                // Initial state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.FindInPage,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Search inside your PDFs", fontWeight = FontWeight.Medium)
                        Text(
                            "Enter a keyword to find text in all documents",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

data class SearchResultItem(
    val documentId: Long,
    val documentName: String,
    val pageIndex: Int,
    val matchedText: String,
    val context: String,
    val lineIndex: Int
)

@Composable
private fun SearchResultCard(
    result: SearchResultItem,
    searchQuery: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.PictureAsPdf,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    result.documentName,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        "Page ${result.pageIndex + 1}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Highlighted context
            Text(
                buildAnnotatedString {
                    val lowerContext = result.context.lowercase()
                    val lowerQuery = searchQuery.lowercase()
                    var lastIndex = 0
                    
                    var matchIndex = lowerContext.indexOf(lowerQuery)
                    while (matchIndex >= 0) {
                        // Text before match
                        append(result.context.substring(lastIndex, matchIndex))
                        
                        // Highlighted match
                        withStyle(SpanStyle(
                            background = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )) {
                            append(result.context.substring(matchIndex, matchIndex + searchQuery.length))
                        }
                        
                        lastIndex = matchIndex + searchQuery.length
                        matchIndex = lowerContext.indexOf(lowerQuery, lastIndex)
                    }
                    
                    // Remaining text
                    if (lastIndex < result.context.length) {
                        append(result.context.substring(lastIndex))
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private suspend fun performSearch(
    context: android.content.Context,
    documents: List<Document>,
    query: String,
    cachedResults: Map<Long, List<OcrEngine.PageOcrResult>>,
    onProgress: (Int, Int) -> Unit,
    onCacheUpdate: (Long, List<OcrEngine.PageOcrResult>) -> Unit
): List<SearchResultItem> = withContext(Dispatchers.IO) {
    val results = mutableListOf<SearchResultItem>()
    
    documents.forEachIndexed { index, document ->
        onProgress(index + 1, documents.size)
        
        // Get OCR results from cache or perform OCR
        val ocrResults = cachedResults[document.id] ?: run {
            val newResults = OcrEngine.extractTextFromPdf(context, document.pdfPath)
            onCacheUpdate(document.id, newResults)
            newResults
        }
        
        // Search in OCR results
        val matches = OcrEngine.searchKeyword(ocrResults, query, caseSensitive = false)
        
        for (match in matches) {
            results.add(SearchResultItem(
                documentId = document.id,
                documentName = document.name,
                pageIndex = match.pageIndex,
                matchedText = match.matchedText,
                context = match.context,
                lineIndex = match.lineIndex
            ))
        }
    }
    
    results
}
