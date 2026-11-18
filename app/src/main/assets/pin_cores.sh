# Dijalankan sebagai root. @PKG@ diganti nama paket game.
# Helio G99: CPU 0-5 = Cortex-A55, CPU 6-7 = Cortex-A76  -> mask hex c0 = CPU6+CPU7
# Hanya thread utama & thread render/game yang di-pin, bukan seluruh thread,
# supaya worker thread game tetap bisa memakai 6 core kecil.
for p in $(pidof @PKG@); do
  taskset -p c0 $p
  for t in /proc/$p/task/*; do
    tid=${t##*/}
    name=$(cat $t/comm 2>/dev/null)
    case "$name" in
      RenderThread*|UnityMain*|UnityGfx*|GameThread*|RHIThread*|*Render*)
        taskset -p c0 $tid
        ;;
    esac
  done
done
