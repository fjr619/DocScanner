package com.fjr.docscanner.presentation.screens.list_img

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fjr.docscanner.domain.model.DocImg
import com.fjr.docscanner.presentation.util.openUri

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ListImgTab(paddingValues: PaddingValues, listDocImg: List<DocImg>, gridState: LazyGridState) {
    val context = LocalContext.current

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(1),
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxSize(),
        contentPadding = PaddingValues(top = paddingValues.calculateTopPadding(), bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(listDocImg) { doc ->

            Card(
                modifier = Modifier.combinedClickable(
                    onClick = { context.openUri(doc.uri, "image/*") },
                    onLongClick = { println("longclick") }
                )
            ){
                Column(
                    modifier = Modifier.padding(8.dp)
                ) {
                    Image(
                        bitmap = doc.imageBitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .padding(4.dp)
                            .aspectRatio(1f)
                    )

                    Text(
                        text = doc.filename,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                    )
                }
            }
        }
    }
}