package com.hyperfiles.manager

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Runs archive extraction in the background (app-scoped, survives the activity
 * that started it) and publishes overall progress to:
 *   - a LiveNotify ongoing notification, and
 *   - any in-app listeners (the ProgressPill).
 *
 * One extraction at a time; a second request while busy is refused with a toast.
 */
object ExtractionManager {

    enum class Status { RUNNING, DONE, FAILED }

    /** progress is 0..100, or -1 for an indeterminate phase. */
    data class State(
        val name: String,
        val progress: Int,
        val status: Status,
        val destPath: String? = null,
        val error: String? = null
    )

    private const val NOTIF_ID = 4210

    @Volatile
    var state: State? = null
        private set

    val isRunning: Boolean get() = state?.status == Status.RUNNING

    private val listeners = CopyOnWriteArrayList<(State?) -> Unit>()
    private val main = Handler(Looper.getMainLooper())

    /** Register a listener; it's immediately invoked with the current state. */
    fun addListener(l: (State?) -> Unit) { listeners.add(l); l(state) }
    fun removeListener(l: (State?) -> Unit) { listeners.remove(l) }

    private fun emit(s: State?) {
        state = s
        main.post { for (l in listeners) l(s) }
    }

    fun start(context: Context, archive: File, destParent: File) {
        val app = context.applicationContext
        if (isRunning) {
            main.post { Toast.makeText(app, "Already extracting ${state?.name}", Toast.LENGTH_SHORT).show() }
            return
        }
        val name = archive.name
        emit(State(name, -1, Status.RUNNING))
        LiveNotify.start(app, NOTIF_ID, "Extracting $name")
        main.post { Toast.makeText(app, "Extracting $name in the background…", Toast.LENGTH_SHORT).show() }

        Thread {
            val result = runCatching {
                val src = Readable.resolve(archive) ?: throw IllegalStateException(Readable.reason())
                val dest = ArchiveEngine.writableExtractDir(destParent, ArchiveEngine.baseName(archive))
                ArchiveEngine.extractAll(src, dest) { p ->
                    emit(State(name, p, Status.RUNNING))
                    LiveNotify.update(app, NOTIF_ID, "Extracting $name", p.coerceAtLeast(0), 100)
                }
                dest
            }
            LiveNotify.finish(app, NOTIF_ID)
            result.onSuccess { dest ->
                FileScanner.invalidate()
                emit(State(name, 100, Status.DONE, destPath = dest.absolutePath))
            }.onFailure { e ->
                emit(State(name, 0, Status.FAILED, error = e.message ?: e.javaClass.simpleName))
            }
            // Let the terminal state linger briefly so the pill can show ✓ / failure, then clear.
            main.postDelayed({ if (state?.status != Status.RUNNING) emit(null) }, 5000)
        }.start()
    }
}
