package com.axiom.task

import android.content.Context
import okhttp3.Call

/**
 * What a task's `fetch` sees: the named clients, the application context, and which attempt this is.
 *
 * Deliberately not the whole [com.axiom.Axiom] — a fetch that could call `sync` on another task, or
 * read another task's store, would be a dependency the registry cannot see.
 */
interface TaskScope {
    val context: Context

    /** The task's own key, for log lines. */
    val key: String

    /** 0 on the first attempt; WorkManager's `runAttemptCount` on a retry. */
    val attempt: Int

    fun <S : Any> api(
        client: String,
        service: Class<S>,
    ): S

    fun callFactory(client: String): Call.Factory
}

/** [TaskScope.api] with the service type inferred. */
inline fun <reified S : Any> TaskScope.api(client: String): S = api(client, S::class.java)

/**
 * What a task's `transform` sees, on top of [TaskScope]: a way to write an intermediate value.
 *
 * [publish] exists for the transform that takes a long time — downloading the images a payload names,
 * say. Publishing the rows first lets every collector of `data()` render them while the download runs,
 * and the value the transform finally returns replaces them. Without it a screen would wait on
 * pictures it can draw without.
 */
interface TransformScope<T : Any> : TaskScope {
    suspend fun publish(value: T)
}
