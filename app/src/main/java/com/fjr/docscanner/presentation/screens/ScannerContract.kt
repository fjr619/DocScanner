package com.fjr.docscanner.presentation.screens

import com.fjr.docscanner.domain.model.DocImg
import com.fjr.docscanner.domain.model.DocPdf
import com.fjr.docscanner.presentation.util.UiText

class ScannerContract {

    sealed interface Action {

    }

    sealed interface Event {
        data class showToast(val uiText: UiText): Event
    }

    data class State(
        val listDocsImg: List<DocImg>? = null,
        val listDocsPdf: List<DocPdf>? = null,
        val isLoading: Boolean = false
    )
}