package io.github.hyperboost.gamespace

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.IBinder
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** In-Game Floating Assistant: HUD FPS / suhu / RAM + tombol pembersih RAM. */
class OverlayService : Service() {

    companion object {
        const val ACTION_START = "io.github.hyperboost.gamespace.START"
        const val ACTION_STOP = "io.github.hyperboost.gamespace.STOP"
        const val EXTRA_PKG = "pkg"
        private const val CHANNEL_ID = "hud"
        private const val NOTIF_ID = 1001

        fun start(ctx: Context, gamePkg: String?) {
            val i = Intent(ctx, OverlayService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_PKG, gamePkg)
            ContextCompat.startForegroundService(ctx, i)
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, OverlayService::class.java))
        }
    }

    private lateinit var wm: WindowManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val fps = FpsMonitor()

    private var hud: View? = null
    private var tvFps: TextView? = null
    private var tvTemp: TextView? = null
    private var tvRam: TextView? = null

    private var targetPkg: String? = null
    private var rootMode = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        targetPkg = intent?.getStringExtra(EXTRA_PKG)

        ServiceCompat.startForeground(
            this, NOTIF_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )

        if (hud == null) {
            if (!Settings.canDrawOverlays(this)) {
                stopSelf()
                return START_NOT_STICKY
            }
            wm = getSystemService(WindowManager::class.java)
            addHud()
            startLoops()
        }
        return START_NOT_STICKY
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "HUD", NotificationManager.IMPORTANCE_LOW)
        )
        val stopPi = PendingIntent.getService(
            this, 0,
            Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bolt)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("HUD overlay aktif")
            .setOngoing(true)
            .addAction(0, "Stop", stopPi)
            .build()
    }

    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    private fun addHud() {
        val view = LayoutInflater.from(this).inflate(R.layout.overlay_hud, null)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 120
            alpha = 0.8f
        }

        tvFps = view.findViewById(R.id.tvFps)
        tvTemp = view.findViewById(R.id.tvTemp)
        tvRam = view.findViewById(R.id.tvRam)

        // Geser panel dengan menyeret judul
        view.findViewById<View>(R.id.tvHandle).setOnTouchListener(object : View.OnTouchListener {
            var startX = 0
            var startY = 0
            var touchX = 0f
            var touchY = 0f
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = lp.x; startY = lp.y
                        touchX = e.rawX; touchY = e.rawY
                    }
                    MotionEvent.ACTION_MOVE -> {
                        lp.x = startX + (e.rawX - touchX).toInt()
                        lp.y = startY + (e.rawY - touchY).toInt()
                        wm.updateViewLayout(view, lp)
                    }
                }
                return true
            }
        })

        view.findViewById<View>(R.id.btnRam).setOnClickListener {
            scope.launch {
                val r = BoosterEngine.cleanRam(this@OverlayService, setOfNotNull(targetPkg))
                Toast.makeText(
                    this@OverlayService, "RAM bebas +${r.freedMb} MB", Toast.LENGTH_SHORT
                ).show()
            }
        }
        view.findViewById<View>(R.id.btnClose).setOnClickListener { stopSelf() }

        wm.addView(view, lp)
        hud = view
    }

    private fun startLoops() {
        fps.start()
        scope.launch {
            rootMode = ShellExecutor.hasRoot()
            var tick = 0
            var lastTs = SystemClock.elapsedRealtime()

            while (isActive) {
                val pkg = targetPkg

                if (tick % 2 == 0) updateVisibility(pkg)

                val now = SystemClock.elapsedRealtime()
                val dt = (now - lastTs).coerceAtLeast(1) / 1000f
                lastTs = now
                val displayHz = (fps.consume() / dt).roundToInt()

                val fpsText = if (rootMode && pkg != null) {
                    "FPS  " + (RootFps.sample(pkg)?.toString() ?: "--")
                } else {
                    "Layar  $displayHz Hz"
                }
                tvFps?.text = fpsText

                val temp = SystemStats.batteryTempC(this@OverlayService)
                tvTemp?.text = if (temp.isNaN()) "Suhu  --" else "Suhu  %.1f°C".format(temp)
                tvRam?.text = "RAM  ${SystemStats.availMb(this@OverlayService)} MB bebas"

                if (rootMode && pkg != null && tick % 10 == 3) {
                    launch { BoosterEngine.pinPerfThreads(this@OverlayService, pkg) }
                }

                tick++
                delay(1000)
            }
        }
    }

    /** Sembunyikan HUD saat game bukan aplikasi foreground (butuh Usage Access). */
    private suspend fun updateVisibility(pkg: String?) {
        if (pkg == null || !UsageHelper.hasAccess(this)) return
        val fg = withContext(Dispatchers.Default) {
            UsageHelper.foregroundPackage(this@OverlayService)
        } ?: return
        hud?.visibility = if (fg == pkg) View.VISIBLE else View.GONE
    }

    override fun onDestroy() {
        scope.cancel()
        fps.stop()
        hud?.let { runCatching { wm.removeView(it) } }
        hud = null
        super.onDestroy()
    }
}
