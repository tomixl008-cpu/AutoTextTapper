package com.example.autotexttapper

import android.os.Handler
import android.os.Looper

/**
 * Simple observable singleton so MainActivity can reflect the accessibility
 * service's live automation status without any IPC/binding.
 */
object AutomationStatusHolder {

    @Volatile
    var currentStatus: String = "Idle"
        private set

    private val mainHandler = Handler(Looper.getMainLooper())
    private val listeners = mutableSetOf<(String) -> Unit>()

    fun addListener(listener: (String) -> Unit) {
        listeners.add(listener)
        listener(currentStatus)
    }

    fun removeListener(listener: (String) -> Unit) {
        listeners.remove(listener)
    }

    fun update(status: String) {
        currentStatus = status
        mainHandler.post {
            listeners.forEach { it(status) }
        }
    }
}
