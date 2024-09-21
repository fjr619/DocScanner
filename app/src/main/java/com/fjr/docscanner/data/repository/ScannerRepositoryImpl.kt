package com.fjr.docscanner.data.repository

import android.content.Context
import android.net.Uri
import com.fjr.docscanner.data.DataSource
import com.fjr.docscanner.domain.model.DocImg
import com.fjr.docscanner.domain.model.DocPdf
import com.fjr.docscanner.domain.repository.ScannerRepository
import com.fjr.docscanner.domain.util.DataError
import com.fjr.docscanner.domain.util.EmptyResult
import com.fjr.docscanner.domain.util.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.koin.core.annotation.Factory

@Factory
class ScannerRepositoryImpl(
    private val dataSource: DataSource,
    private val coroutineScope: CoroutineScope
): ScannerRepository {
    override suspend fun saveDocPdf(pdfUri: Uri): EmptyResult<DataError.Storage> {
        return dataSource.saveDocPdf(pdfUri)
    }

    override suspend fun readDocsPdf(): Result<List<DocPdf>, DataError.Storage> {
        return dataSource.readDocPdf()
    }

    override suspend fun saveDocImg(imageUri: Uri): EmptyResult<DataError.Storage> {
        return dataSource.saveDocImg(imageUri)
    }

    override suspend fun readDocsImg(): Result<List<DocImg>, DataError.Storage> {
        return dataSource.readDocsImg()
    }

    override suspend fun startObservingDocuments(
        onUpdateImg: () -> Unit,
        onUpdatePdf: () -> Unit
    ) {
        dataSource.startObservingDocuments(onUpdateImg, onUpdatePdf)
    }

    override suspend fun stopObservingDocuments() {
        dataSource.stopObservingDocuments()
    }

}