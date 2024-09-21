package com.fjr.docscanner.presentation.screens.scanner

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fjr.docscanner.R
import com.fjr.docscanner.domain.repository.ScannerRepository
import com.fjr.docscanner.domain.util.DataError
import com.fjr.docscanner.domain.util.Result
import com.fjr.docscanner.presentation.util.UiText
import com.fjr.docscanner.presentation.util.asUiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.annotation.KoinViewModel

@KoinViewModel
class ScannerViewModel(
    private val scannerRepository: ScannerRepository
) : ViewModel() {

    private val _scannerState = MutableStateFlow(ScannerContract.State())
    val scannerState = _scannerState.asStateFlow()

    private val eventChannel = Channel<ScannerContract.Event>()
    val events = eventChannel.receiveAsFlow()

    private fun <T> fetchDocuments(
        fetch: suspend () -> Result<T, DataError.Storage>,
        onSuccess: (T) -> Unit,
        onFailed: suspend (UiText) -> Unit, // it will be use with event
        onCompleted: () -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            // Update loading state
            _scannerState.update {
                it.copy(isLoading = true)
            }

            val result = withContext(Dispatchers.IO) { fetch() }

            // Update state based on result
            when (result) {
                is Result.Success -> onSuccess(result.data)
                is Result.Failed -> onFailed(result.error.asUiText())
            }

            // Call onCompleted callback and update loading state
            _scannerState.update {
                it.copy(isLoading = false)
            }

            withContext(Dispatchers.Main) {
                onCompleted()
            }

        }
    }

    fun readAllDocs(onCompleted: () -> Unit = {}) {
        fetchDocuments(
            fetch = {
                coroutineScope {
                    val pdfDocs = async { scannerRepository.readDocsPdf() }
                    val imgDocs = async { scannerRepository.readDocsImg() }
                    val pdfResult = pdfDocs.await()
                    val imgResult = imgDocs.await()

                    if (pdfResult is Result.Success && imgResult is Result.Success) {
                        println("== readAllDocs Success ${imgResult.data.size} ${pdfResult.data.size}")
                        Result.Success(imgResult.data to pdfResult.data)
                    } else {
                        println("== readAllDocs Failed")
                        Result.Failed(DataError.Storage.ERROR_READING)
                    }
                }

            },

            onSuccess = { data ->
                _scannerState.update {
                    it.copy(listDocsImg = data.first, listDocsPdf = data.second)
                }
            },

            onFailed = { uiText ->
                eventChannel.send(ScannerContract.Event.showToast(uiText))
            },

            onCompleted = onCompleted
        )
    }

    fun readDocsImg(onCompleted: () -> Unit = {}) {
        println("== readDocsImg")
        fetchDocuments(
            fetch = {
                coroutineScope {
                    val imgDocs = async { scannerRepository.readDocsImg() }
                    val imgResult = imgDocs.await()

                    if (imgResult is Result.Success) {
                        Result.Success(imgResult.data)
                    } else {
                        imgResult
                    }
                }
            },
            onSuccess = { data ->
                _scannerState.update {
                    it.copy(listDocsImg = data)
                }

                println("== readDocsImg size image docs ${scannerState.value.listDocsImg?.size}")
            },
            onFailed = { uiText ->
                eventChannel.send(ScannerContract.Event.showToast(uiText))
            },
            onCompleted = onCompleted
        )
    }

    fun readDocsPdf(onCompleted: () -> Unit = {}) {
        println("== readDocsPdf")
        fetchDocuments(
            fetch = {
                coroutineScope {
                    val pdfDocs = async { scannerRepository.readDocsPdf() }
                    val pdfResult = pdfDocs.await()

                    if (pdfResult is Result.Success) {
                        Result.Success(pdfResult.data)
                    } else {
                        pdfResult
                    }
                }
            },
            onSuccess = { data ->
                _scannerState.update {
                    it.copy(listDocsPdf = data)
                }

                println("== readDocsPdf size pdf docs ${scannerState.value.listDocsPdf?.size}")

            },
            onFailed = { uiText ->
                eventChannel.send(ScannerContract.Event.showToast(uiText))
            },
            onCompleted = onCompleted
        )
    }

    private fun <T> saveDocument(
        saveOperation: suspend () -> Result<T, DataError.Storage>,
        onSucceed: suspend (UiText) -> Unit,
        onFailed: suspend (UiText) -> Unit,
    ) {
        viewModelScope.launch {
            when(val result = saveOperation()) {
                is Result.Success -> onSucceed(UiText.StringResource(R.string.storage_save_doc_to_device_error_message))
                is Result.Failed ->  onFailed(result.error.asUiText())
            }
        }
    }

    fun saveDocPdf(pdfUri: Uri) {
        println("== saveDocPdf")
        saveDocument(
            saveOperation = {
                scannerRepository.saveDocPdf(pdfUri)
            },
            onSucceed = { uiText ->
                println("== saveDocPdf succeed")
                viewModelScope.launch {
                    eventChannel.send(ScannerContract.Event.showToast(uiText))
                }
               readDocsPdf {  }
            },
            onFailed = { uiText ->
                eventChannel.send(ScannerContract.Event.showToast(uiText))
            },
        )
    }

    fun saveDocImg(imgUri: Uri) {
        println("== saveDocImg")
        saveDocument(
            saveOperation = {
                scannerRepository.saveDocImg(imgUri)
            },
            onSucceed = { uiText ->
                println("== saveDocImg succeed")
                viewModelScope.launch {
                    eventChannel.send(ScannerContract.Event.showToast(uiText))
                }
                readDocsImg {  }
            },
            onFailed = { uiText ->
                eventChannel.send(ScannerContract.Event.showToast(uiText))
            },
        )
    }

//    fun saveDocPdf(pdfUri: Uri) {
//        viewModelScope.launch {
//            when (val result = scannerRepository.saveDocPdf(pdfUri)) {
//                is Result.Success -> {
//                    eventChannel.send(ScannerContract.Event.showToast(UiText.StringResource(R.string.storage_save_doc_to_device_error_message)))
//                }
//
//                is Result.Failed -> {
//                    eventChannel.send(ScannerContract.Event.showToast(result.error.asUiText()))
//                }
//            }
//        }
//    }

//    fun saveDocImg(imgUri: Uri) {
//        println("== saveDocImg2")
//        viewModelScope.launch {
//            when (val result = scannerRepository.saveDocImg(imgUri)) {
//                is Result.Success -> {
//                    println("== saveDocImg2 succed")
//                    eventChannel.send(ScannerContract.Event.showToast(UiText.StringResource(R.string.storage_save_doc_to_device_error_message)))
//                }
//
//                is Result.Failed -> {
//                    eventChannel.send(ScannerContract.Event.showToast(result.error.asUiText()))
//                }
//            }
//        }
//    }


//    fun readAllDocs(onCompleted: () -> Unit = {}) {
//        viewModelScope.launch(Dispatchers.IO) {
//            _scannerState.update {
//                it.copy(isLoading = true)
//            }
//
//            val pdfDocsDeferred = async(Dispatchers.IO) {
//                scannerRepository.readDocPdf()
//            }
//
//            val docsImgDeferred = async(Dispatchers.IO) {
//                scannerRepository.readDocsImg()
//            }
//
//
//            val resultDocsPdf = pdfDocsDeferred.await()
//            val resultDocsImg = docsImgDeferred.await()
//
//            when(resultDocsPdf) {
//                is Result.Success -> {
//                    _scannerState.update {
//                        it.copy(listDocsPdf = resultDocsPdf.data)
//                    }
//                }
//
//                is Result.Failed -> {
//
//                }
//            }
//
//            when(resultDocsImg) {
//                is Result.Success -> {
//                    _scannerState.update {
//                        it.copy(listDocsImg = resultDocsImg.data)
//                    }
//                }
//                is Result.Failed ->{
//
//                }
//            }
//
//            withContext(Dispatchers.Main) {
//                onCompleted()
//                _scannerState.update {
//                    it.copy(isLoading = false)
//                }
//            }
//        }
//    }

//    fun readDocsImg(onCompleted: () -> Unit = {}) {
//        viewModelScope.launch(Dispatchers.IO) {
//            _scannerState.update {
//                it.copy(isLoading = true)
//            }
//
//            val docsImgDeferred = async(Dispatchers.IO) {
//                scannerRepository.readDocsImg()
//            }
//
//
//            when(val resultDocsImg = docsImgDeferred.await()) {
//                is Result.Success -> {
//                    _scannerState.update {
//                        it.copy(listDocsImg = resultDocsImg.data)
//                    }
//                }
//                is Result.Failed ->{
//
//                }
//            }
//
//            withContext(Dispatchers.Main) {
//                onCompleted()
//                _scannerState.update {
//                    it.copy(isLoading = false)
//                }
//            }
//        }
//    }

//    fun readDocsPdf2(onCompleted: () -> Unit = {}) {
//        viewModelScope.launch(Dispatchers.IO) {
//            _scannerState.update {
//                it.copy(isLoading = true)
//            }
//
//            val pdfDocsDeferred = async(Dispatchers.IO) {
//                scannerRepository.readDocsPdf()
//            }
//
//
//            val resultDocsPdf = pdfDocsDeferred.await()
//
//            when(resultDocsPdf) {
//                is Result.Success -> {
//                    _scannerState.update {
//                        it.copy(listDocsPdf = resultDocsPdf.data)
//                    }
//                }
//
//                is Result.Failed -> {
//
//                }
//            }
//
//            withContext(Dispatchers.Main) {
//                onCompleted()
//                _scannerState.update {
//                    it.copy(isLoading = false)
//                }
//            }
//        }
//    }
}