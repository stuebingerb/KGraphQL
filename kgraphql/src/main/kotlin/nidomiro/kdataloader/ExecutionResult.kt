package nidomiro.kdataloader

sealed class ExecutionResult<out T> {
    data class Success<out T>(val value: T) : ExecutionResult<T>()
    data class Failure(val throwable: Throwable) : ExecutionResult<Nothing>()
}
