package com.fjr.docscanner.domain.repository

import android.net.Uri
import com.fjr.docscanner.domain.model.DocImg
import com.fjr.docscanner.domain.model.DocPdf
import com.fjr.docscanner.domain.util.DataError
import com.fjr.docscanner.domain.util.EmptyResult
import com.fjr.docscanner.domain.util.Result
import kotlinx.coroutines.CoroutineScope

interface ScannerRepository {
    suspend fun saveDocPdf(pdfUri: Uri): EmptyResult<DataError.Storage>
    suspend fun readDocsPdf(): Result<List<DocPdf>, DataError.Storage>
    suspend fun saveDocImg(imageUri: Uri): EmptyResult<DataError.Storage>
    suspend fun readDocsImg(): Result<List<DocImg>, DataError.Storage>
    suspend fun startObservingDocuments(onUpdateImg: () -> Unit, onUpdatePdf: () -> Unit)
    suspend fun stopObservingDocuments()
}
