package com.fjr.docscanner.domain.util

sealed interface DataError : Error {
    sealed class Network(val message: String? = null) : DataError {
    }

    enum class Storage : DataError {
        ERROR_SAVING,
        ERROR_READING,
        ERROR_DELETING
    }
}