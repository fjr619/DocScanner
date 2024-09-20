package com.fjr.docscanner.domain.util

sealed interface Result<out D, out E> {
    data class Success<out D>(val data : D) : Result<D, Nothing>
    data class Failed<out E : Error>(val error : E) : Result<Nothing, E>
}

inline fun <T, E : Error, R> Result<T, E>.map(map: (T) -> R) : Result<R, E> {
    return when(this) {
        is Result.Failed -> Result.Failed(error)
        is Result.Success -> Result.Success(map(data))
    }
}
fun <T, E: Error> Result<T, E>.asEmptyDataResult(): EmptyResult<E> {
    return map {  }
}

typealias EmptyResult<E> = Result<Unit, E>