package com.hyperfiles.manager

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/** Thrown to unwind extraction when the user cancels. */
class ExtractCancelled : RuntimeException()

/**
 * Runs archive extraction in the background (app-scoped, survives the activity
 * that started it) and publishes overall progress to a LiveNotify ongoing
 * notification (with a Cancel action) and to any in-app listeners (ProgressPill).
 *
 * One extraction at a time. Cancelling aborts the job and deletes the partial
 * output folder.
 */
object ExtractionManager {

    enum class Status { RUNNING, DONE, FAILED, CANCELLED }

    /** progress is 0..100, or -1 for an indeterminate phase. */
    data class State(
        val name: String,
        val progress: Int,
        val status: Status,
        val destPath: String? = null,
        val error: String? = null
    )

    const val ACTION_CANCEL = "com.hyperfiles.manager.CANCEL_EXTRACTION"
    private const val NOTIF_ID = 4210

    @Volatile
    var state: State? = null
        private set

    @Volatile
    private var cancelRequested = false

    val isRunning: Boolean get() = state?.status == Status.RUNNING

    private val listeners = CopyOnWriteArrayList<(State?) -> Unit>()
    private val main = Handler(Looper.getMainLooper())

    fun addListener(l: (State?) -> Unit) { listeners.add(l); l(state) }
    fun removeListener(l: (State?) -> Unit) { listeners.remove(l) }

    private fun emit(s: State?) {
        state = s
        main.post { for (l in listeners) l(s) }
    }

    /** Request cancellation of the running extraction (no-op if idle). */
    fun cancel() { if (isRunning) cancelRequested = true }

    private fun cancelPendingIntent(app: Context): PendingIntent {
        val i = Intent(app, CancelExtractionReceiver::class.java).setAction(ACTION_CANCEL)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(app, 0, i, flags)
    }

    fun start(context: Context, archive: File, destParent: File) {
        val app = context.applicationContext
        if (isRunning) {
            main.post { Toast.makeText(app, "Already extracting ${state?.name}", Toast.LENGTH_SHORT).show() }
            return
        }
        cancelRequested = false
        val name = archive.name
        emit(State(name, -1, Status.RUNNING))
        val cancelPi = cancelPendingIntent(app)
        LiveNotify.start(app, NOTIF_ID, "Extracting $name", 0, cancelPi)
        main.post { Toast.makeText(app, "Extracting $name in the background…", Toast.LENGTH_SHORT).show() }

        Thread {
            var dest: File? = null
            val result = runCatching {
                val src = Readable.resolve(archive) ?: throw IllegalStateException(Readable.reason())
                val d = ArchiveEngine.writableExtractDir(destParent, ArchiveEngine.baseName(archive))
                dest = d
                ArchiveEngine.extractAll(
                    src, d,
                    onProgress = { p ->
                        emit(State(name, p, Status.RUNNING))
                        LiveNotify.update(app, NOTIF_ID, "Extracting $name", p.coerceAtLeast(0), 100, cancelPi)
                    },
                    isCancelled = { cancelRequested }
                )
                d
            }
            LiveNotify.finish(app, NOTIF_ID)
            val err = result.exceptionOrNull()
            when {
                result.isSuccess -> {
                    FileScanner.invalidate()
                    emit(State(name, 100, Status.DONE, destPath = dest?.absolutePath))
                }
                err is ExtractCancelled -> {
                    runCatching { dest?.deleteRecursively() }   // remove the partial output
                    FileScanner.invalidate()
                    emit(State(name, 0, Status.CANCELLED))
                    main.post { Toast.makeText(app, "Extraction canceled", Toast.LENGTH_SHORT).show() }
                }
                else -> emit(State(name, 0, Status.FAILED, error = err?.message ?: err?.javaClass?.simpleName))
            }
            cancelRequested = false
            // Let the terminal state linger briefly so the pill can show ✓ / canceled / failure, then clear.
            main.postDelayed({ if (state?.status != Status.RUNNING) emit(null) }, 5000)
        }.start()
    }
}
