package com.fjr.docscanner.data.repository

import android.net.Uri
import com.fjr.docscanner.data.ImgDataSource
import com.fjr.docscanner.data.PdfDataSource
import com.fjr.docscanner.data.ScannerContentObserver
import com.fjr.docscanner.domain.model.DocImg
import com.fjr.docscanner.domain.model.DocPdf
import com.fjr.docscanner.domain.repository.ScannerRepository
import com.fjr.docscanner.domain.util.DataError
import com.fjr.docscanner.domain.util.EmptyResult
import com.fjr.docscanner.domain.util.Result
import kotlinx.coroutines.CoroutineScope
import org.koin.core.annotation.Factory

@Factory
class ScannerRepositoryImpl(
    private val imgdataSource: ImgDataSource,
    private val pdfDataSource: PdfDataSource,
    private val scannerContentObserver: ScannerContentObserver,
    private val coroutineScope: CoroutineScope
) : ScannerRepository {
    override suspend fun saveDocPdf(pdfUri: Uri): EmptyResult<DataError.Storage> {
        return pdfDataSource.saveDocPdf(pdfUri)
    }

    override suspend fun readDocsPdf(): Result<List<DocPdf>, DataError.Storage> {
        return pdfDataSource.readDocPdf()
    }

    override suspend fun saveDocImg(imageUri: Uri): EmptyResult<DataError.Storage> {
        return imgdataSource.saveDocImg(imageUri)
    }

    override suspend fun readDocsImg(): Result<List<DocImg>, DataError.Storage> {
        return imgdataSource.readDocsImg()
    }

    override suspend fun startObservingDocuments(
        onUpdateImg: () -> Unit,
        onUpdatePdf: () -> Unit
    ) {
        scannerContentObserver.startObservingDocuments(onUpdateImg, onUpdatePdf)
    }

    override suspend fun stopObservingDocuments() {
        scannerContentObserver.stopObservingDocuments()
    }

}