package io.github.hyperboost.gamespace

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

/** Mendeteksi game terinstal: kategori GAME, flag IS_GAME, atau daftar paket populer. */
object GameDetector {

    private val KNOWN_GAMES = setOf(
        "com.mobile.legends",
        "com.tencent.ig",
        "com.pubg.imobile",
        "com.dts.freefireth",
        "com.dts.freefiremax",
        "com.miHoYo.GenshinImpact",
        "com.activision.callofduty.shooter",
        "com.supercell.brawlstars",
        "com.roblox.client"
    )

    @Suppress("DEPRECATION")
    fun findGames(ctx: Context): List<GameInfo> {
        val pm = ctx.packageManager
        return pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
            .asSequence()
            .filter { it.packageName != ctx.packageName }
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .filter {
                it.category == ApplicationInfo.CATEGORY_GAME ||
                    (it.flags and ApplicationInfo.FLAG_IS_GAME) != 0 ||
                    it.packageName in KNOWN_GAMES
            }
            .map { GameInfo(it.packageName, it.loadLabel(pm).toString(), it.loadIcon(pm)) }
            .sortedBy { it.label.lowercase() }
            .toList()
    }
}
