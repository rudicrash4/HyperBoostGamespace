package io.github.hyperboost.gamespace

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Performance Booster Engine.
 *
 *  - Tanpa root : membersihkan proses latar belakang (killBackgroundProcesses).
 *  - Dengan root: Game Mode "performance" + pin thread render/utama game ke Cortex-A76 (CPU 6-7).
 */
object BoosterEngine {

    data class BoostResult(val targeted: Int, val freedMb: Long, val rootApplied: Boolean)

    private val PKG_REGEX = Regex("^[A-Za-z0-9._]+$")

    suspend fun cleanRam(ctx: Context, keep: Set<String> = emptySet()): BoostResult =
        withContext(Dispatchers.Default) {
            val am = ctx.getSystemService(ActivityManager::class.java)
            val before = SystemStats.availMb(ctx)

            val protectedPkgs = keep + ctx.packageName + setOfNotNull(
                defaultLauncher(ctx), defaultKeyboard(ctx)
            )

            var targeted = 0
            for (pkg in userApps(ctx)) {
                if (pkg in protectedPkgs) continue
                am.killBackgroundProcesses(pkg)
                targeted++
            }
            delay(700) // beri waktu kernel/LMKD melepas memori
            val after = SystemStats.availMb(ctx)
            BoostResult(targeted, (after - before).coerceAtLeast(0), false)
        }

    /** Dipanggil sebelum game diluncurkan. */
    suspend fun boostForGame(ctx: Context, pkg: String): BoostResult {
        val ram = cleanRam(ctx, setOf(pkg))
        val root = ShellExecutor.hasRoot()
        if (root && PKG_REGEX.matches(pkg)) {
            // Game Mode performance (best-effort; sintaks berbeda antar versi Android/One UI)
            ShellExecutor.su("cmd game mode performance $pkg || cmd game set --mode 2 $pkg")
        }
        return ram.copy(rootApplied = root)
    }

    /** Root only: pin thread utama + render ke inti performa. Panggil berkala selagi game jalan. */
    suspend fun pinPerfThreads(ctx: Context, pkg: String): Boolean {
        if (!PKG_REGEX.matches(pkg)) return false
        val script = ctx.assets.open("pin_cores.sh").bufferedReader().use { it.readText() }
            .replace("@PKG@", pkg)
        return ShellExecutor.su(script).ok
    }

    @Suppress("DEPRECATION")
    private fun userApps(ctx: Context): List<String> =
        ctx.packageManager.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
            .map { it.packageName }

    private fun defaultLauncher(ctx: Context): String? {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return ctx.packageManager
            .resolveActivity(
                i, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
            )?.activityInfo?.packageName
    }

    private fun defaultKeyboard(ctx: Context): String? =
        Settings.Secure.getString(ctx.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.substringBefore('/')
}
