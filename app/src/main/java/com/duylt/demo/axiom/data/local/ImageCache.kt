package com.duylt.demo.axiom.data.local

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.security.MessageDigest

/**
 * A file cache for the pictures a payload names, downloading through the OkHttp [Call.Factory] Axiom
 * hands a task via `callFactory("assets")` — the same connection pool as the API client, no key, no
 * logging.
 *
 * Small on purpose: the SDK deliberately does not cache images (axiom/README.md, "Axiom không làm"), so
 * this is the piece a consumer writes. `filesDir/img_cache/<namespace>/<sha1(url)>`.
 */
class ImageCache(
    context: Context,
    namespace: String,
    private val calls: Call.Factory,
) {
    private val dir: File = File(context.filesDir, "img_cache/$namespace")

    /** Which of [urls] are already on disk — a `stat` each, no network. */
    fun cachedPaths(urls: Collection<String>): Map<String, String?> =
        urls.associateWith { url -> fileFor(url).takeIf { it.length() > 0 }?.absolutePath }

    /**
     * Downloads every URL that is missing, [parallelism] at a time. A failed download maps to `null`:
     * the record keeps its remote URL and the next sync tries again, because the transform is what is
     * fingerprinted (axiom/README.md §10) and a value with one more path filled in is a new value.
     */
    suspend fun resolveAll(
        urls: Collection<String>,
        parallelism: Int = 6,
    ): Map<String, String?> =
        withContext(Dispatchers.IO) {
            dir.mkdirs()
            val gate = Semaphore(parallelism)
            coroutineScope {
                urls.distinct().map { url ->
                    async { url to gate.withPermit { download(url) } }
                }.map { it.await() }
            }.toMap()
        }

    /** Deletes cached files nothing in [keepPaths] points at any more. */
    fun prune(keepPaths: Set<String>) {
        dir.listFiles()?.forEach { file ->
            if (file.absolutePath !in keepPaths) file.delete()
        }
    }

    /** Wipes the namespace — the Lab's "Clear" on a task with pictures also drops the pictures. */
    fun clear() {
        dir.deleteRecursively()
    }

    private fun download(url: String): String? {
        val target = fileFor(url)
        if (target.length() > 0) return target.absolutePath
        return try {
            calls.newCall(Request.Builder().url(url).build()).execute().use { response ->
                val body = response.body
                if (!response.isSuccessful || body == null) {
                    Timber.w("Image %s → HTTP %d", url, response.code)
                    return null
                }
                val tmp = File(dir, "${target.name}.part")
                tmp.outputStream().use { out -> body.byteStream().copyTo(out) }
                if (!tmp.renameTo(target)) {
                    tmp.delete()
                    return null
                }
                target.absolutePath
            }
        } catch (e: Exception) {
            Timber.w(e, "Image %s failed", url)
            null
        }
    }

    private fun fileFor(url: String): File = File(dir, sha1(url))

    private fun sha1(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
}
