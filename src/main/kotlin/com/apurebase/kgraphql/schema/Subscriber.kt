package com.apurebase.kgraphql.schema

import com.fasterxml.jackson.databind.ObjectWriter
<<<<<<< HEAD

interface Subscriber {
    fun onSubscribe(subscription: Subscription)
=======
import java.util.concurrent.Flow

interface Subscriber {
    fun onSubscribe(subscription: Flow.Subscription)
>>>>>>> #10 Add Subscription support

    fun onNext(item: Any?)

    fun setArgs(args: Array<String>)

    fun onError(throwable: Throwable)

    fun onComplete()

    fun setObjectWriter(objectWriter: ObjectWriter)
}