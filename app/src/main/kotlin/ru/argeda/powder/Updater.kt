package ru.argeda.powder

import android.app.Activity
import android.app.AlertDialog
import android.app.PendingIntent
import android.app.ProgressDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.widget.Toast
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Автообновление: при запуске игра смотрит, какая сборка последняя в Releases на GitHub,
 * и если она новее установленной, предлагает скачать и поставить её поверх.
 * Сборки подписаны одним ключом, поэтому сохранения остаются.
 */
object Updater {
    private const val REPO = "https://github.com/antennashopru-web/powder-and-banners"
    private const val APK = "PowderAndBanners.apk"
    private var asked = false
    /** для проверки на эмуляторе: считать, что установлена эта сборка (am start --ei oldbuild 1) */
    var pretend = 0L

    fun installed(a: Context): Long = if (pretend > 0) pretend else try {
        val pi = a.packageManager.getPackageInfo(a.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else @Suppress("DEPRECATION") pi.versionCode.toLong()
    } catch (e: Exception) { 0L }

    /** номер последней сборки: адрес /releases/latest перенаправляет на /releases/tag/build-N */
    private fun latest(): Long {
        val c = URL("$REPO/releases/latest").openConnection() as HttpURLConnection
        try {
            c.instanceFollowRedirects = false; c.connectTimeout = 8000; c.readTimeout = 8000
            c.requestMethod = "HEAD"
            val loc = c.getHeaderField("Location") ?: return 0
            return Regex("build-(\\d+)").find(loc)?.groupValues?.get(1)?.toLong() ?: 0
        } finally { c.disconnect() }
    }

    /** тихая проверка при запуске (manual = false) или по кнопке в меню (manual = true) */
    fun check(a: Activity, manual: Boolean) {
        if (!manual && asked) return
        asked = true
        Thread {
            val n = try { latest() } catch (e: Exception) { -1L }
            val cur = installed(a)
            a.runOnUiThread {
                if (a.isFinishing || a.isDestroyed) return@runOnUiThread
                when {
                    n > cur -> AlertDialog.Builder(a)
                        .setTitle("Вышла новая версия")
                        .setMessage("Доступна сборка $n, у вас стоит $cur. Обновить сейчас? Сохранения останутся.")
                        .setPositiveButton("Обновить") { _, _ -> download(a, n) }
                        .setNegativeButton("Позже", null).show()
                    manual && n < 0 -> Toast.makeText(a, "Не удалось проверить обновление: нет связи", Toast.LENGTH_LONG).show()
                    manual -> Toast.makeText(a, "У вас последняя версия (сборка $cur)", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    @Suppress("DEPRECATION")
    private fun download(a: Activity, n: Long) {
        val pd = ProgressDialog(a).apply {
            setTitle("Загрузка сборки $n"); setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            max = 100; setCancelable(false); show()
        }
        Thread {
            var err: String? = null
            val f = File(a.cacheDir, "update.apk")
            try {
                val c = URL("$REPO/releases/download/build-$n/$APK").openConnection() as HttpURLConnection
                c.connectTimeout = 10000; c.readTimeout = 20000
                if (c.responseCode != 200) throw RuntimeException("ответ сервера " + c.responseCode)
                val total = c.contentLengthLong
                c.inputStream.use { inp -> f.outputStream().use { out ->
                    val buf = ByteArray(65536); var got = 0L
                    while (true) {
                        val k = inp.read(buf); if (k < 0) break
                        out.write(buf, 0, k); got += k
                        if (total > 0) { val p = (got * 100 / total).toInt(); a.runOnUiThread { pd.progress = p } }
                    }
                } }
                install(a, f)
            } catch (e: Exception) { err = e.message ?: e.toString() }
            a.runOnUiThread {
                try { pd.dismiss() } catch (e: Exception) {}
                if (err != null) Toast.makeText(a, "Обновление не удалось: $err", Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    /** установка поверх через системный установщик: Android сам спросит подтверждение */
    private fun install(a: Activity, f: File) {
        val pi = a.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(a.packageName)
        val id = pi.createSession(params)
        pi.openSession(id).use { s ->
            s.openWrite("apk", 0, f.length()).use { out -> f.inputStream().use { it.copyTo(out) }; s.fsync(out) }
            val intent = Intent(a, UpdateReceiver::class.java)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            s.commit(PendingIntent.getBroadcast(a, id, intent, flags).intentSender)
        }
        f.delete()
    }
}

/** ответ системного установщика: показать окно подтверждения или сообщить об ошибке */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        when (i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION") val ask = i.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                ask.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try { c.startActivity(ask) } catch (e: Exception) { Toast.makeText(c, "Не удалось открыть установщик", Toast.LENGTH_LONG).show() }
            }
            PackageInstaller.STATUS_SUCCESS -> {}
            else -> Toast.makeText(c, "Обновление не установилось: " + (i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "ошибка"), Toast.LENGTH_LONG).show()
        }
    }
}
