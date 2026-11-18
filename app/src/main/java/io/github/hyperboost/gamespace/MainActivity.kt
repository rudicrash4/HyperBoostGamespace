package io.github.hyperboost.gamespace

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import io.github.hyperboost.gamespace.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private val adapter = GameAdapter { onGameSelected(it) }

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.rvGames.layoutManager = LinearLayoutManager(this)
        b.rvGames.adapter = adapter

        b.btnOverlayPerm.setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        }
        b.btnUsagePerm.setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        b.btnBoost.setOnClickListener { boostRam() }
        b.btnStopOverlay.setOnClickListener { OverlayService.stop(this) }

        notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        loadGames()
    }

    private fun refreshStatus() {
        val overlay = Settings.canDrawOverlays(this)
        val usage = UsageHelper.hasAccess(this)
        val root = when (ShellExecutor.rootCached) {
            true -> "aktif"
            false -> "tidak ada"
            null -> "belum dicek"
        }
        val temp = SystemStats.batteryTempC(this)
        val tempText = if (temp.isNaN()) "--" else "%.1f°C".format(temp)
        b.tvStatus.text = buildString {
            appendLine("RAM bebas: ${SystemStats.availMb(this@MainActivity)} / ${SystemStats.totalMb(this@MainActivity)} MB")
            appendLine("Suhu baterai: $tempText")
            append("Overlay: ${if (overlay) "✓" else "✗"}   Usage access: ${if (usage) "✓" else "✗"}   Root: $root")
        }
        b.btnOverlayPerm.isEnabled = !overlay
        b.btnUsagePerm.isEnabled = !usage
    }

    private fun loadGames() {
        lifecycleScope.launch {
            val games = withContext(Dispatchers.Default) { GameDetector.findGames(this@MainActivity) }
            adapter.submit(games)
            b.tvEmpty.visibility = if (games.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        }
    }

    private fun boostRam() {
        b.btnBoost.isEnabled = false
        lifecycleScope.launch {
            val r = BoosterEngine.cleanRam(this@MainActivity)
            Toast.makeText(
                this@MainActivity,
                "Memproses ${r.targeted} app • RAM bebas +${r.freedMb} MB",
                Toast.LENGTH_LONG
            ).show()
            refreshStatus()
            b.btnBoost.isEnabled = true
        }
    }

    private fun onGameSelected(game: GameInfo) {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Izinkan overlay dulu", Toast.LENGTH_LONG).show()
            b.btnOverlayPerm.performClick()
            return
        }
        lifecycleScope.launch {
            val r = BoosterEngine.boostForGame(this@MainActivity, game.packageName)
            Toast.makeText(
                this@MainActivity,
                "Boost: +${r.freedMb} MB" + if (r.rootApplied) " • Game Mode (root)" else "",
                Toast.LENGTH_SHORT
            ).show()
            OverlayService.start(this@MainActivity, game.packageName)
            packageManager.getLaunchIntentForPackage(game.packageName)?.let { startActivity(it) }
        }
    }
}
