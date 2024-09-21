package com.fjr.docscanner.presentation.screens.scanner

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fjr.docscanner.R
import com.fjr.docscanner.presentation.components.MultiSelector
import com.fjr.docscanner.presentation.components.Scanner
import com.fjr.docscanner.presentation.screens.list_img.ListImgTab
import com.fjr.docscanner.presentation.screens.list_pdf.ListPdfTab
import com.fjr.docscanner.presentation.util.RequestStoragePermissions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

class PagerNestedScrollConnection : NestedScrollConnection

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ScannerScreen(
    scannerViewModel: ScannerViewModel = koinViewModel()
) {
    var hasPermission by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val options = listOf("Image", "Pdf")
    val pagerState = rememberPagerState(pageCount = { options.size })
    val selectedTabIndex by remember { derivedStateOf { pagerState.currentPage } }
    var startScan by remember { mutableStateOf(false) }
    val state by scannerViewModel.scannerState.collectAsStateWithLifecycle()

    // Create scroll states for each page
    val imgListState = rememberLazyGridState() // For images
    val pdfListState = rememberLazyListState() // For PDFs

    val a = rememberLazyGridState()

    if (!hasPermission) {
        RequestStoragePermissions { granted ->
            println("== granted $granted")
            hasPermission = granted

            println("== hasPermission $hasPermission")
        }
    } else {
        LaunchedEffect(key1 = Unit) {
            scannerViewModel.readAllDocs()
        }
    }

    if (startScan) {
        val activity = context as ComponentActivity
        Scanner(
            selectedTabIndex = selectedTabIndex,
            activity = activity,
            onResult = { activityResult ->
                startScan = false

                if (activityResult.resultCode == Activity.RESULT_OK) {
                    val result =
                        GmsDocumentScanningResult.fromActivityResultIntent(activityResult.data)
                    if (selectedTabIndex == 0) {
                        //image
                        result?.pages?.let { pages ->
                            for (page in pages) {
                                val imageUri = page.imageUri
                                scannerViewModel.saveDocImg(imageUri)
                            }
                        }
                    } else {
                        //pdf
                        result?.pdf?.let { pdf ->
                            scannerViewModel.saveDocPdf(pdf.uri)
                        }
                    }


                }
            }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(title = {
                MultiSelector(
                    options = options,
                    selectedOption = options[selectedTabIndex],
                    onOptionSelect = { option ->
                        scope.launch {
                            println("onclick $option ${options.indexOf(option)}")
                            pagerState.animateScrollToPage(options.indexOf(option))
                        }
                    },
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .height(40.dp)
                )
            })
        },
        floatingActionButton = {
            FloatingActionButton(
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 0.dp,
                    focusedElevation = 0.dp,
                    hoveredElevation = 0.dp,
                    pressedElevation = 0.dp
                ),
                onClick = {
                    startScan = true
                }) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Scan")
                    Spacer(modifier = Modifier.width(5.dp))
                    Icon(
                        painter = painterResource(id = R.drawable.capture),
                        contentDescription = null,
                    )
                }
            }
        }

    ) { paddingValues ->

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> {
                    ListImgTab(
                        paddingValues = paddingValues,
                        listDocImg = state.listDocsImg ?: listOf(),
                        gridState = imgListState,
                    )
                }

                1 -> {
                    ListPdfTab(
                        paddingValues = paddingValues,
                        listDocPdf = state.listDocsPdf ?: listOf(),
                        listState = pdfListState,
                    )
                }
            }
        }
    }
}

data class Item(val title: String)
