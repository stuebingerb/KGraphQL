package com.apurebase.kgraphql.dao

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

object CSVDataImporter {
    fun importFromCsv(inputStream: InputStream, consumer: (Array<String>) -> Unit) {
        val bufferedReader = BufferedReader(InputStreamReader(inputStream))
        return bufferedReader.useLines { lines ->
            lines.drop(1).forEach { line ->
                consumer(line.split("\\s*,\\s*".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray())
            }
        }
    }

}
