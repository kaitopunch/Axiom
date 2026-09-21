package com.duylt.demo.axiom.log

import com.axiom.log.AndroidAxiomLogger
import com.axiom.log.AxiomLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** One line Axiom logged, as the Lab screen shows it. */
data class LogLine(
    val at: Long,
    val level: AxiomLogger.Level,
    val text: String,
)

/**
 * An [AxiomLogger] that forwards to logcat *and* keeps the last [CAPACITY] lines in memory for the
 * Lab screen — the "wrap your own logger" case from axiom/README.md §4.4, with a ring buffer instead
 * of Timber. An `object` because Axiom is a process singleton and its logger is handed over at
 * `init`, before any DI container exists.
 */
object AxiomLogRecorder : AxiomLogger {
    private const val CAPACITY = 200

    private val delegate = AndroidAxiomLogger()

    private val _lines = MutableStateFlow<List<LogLine>>(emptyList())
    val lines: StateFlow<List<LogLine>> = _lines

    override fun log(
        level: AxiomLogger.Level,
        throwable: Throwable?,
        message: () -> String,
    ) {
        // Format once; the delegate gets the already-built string.
        val text = message()
        delegate.log(level, throwable) { text }
        val line = LogLine(System.currentTimeMillis(), level, throwable?.let { "$text — ${it.javaClass.simpleName}: ${it.message}" } ?: text)
        _lines.update { (it + line).takeLast(CAPACITY) }
    }

    fun clear() {
        _lines.value = emptyList()
    }
}
