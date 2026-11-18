package io.github.hyperboost.gamespace

import android.view.Choreographer

/**
 * Penghitung frame display via Choreographer (tanpa root).
 * CATATAN: ini mengukur refresh display, BUKAN FPS internal game.
 * Untuk FPS game yang sebenarnya gunakan RootFps (butuh root).
 */
class FpsMonitor : Choreographer.FrameCallback {
    private var frames = 0
    private var running = false

    fun start() {
        if (running) return
        running = true
        Choreographer.getInstance().postFrameCallback(this)
    }

    fun stop() {
        running = false
        Choreographer.getInstance().removeFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        frames++
        Choreographer.getInstance().postFrameCallback(this)
    }

    fun consume(): Int {
        val f = frames
        frames = 0
        return f
    }
}
