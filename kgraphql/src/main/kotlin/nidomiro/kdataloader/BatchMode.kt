package nidomiro.kdataloader

sealed class BatchMode {
    /**
     * Load data in batches of [batchSize]
     */
    data class LoadInBatch(val batchSize: Int? = null) : BatchMode()

    /**
     * Load everything immediately
     */
    data object LoadImmediately : BatchMode()
}
