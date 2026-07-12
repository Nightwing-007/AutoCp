package com.github.pushpavel.autocp.submit

import com.github.pushpavel.autocp.common.res.R
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Bridge between the "CPH-NG Submit" browser extension and AutoCp.
 * Application-level service to ensure only one [SubmitServer] is running.
 *
 * Must be started before [com.github.pushpavel.autocp.gather.base.ProblemGatheringBridge]
 * so it wins the shared port before the gathering server falls back to it.
 */
@Service(Service.Level.APP)
class SubmitBridge : Disposable {

    @Volatile
    private var server: SubmitServer? = null

    @Volatile
    private var startAttempted = false

    companion object {
        fun getInstance(): SubmitBridge {
            return ApplicationManager.getApplication().getService(SubmitBridge::class.java)
        }
    }

    suspend fun start() {
        if (startAttempted) return
        startAttempted = true

        val server = SubmitServer(R.others.submitServerPort)
        val bound = withTimeoutOrNull(5000) { server.startAsync().await() } ?: false
        if (bound) {
            this.server = server
        } else {
            server.stop()
            R.notify.submitServerPortTaken(R.others.submitServerPort)
        }
    }

    val isBrowserConnected get() = server?.isBrowserConnected == true

    fun submit(data: SubmitData): Boolean = server?.submit(data) ?: false

    override fun dispose() {
        server?.stop()
        server = null
    }
}
