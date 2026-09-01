package com.example.aidocumentscanner.navigation

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.aidocumentscanner.ui.screens.CameraScreen
import com.example.aidocumentscanner.ui.screens.DevicePdfBrowserScreen
import com.example.aidocumentscanner.ui.screens.DocumentsScreen
import com.example.aidocumentscanner.ui.screens.EditorScreen
import com.example.aidocumentscanner.ui.screens.ExternalPdfViewerScreen
import com.example.aidocumentscanner.ui.screens.HomeScreen
import com.example.aidocumentscanner.ui.screens.PdfOptimizerScreen
import com.example.aidocumentscanner.ui.screens.PdfPreviewScreen
import com.example.aidocumentscanner.ui.screens.PdfToolsScreen
import com.example.aidocumentscanner.ui.screens.PdfViewerScreen
import com.example.aidocumentscanner.ui.screens.SearchScreen
import com.example.aidocumentscanner.ui.screens.SettingsScreen
import com.example.aidocumentscanner.ui.theme.ThemeMode

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Camera : Screen("camera")
    data object Editor : Screen("editor")
    data object PdfPreview : Screen("pdf_preview")
    data object Documents : Screen("documents")
    data object Settings : Screen("settings")

    data object PdfViewer : Screen("pdf_viewer/{documentId}?page={page}") {
        fun createRoute(documentId: Long, page: Int = 0): String =
            "pdf_viewer/$documentId?page=${page.coerceAtLeast(0)}"
    }

    data object PdfTools : Screen("pdf_tools")
    data object Search : Screen("search")
    data object PdfOptimizer : Screen("pdf_optimizer")
    data object DevicePdfs : Screen("device_pdfs")
    data object ExternalPdfViewer : Screen("external_pdf_viewer")
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(
    navController: NavHostController,
    pages: MutableList<Bitmap>,
    onAddPage: (Bitmap) -> Unit,
    onAddPages: (List<Bitmap>) -> Unit,
    onClearPages: () -> Unit,
    onRemovePage: (Int) -> Unit,
    onReplacePage: (Int, Bitmap) -> Unit,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onThemeModeChange: (ThemeMode) -> Unit = {},
    externalPdfUri: Uri? = null,
    onExternalPdfHandled: () -> Unit = {}
) {
    var selectedPdfUri by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(externalPdfUri) {
        externalPdfUri?.let { uri ->
            selectedPdfUri = uri
            navController.navigate(Screen.ExternalPdfViewer.route)
        }
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onScanClick = { navController.navigate(Screen.Camera.route) },
                onDocumentsClick = { navController.navigate(Screen.Documents.route) },
                onSettingsClick = { navController.navigate(Screen.Settings.route) },
                onPdfToolsClick = { navController.navigate(Screen.PdfTools.route) },
                onOptimizeClick = { navController.navigate(Screen.PdfOptimizer.route) },
                onDocumentClick = { documentId ->
                    navController.navigate(Screen.PdfViewer.createRoute(documentId))
                },
                onImagesSelected = { bitmaps ->
                    if (bitmaps.isNotEmpty()) {
                        onAddPages(bitmaps)
                        navController.navigate(Screen.Editor.route)
                    }
                },
                onDevicePdfsClick = { navController.navigate(Screen.DevicePdfs.route) }
            )
        }

        composable(Screen.Camera.route) {
            CameraScreen(
                onImageCaptured = { bitmap ->
                    onAddPage(bitmap)
                    navController.navigate(Screen.Editor.route) {
                        popUpTo(Screen.Camera.route) { inclusive = true }
                    }
                },
                onMultipleImagesCaptured = { bitmaps ->
                    onAddPages(bitmaps)
                    navController.navigate(Screen.Editor.route) {
                        popUpTo(Screen.Camera.route) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Editor.route) {
            EditorScreen(
                pages = pages,
                onContinue = { navController.navigate(Screen.PdfPreview.route) },
                onBack = { navController.popBackStack() },
                onAddMorePages = { navController.navigate(Screen.Camera.route) },
                onRemovePage = onRemovePage,
                onPageUpdated = onReplacePage,
                onReorderPages = { from, to ->
                    if (from in pages.indices && to in pages.indices && from != to) {
                        val page = pages.removeAt(from)
                        pages.add(to, page)
                    }
                },
                onDuplicatePage = { index ->
                    if (index in pages.indices) {
                        val original = pages[index]
                        val copy = original.copy(original.config ?: Bitmap.Config.ARGB_8888, true)
                        pages.add(index + 1, copy)
                    }
                }
            )
        }

        composable(Screen.PdfPreview.route) {
            PdfPreviewScreen(
                pages = pages,
                onSave = { documentId ->
                    onClearPages()
                    navController.navigate(Screen.PdfViewer.createRoute(documentId)) {
                        popUpTo(Screen.Home.route) { inclusive = false }
                    }
                },
                onAddMore = { navController.navigate(Screen.Camera.route) },
                onBack = { navController.popBackStack() },
                onReorderPages = { from, to ->
                    if (from in pages.indices && to in pages.indices && from != to) {
                        val page = pages.removeAt(from)
                        pages.add(to, page)
                    }
                }
            )
        }

        composable(Screen.Documents.route) {
            DocumentsScreen(
                onDocumentClick = { documentId ->
                    navController.navigate(Screen.PdfViewer.createRoute(documentId))
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                currentThemeMode = themeMode,
                onThemeModeChange = onThemeModeChange
            )
        }

        composable(
            route = Screen.PdfViewer.route,
            arguments = listOf(
                navArgument("documentId") { type = NavType.LongType },
                navArgument("page") {
                    type = NavType.IntType
                    defaultValue = 0
                }
            )
        ) { entry ->
            val documentId = entry.arguments?.getLong("documentId") ?: 0L
            val page = entry.arguments?.getInt("page") ?: 0
            PdfViewerScreen(
                documentId = documentId,
                initialPage = page,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.PdfTools.route) {
            PdfToolsScreen(
                onBack = { navController.popBackStack() },
                onMergeComplete = { documentId ->
                    navController.navigate(Screen.PdfViewer.createRoute(documentId))
                },
                onOptimizeRequested = {
                    navController.navigate(Screen.PdfOptimizer.route)
                }
            )
        }

        composable(Screen.Search.route) {
            SearchScreen(
                onBack = { navController.popBackStack() },
                onResultClick = { documentId, pageIndex ->
                    navController.navigate(
                        Screen.PdfViewer.createRoute(documentId, pageIndex)
                    )
                }
            )
        }

        composable(Screen.PdfOptimizer.route) {
            PdfOptimizerScreen(
                onBack = { navController.popBackStack() },
                onOptimized = { documentId ->
                    navController.navigate(Screen.PdfViewer.createRoute(documentId)) {
                        popUpTo(Screen.PdfOptimizer.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.DevicePdfs.route) {
            DevicePdfBrowserScreen(
                onBack = { navController.popBackStack() },
                onPdfSelected = { uri ->
                    selectedPdfUri = uri
                    navController.navigate(Screen.ExternalPdfViewer.route)
                }
            )
        }

        composable(Screen.ExternalPdfViewer.route) {
            val uri = selectedPdfUri
            if (uri == null) {
                LaunchedEffect(Unit) { navController.popBackStack() }
            } else {
                ExternalPdfViewerScreen(
                    pdfUri = uri,
                    onBack = {
                        onExternalPdfHandled()
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}
