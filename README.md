# ⚡ HyperBoost Game Space

Aplikasi Android open source: game launcher + booster + overlay HUD, dioptimalkan untuk
Samsung Galaxy A07 (Helio G99, Mali-G57 MC2, RAM 4GB, Android 16 / API 36).

## Fitur & batasan jujur

| Fitur | Tanpa root | Dengan root |
|---|---|---|
| Bersihkan RAM | `killBackgroundProcesses` untuk app user non-sistem | sama |
| Deteksi game | kategori GAME / flag IS_GAME / daftar paket populer | sama |
| HUD overlay | `TYPE_APPLICATION_OVERLAY`, suhu baterai, RAM bebas | sama |
| FPS | **refresh display** (bukan FPS game) | FPS game via `dumpsys SurfaceFlinger --latency` |
| Prioritas ke Cortex-A76 | tidak bisa (Android memblokir affinity proses lain) | `taskset` thread utama/render ke CPU 6-7 |
| Mode performa game | tidak bisa | `cmd game mode performance <pkg>` |

Catatan: semua app Android sudah memakai akselerasi hardware (HWUI/GPU) secara default;
tidak ada "paksa GPU" yang berarti untuk game. Panel overlay sendiri memakai
`FLAG_HARDWARE_ACCELERATED`. Pemetaan core (6 x A55 = CPU 0-5, 2 x A76 = CPU 6-7)
sebaiknya diverifikasi di perangkat: `cat /sys/devices/system/cpu/cpu*/cpufreq/cpuinfo_max_freq`.

## Build

GitHub Actions (`.github/workflows/build_apk.yml`) otomatis build setiap push.
Lokal: `gradle wrapper --gradle-version 8.11.1 && ./gradlew assembleDebug` (JDK 17).

## Izin yang harus diberikan manual
1. Display over other apps (Overlay)
2. Usage access (menyembunyikan HUD saat bukan di game)
3. Notifikasi (Android 13+)
