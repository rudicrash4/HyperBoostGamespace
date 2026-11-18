package io.github.hyperboost.gamespace

/** FPS game sebenarnya via `dumpsys SurfaceFlinger --latency` (root only). */
object RootFps {
    private var layer: String? = null

    fun reset() { layer = null }

    suspend fun sample(pkg: String): Int? {
        if (layer == null) {
            val list = ShellExecutor.su("dumpsys SurfaceFlinger --list").out.lines()
            layer = list.firstOrNull { it.contains(pkg) && it.startsWith("SurfaceView") }
                ?: list.firstOrNull { it.contains(pkg) && !it.startsWith("Background") }
        }
        val l = layer ?: return null
        val lines = ShellExecutor.su("dumpsys SurfaceFlinger --latency \"$l\"").out.lines()
        val ts = lines.drop(1).mapNotNull { line ->
            line.trim().split(Regex("\\s+")).takeIf { it.size >= 3 }?.get(1)?.toLongOrNull()
        }.filter { it > 0 && it != Long.MAX_VALUE }
        if (ts.isEmpty()) {
            layer = null // layer mungkin berganti
            return null
        }
        val last = ts.max()
        return ts.count { it > last - 1_000_000_000L }.coerceAtMost(240)
    }
}
