package com.andloading.assistant.agent

import kotlinx.coroutines.CompletableDeferred

class ApprovalGate {
    @Volatile private var pending: CompletableDeferred<Boolean>? = null

    suspend fun awaitApproval(): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        pending = deferred
        return deferred.await()
    }

    fun resolve(approved: Boolean) {
        pending?.complete(approved)
        pending = null
    }
}
