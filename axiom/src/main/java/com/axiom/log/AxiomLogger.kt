package com.axiom.log

import android.util.Log

/**
 * Where Axiom writes its own log lines. The SDK never depends on Timber or any logging library; a host
 * that has one wraps it in a few lines.
 *
 * Messages are lambdas so a silenced level costs nothing to format.
 */
interface AxiomLogger {
    enum class Level { DEBUG, INFO, WARN, ERROR }

    fun log(
        level: Level,
        throwable: Throwable? = null,
        message: () -> String,
    )

    companion object {
        /** Drops everything. */
        val NONE: AxiomLogger =
            object : AxiomLogger {
                override fun log(
                    level: Level,
                    throwable: Throwable?,
                    message: () -> String,
                ) = Unit
            }
    }
}

fun AxiomLogger.debug(message: () -> String) = log(AxiomLogger.Level.DEBUG, null, message)

fun AxiomLogger.info(message: () -> String) = log(AxiomLogger.Level.INFO, null, message)

fun AxiomLogger.warn(
    throwable: Throwable? = null,
    message: () -> String,
) = log(AxiomLogger.Level.WARN, throwable, message)

fun AxiomLogger.error(
    throwable: Throwable? = null,
    message: () -> String,
) = log(AxiomLogger.Level.ERROR, throwable, message)

/** Logcat, under one tag. [minLevel] gates what is printed at all. */
class AndroidAxiomLogger(
    private val tag: String = "Axiom",
    private val minLevel: AxiomLogger.Level = AxiomLogger.Level.DEBUG,
) : AxiomLogger {
    override fun log(
        level: AxiomLogger.Level,
        throwable: Throwable?,
        message: () -> String,
    ) {
        if (level < minLevel) return
        val text = message()
        when (level) {
            AxiomLogger.Level.DEBUG -> Log.d(tag, text, throwable)
            AxiomLogger.Level.INFO -> Log.i(tag, text, throwable)
            AxiomLogger.Level.WARN -> Log.w(tag, text, throwable)
            AxiomLogger.Level.ERROR -> Log.e(tag, text, throwable)
        }
    }
}
