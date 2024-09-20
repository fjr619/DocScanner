package com.fjr.docscanner.presentation.util

import com.fjr.docscanner.R
import com.fjr.docscanner.domain.util.DataError

fun DataError.asUiText(): UiText {
    return when (this) {
        DataError.Storage.ERROR_READING -> UiText.StringResource(R.string.storage_read_doc_from_device_error_message)
        DataError.Storage.ERROR_SAVING -> UiText.StringResource(R.string.storage_save_doc_to_device_error_message)
    }
}