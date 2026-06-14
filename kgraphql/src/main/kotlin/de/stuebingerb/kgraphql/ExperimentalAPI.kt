package de.stuebingerb.kgraphql

@RequiresOptIn(
    message = "This API is experimental. It could change in the future without notice.",
    level = RequiresOptIn.Level.WARNING
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class ExperimentalAPI
