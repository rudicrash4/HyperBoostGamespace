package io.github.hyperboost.gamespace

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Menjalankan skrip lewat `su` (opsional, hanya jika perangkat di-root). */
object ShellExecutor {

    data class Result(val ok: Boolean, val out: String)

    /** null = belum dicek, true = root tersedia, false = tidak ada root. */
    @Volatile
    var rootCached: Boolean? = null
        private set

    suspend fun su(script: String): Result = withContext(Dispatchers.IO) {
        try {
            val p = ProcessBuilder("su").redirectErrorStream(true).start()
            p.outputStream.bufferedWriter().use {
                it.write(script)
                it.write("\nexit\n")
            }
            val out = p.inputStream.bufferedReader().readText()
            Result(p.waitFor() == 0, out.trim())
        } catch (e: Exception) {
            Result(false, e.message ?: "")
        }
    }

    suspend fun hasRoot(): Boolean {
        rootCached?.let { return it }
        val r = su("id")
        val ok = r.ok && r.out.contains("uid=0")
        rootCached = ok
        return ok
    }
}
