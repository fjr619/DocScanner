package com.fjr.docscanner.presentation.screens.list_img

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
fun ListImgTab(list: List<DocImg>) {
    val context = LocalContext.current

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.padding(16.dp).fillMaxSize(),
//                    contentPadding = PaddingValues(16.dp),
    ) {
        items(list) { doc ->
//                        DocBox(doc, context, viewModel)
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .border(0.dp, Color.Transparent, RoundedCornerShape(16.dp))
                    .combinedClickable(
                        onClick = { context.openUri(doc.uri) },
                        onLongClick = {  }
                    )
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