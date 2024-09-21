package com.fjr.docscanner.presentation.screens.list_pdf

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fjr.docscanner.domain.model.DocPdf
import com.fjr.docscanner.presentation.util.openUri

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ListPdfTab(paddingValues: PaddingValues,listDocPdf: List<DocPdf>, listState: LazyListState) {
    val context = LocalContext.current

    LazyColumn(
        state = listState,
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxSize(),
        contentPadding = PaddingValues(top = paddingValues.calculateTopPadding(), bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(listDocPdf) { doc ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { context.openUri(doc.uri, "application/pdf") },
                        onLongClick = {}
                    )
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                ) {
                    Text(
                        text = doc.filename,
                    )
                }
            }
        }
    }
}