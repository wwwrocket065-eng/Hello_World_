package com.glassstudy.filament

import android.app.Activity
import android.content.Context
import android.net.Uri
import java.net.HttpURLConnection
import java.net.URL
import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * Signed, data-only native Filament patches. No Java / DEX / native executable code.
 * Public key is safe to ship; the RSA private key stays OUTSIDE GitHub and APK.
 *
 * Format glassstudy-filament-assets-v1:
 * {format,version,entries:[{path,sha256,data}],signature}
 * Signature: RSA SHA256 PKCS1v1.5 over UTF-8:
 *   "GlassStudy-Filament-Patch-v1\n" + version + "\n" +
 *   each (path + "\n" + sha256 + "\n") in JSON entry order.
 */
class PatchManager(private val activity: Activity) {
    companion object {
        const val FORMAT = "glassstudy-filament-assets-v1"
        const val PREFIX = "GlassStudy_Filament_"
        private const val MAX_PATCH_BYTES = 32L * 1024 * 1024
        private const val MAX_TOTAL_BYTES = 28L * 1024 * 1024
        private const val MAX_ENTRY_BYTES = 16L * 1024 * 1024
        private const val MAX_FILES = 80
        private const val MIN_PATCH_VERSION = 20005L
        private const val PUBLIC_DER = "MIIBojANBgkqhkiG9w0BAQEFAAOCAY8AMIIBigKCAYEAyhk7Bexza7Ql/DBqcSPdWpl2JPWY3VvbaL5occPApXWIVeOLISsLPCckXgHGQ2mdwX6d6jwAuistLXRuwL9utYlBEENIwtH2FJoGohbQ7TqvPiM/KrFCR5TYw3s6z0xb/cjhXiBDxoLEb1c8bo6n36AM77bVOMd2sITODui/Dhx3lhjbCyJ/rc1FYHhkRNSw3aqYjxHVUtecAjNC2SLS95fRlRpb4tw709q1jmManXJunKJio2Rnay2O8LCOzJmQVHnWBaf+TSEh8dTijU5KpkRot5FUV4KXmCvJbeReS6nIblMFjpW4SlfBhoWW28Oh/k8UMCd+qE+Sg2T49CPm3vfyWl2idwe2NZ0/MAaEXiZxj3QIu/Xk9YQTheQtbJQq1aiGuOs/NPDAujkdqxRAMZtYH686JXw1bfIzrxYaa0wQ6ySIqGBj7E3zq4YzoVZW+1nDfzJEve40wq0CAx9nc2pBNXzkTkobBCuFIUlZ1X9T6nZ0Yti34IR58ovRe+rzAgMBAAE="
    }
    private val prefs = activity.getSharedPreferences("GlassStudySecurePatches", Context.MODE_PRIVATE)
    private val root = File(activity.filesDir, "patches")
    @Volatile var report: String = "Патчи: поиск не выполнялся"
        private set

    val version: Long
        get() = prefs.getLong("active", 0)


    fun asset(path: String): ByteArray? {
        if (!safePath(path)) return null
        val v = version
        if (v == 0L) return null
        val f = File(File(root, v.toString()), path)
        if (!f.isFile || f.length() > MAX_ENTRY_BYTES) return null
        return f.readBytes()
    }

    fun revert(reason: String) {
        val current = version
        val previous = prefs.getLong("previous", 0)
        prefs.edit().putLong("active", previous).putLong("blocked", current)
            .putLong("previous", 0).commit()
        report = "Патч " + current + " откачен: " + reason + ". Активен " + previous
    }

    fun confirmModelLoaded() {
        // A patch is considered healthy only after gltfio loads its GLB successfully.
        if (version > 0) {
            prefs.edit().putLong("healthy", version).apply()
        }
    }

    private fun safePath(p: String): Boolean {
        if (p == "studio_ibl.ktx") return true
        return Regex("^models/(faceted|tumbler|wine|mug|pitcher|bowl|vase|sphere|cube|cylinder|cone)_(low|medium|high)(_debug)?\\.glb$").matches(p)
    }

    private fun sha256(bytes: ByteArray): String {
        return MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    private fun verifySig(message: String, signature: String): Boolean {
        val key = KeyFactory.getInstance("RSA").generatePublic(
            X509EncodedKeySpec(Base64.decode(PUBLIC_DER, Base64.DEFAULT)))
        val sig = Signature.getInstance("SHA256withRSA")
        sig.initVerify(key)
        sig.update(message.toByteArray(Charsets.UTF_8))
        return sig.verify(Base64.decode(signature, Base64.DEFAULT))
    }

    private fun readLimited(input: InputStream, max: Long): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            require(total <= max) { "Файл превышает лимит размера" }
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    /**
     * All signature and SHA checks complete before changing the active asset version.
     * Temp dir + atomic rename + active pref commit allow rollback on process interruption.
     */
    @Synchronized
    fun install(bytes: ByteArray, sourceName: String): String {
        require(bytes.size <= MAX_PATCH_BYTES) { "Слишком большой патч" }
        val j = JSONObject(String(bytes, Charsets.UTF_8))
        require(j.optString("format") == FORMAT) { "Это не патч Filament" }
        val v = j.getLong("version")
        require(v >= MIN_PATCH_VERSION) { "Некорректная версия" }
        require(v > version) { "Уже установлена версия " + version }
        require(v != prefs.getLong("blocked", -1L)) { "Ранее откатили этот патч" }
        val entries = j.getJSONArray("entries")
        require(entries.length() in 1..MAX_FILES) { "Некорректное количество файлов" }
        val staged = ArrayList<Pair<String, ByteArray>>()
        val unique = HashSet<String>()
        val canonical = StringBuilder("GlassStudy-Filament-Patch-v1\n")
            .append(v).append('\n')
        var total = 0L
        for (i in 0 until entries.length()) {
            val e = entries.getJSONObject(i)
            val path = e.getString("path")
            require(safePath(path) && unique.add(path)) { "Недопустимый путь: " + path }
            val hash = e.getString("sha256")
            require(Regex("^[a-f0-9]{64}$").matches(hash)) { "Некорректный SHA256" }
            canonical.append(path).append('\n').append(hash).append('\n')
            val decoded = Base64.decode(e.getString("data"), Base64.DEFAULT)
            total += decoded.size
            require(decoded.isNotEmpty() && decoded.size <= MAX_ENTRY_BYTES && total <= MAX_TOTAL_BYTES) {
                "Некорректный размер вложения"
            }
            require(sha256(decoded) == hash) { "Неверная контрольная сумма: " + path }
            if (path.endsWith(".glb")) {
                require(decoded.size >= 12 && String(decoded, 0, 4, Charsets.US_ASCII) == "glTF") {
                    "Некорректный GLB"
                }
            }
            staged.add(path to decoded)
        }
        require(verifySig(canonical.toString(), j.getString("signature"))) {
            "Цифровая подпись не совпадает"
        }
        root.mkdirs()
        val temp = File(root, ".staging-" + v)
        if (temp.exists()) temp.deleteRecursively()
        require(temp.mkdirs()) { "Не удалось создать временную папку" }
        try {
            val prior = File(root, version.toString())
            if (version > 0 && prior.isDirectory) {
                prior.walkTopDown().filter { it.isFile }.forEach { old ->
                    val rel=old.relativeTo(prior).invariantSeparatorsPath
                    require(safePath(rel)) { "Повреждённый файл предыдущего патча" }
                    val dest=File(temp,rel)
                    dest.parentFile?.mkdirs()
                    old.copyTo(dest,overwrite=true)
                }
            }
            staged.forEach { (path, data) ->
                val target = File(temp, path)
                require(target.parentFile!!.mkdirs() || target.parentFile!!.exists())
                target.outputStream().use { it.write(data);it.fd.sync() }
            }
            require(temp.walkTopDown().filter { it.isFile }.sumOf { it.length() } <= MAX_TOTAL_BYTES) { "Слишком большая суммарная база патчей" }
            val target = File(root, v.toString())
            if (target.exists()) target.deleteRecursively()
            require(temp.renameTo(target)) { "Не удалось применить патч" }
            val old = version
            require(prefs.edit().putLong("previous", old).putLong("active", v)
                .putLong("healthy", 0).putString("source", sourceName).commit()) {
                "Не удалось сохранить активную версию"
            }
            report = "Установлен патч " + v + " из " + sourceName + "; файлов: " + staged.size
            return report
        } catch (t: Throwable) {
            temp.deleteRecursively()
            throw t
        }
    }

    /**
     * Scoped-storage safe automatic updates. Fetches only files from our fixed
     * repository. The downloaded payload is authenticated by the embedded RSA
     * signature before it can affect an asset on disk.
     * Run on a worker thread (never the Android UI thread).
     */
    fun onlinePatchVersion(): Long {
        val endpoint="https://raw.githubusercontent.com/wwwrocket065-eng/Hello_World_/glassstudy-filament-ci/glassstudy/updates/latest.json"
        val json=JSONObject(String(fetchHttps(endpoint, 32768L), Charsets.UTF_8))
        require(json.getString("format") == "glassstudy-filament-latest-v1") {
            "Неподходящий манифест обновлений"
        }
        return json.getLong("version")
    }

    fun checkAndInstallOnline(): String {
        val versionOnServer=onlinePatchVersion()
        if (versionOnServer <= version) {
            report="Обновлений нет; активный патч: " + version
            return report
        }
        require(versionOnServer >= MIN_PATCH_VERSION) { "Неверная версия на сервере" }
        val patchName=PREFIX + versionOnServer + ".pupdate"
        val url="https://raw.githubusercontent.com/wwwrocket065-eng/Hello_World_/glassstudy-filament-ci/glassstudy/updates/" + patchName
        return install(fetchHttps(url, MAX_PATCH_BYTES), patchName)
    }

    private fun fetchHttps(src: String, maxBytes: Long): ByteArray {
        require(src.startsWith("https://raw.githubusercontent.com/wwwrocket065-eng/Hello_World_/glassstudy-filament-ci/glassstudy/updates/"))
        val conn=(URL(src).openConnection() as HttpURLConnection)
        conn.connectTimeout=12000
        conn.readTimeout=12000
        conn.instanceFollowRedirects=false
        try {
            require(conn.responseCode==200) { "HTTP " + conn.responseCode }
            return conn.inputStream.use { readLimited(it,maxBytes) }
        } finally {
            conn.disconnect()
        }
    }

    fun installFromUri(uri: Uri): String {
        val stream = activity.contentResolver.openInputStream(uri)
            ?: error("Не удалось открыть файл")
        return stream.use { install(readLimited(it, MAX_PATCH_BYTES), uri.lastPathSegment ?: "выбранный файл") }
    }
}
