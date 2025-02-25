package com.marketap.sdk.api.coroutine

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

class WorkerGroup(
    workerCount: Int = 5,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) {
    private val channel = Channel<suspend () -> Unit>()
    private val workers = List(workerCount) { createWorker() }

    fun dispatch(work: suspend () -> Unit): Boolean {
        return channel.trySend(work).isSuccess
    }

    fun start() {
        workers.forEach { it.start() }
    }

    private fun createWorker(): Job {
        return scope.launch {
            for (work in channel) {
                try {
                    work()
                } catch (e: Exception) {
                    Log.e("WorkerGroup", "Error in worker", e)
                }
            }
        }
    }
}