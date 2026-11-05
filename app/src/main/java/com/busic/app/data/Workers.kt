package com.busic.app.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

object Workers {
    val dispatcher: CoroutineDispatcher = Executors.newFixedThreadPool(4) { task ->
        Thread(task, "busic-io").apply { isDaemon = true }
    }.asCoroutineDispatcher()
}
