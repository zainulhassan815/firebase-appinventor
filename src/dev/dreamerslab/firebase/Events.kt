package dev.dreamerslab.firebase

import android.os.Handler
import android.os.Looper
import com.google.android.gms.tasks.Task
import com.google.appinventor.components.runtime.Component
import com.google.appinventor.components.runtime.Form
import com.google.appinventor.components.runtime.util.ErrorMessages
import com.google.appinventor.components.runtime.util.OnInitializeListener

internal class Events(
    private val form: Form,
    private val component: Component,
    private val dispatchError: (operation: String, message: String) -> Boolean,
) : OnInitializeListener {

    private val main = Handler(Looper.getMainLooper())
    private val beforeInitialize = mutableListOf<Runnable>()

    var initialized = false
        private set

    init {
        form.registerForOnInitialize(this)
    }

    override fun onInitialize() {
        initialized = true
        beforeInitialize.forEach(main::post)
        beforeInitialize.clear()
    }

    fun fire(event: Runnable) {
        main.post {
            if (initialized) event.run() else beforeInitialize += event
        }
    }

    fun attempt(operation: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            fail(operation, e.message ?: e.toString())
        }
    }

    fun <T> request(operation: String, task: () -> Task<T>, done: (T) -> Unit) = attempt(operation) {
        task().addOnCompleteListener { result ->
            if (result.isSuccessful) done(result.result)
            else fail(operation, result.exception?.message ?: "$operation failed")
        }
    }

    fun fail(operation: String, message: String) = fire {
        if (!dispatchError(operation, message)) {
            form.ErrorOccurred(component, operation, ErrorMessages.ERROR_EXTENSION_ERROR, message)
        }
    }
}
