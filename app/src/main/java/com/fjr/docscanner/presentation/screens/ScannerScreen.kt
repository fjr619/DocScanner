package com.fjr.docscanner.presentation.screens

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fjr.docscanner.R
import com.fjr.docscanner.presentation.components.MultiSelector
import com.fjr.docscanner.presentation.components.Scanner
import com.fjr.docscanner.presentation.util.RequestStoragePermissions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(

) {
    var hasPermission by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val options = listOf("Image", "Pdf")
    val pagerState = rememberPagerState(pageCount = { options.size })
    val selectedTabIndex by remember { derivedStateOf { pagerState.currentPage } }
    var startScan by remember { mutableStateOf(false) }


    val scannerViewModel: ScannerViewModel = koinViewModel()
    val state by scannerViewModel.scannerState.collectAsStateWithLifecycle()


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
        val context = LocalContext.current
        val activity = context as ComponentActivity
        Scanner(
            selectedTabIndex = selectedTabIndex,
            activity = activity,
            onResult = { activityResult ->
                startScan = false

                if (activityResult.resultCode == Activity.RESULT_OK) {
                    val result = GmsDocumentScanningResult.fromActivityResultIntent(activityResult.data)

                    result?.pages?.let { pages ->
                        for (page in pages) {
                            val imageUri = page.imageUri
                            println("$imageUri")
//                            Storage.saveDoc(context, imageUri)
//                            viewModel.readDocs()
                        }
                    }
                }
            }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(title = { MultiSelector(
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
                    .height(40.dp)
            ) })
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
        Column(
            modifier = Modifier.padding(
                paddingValues
            )
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = options[selectedTabIndex])
                }
            }

        }
    }
}