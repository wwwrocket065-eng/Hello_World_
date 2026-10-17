package com.glassstudy.filament

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.concurrent.Executors

/**
 * Source-based, testable signed data updater. Never patches DEX or native code.
 * A complete patch is staged before the currently active version is changed.
 */
class PatchManager(private val context: Context) {
    companion object {
        private const val TAG = "GlassStudyUpdate"
        private const val FORMAT = "glassstudy-filament-assets-v2"
        private const val FEED_FORMAT = "glassstudy-filament-feed-v2"
        private const val BASE_VERSION = 20009
        private const val MAX_PATCH_BYTES = 50 * 1024 * 1024
        private const val MAX_ENTRY_BYTES = 16 * 1024 * 1024
        private const val MAX_ENTRIES = 120
        private const val UI_PATH = "ui/layout.json"
        private val MODEL_PATH = Regex("models/(faceted|tumbler|wine|mug|pitcher|bowl|vase|sphere|cube|cylinder|cone)_(low|medium|high)(_debug)?\\.glb")
        // Public key of the existing GlassStudy .pupdate signing identity. NOT a secret.
        private const val PUBLIC_KEY_BASE64 = "MIIBojANBgkqhkiG9w0BAQEFAAOCAY8AMIIBigKCAYEAyhk7Bexza7Ql/DBqcSPdWpl2JPWY3VvbaL5occPApXWIVeOLISsLPCckXgHGQ2mdwX6d6jwAuistLXRuwL9utYlBEENIwtH2FJoGohbQ7TqvPiM/KrFCR5TYw3s6z0xb/cjhXiBDxoLEb1c8bo6n36AM77bVOMd2sITODui/Dhx3lhjbCyJ/rc1FYHhkRNSw3aqYjxHVUtecAjNC2SLS95fRlRpb4tw709q1jmManXJunKJio2Rnay2O8LCOzJmQVHnWBaf+TSEh8dTijU5KpkRot5FUV4KXmCvJbeReS6nIblMFjpW4SlfBhoWW28Oh/k8UMCd+qE+Sg2T49CPm3vfyWl2idwe2NZ0/MAaEXiZxj3QIu/Xk9YQTheQtbJQq1aiGuOs/NPDAujkdqxRAMZtYH686JXw1bfIzrxYaa0wQ6ySIqGBj7E3zq4YzoVZW+1nDfzJEve40wq0CAx9nc2pBNXzkTkobBCuFIUlZ1X9T6nZ0Yti34IR58ovRe+rzAgMBAAE="
    }

    data class Candidate(val version: Int, val staging: File, val paths: Set<String>)
    data class Feed(val patchVersion: Int, val patchName: String?, val patchSha256: String?)

    private val prefs = context.getSharedPreferences("glassstudy.update.v2", Context.MODE_PRIVATE)
    private val patchRoot = File(context.filesDir, "signed_patches")
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    @Volatile var updateStatus: String = "Проверка ещё не выполнялась"
        private set
    @Volatile var serverPatchVersion: Int = 0
        private set

    fun lastConfirmedVersion(): Int = prefs.getInt("lastConfirmed", installedPatchVersion())

    /** Read local content:// uploads with an upper bound before decoding JSON. */
    fun installFromUri(
        uri: Uri,
        onCandidate: (Candidate) -> Unit,
        onError: (String) -> Unit
    ) {
        executor.execute {
            try {
                val data=context.contentResolver.openInputStream(uri)?.use { input ->
                    val output=java.io.ByteArrayOutputStream()
                    val buffer=ByteArray(16384)
                    while (true) {
                        val read=input.read(buffer)
                        if (read < 0) break
                        require(output.size()+read <= MAX_PATCH_BYTES) { "Размер .pupdate превышает лимит" }
                        output.write(buffer,0,read)
                    }
                    output.toByteArray()
                } ?: error("Файл недоступен для чтения")
                val candidate=stage(data)
                updateStatus="Патч ${candidate.version}: локальный файл и подпись проверены"
                main.post { onCandidate(candidate) }
            } catch (e: Exception) {
                updateStatus="Ошибка установки файла: ${e.message}"
                Log.w(TAG,"Signed local patch rejected",e)
                main.post { onError(e.message ?: "Файл или подпись не прошли проверку") }
            }
        }
    }

    fun installedPatchVersion(): Int {
        val v = prefs.getInt("active", BASE_VERSION)
        return if (v == BASE_VERSION || File(patchRoot, "v$v").isDirectory) v else BASE_VERSION
    }

    fun readModel(path: String): ByteArray {
        require(MODEL_PATH.matches(path)) { "Invalid model name" }
        val installed = File(patchRoot, "v${installedPatchVersion()}/$path")
        return if (installed.isFile) installed.readBytes() else context.assets.open(path).use { it.readBytes() }
    }

    fun readUiConfig(candidate: Candidate? = null): ByteArray {
        val file=if(candidate != null) File(candidate.staging,UI_PATH) else File(patchRoot,"v${installedPatchVersion()}/$UI_PATH")
        return if(file.isFile) file.readBytes()
            else if(candidate != null) readUiConfig()
            else context.assets.open(UI_PATH).use { it.readBytes() }
    }

    fun readModel(path: String, candidate: Candidate): ByteArray {
        require(MODEL_PATH.matches(path))
        val pending = File(candidate.staging, path)
        return if (pending.isFile) pending.readBytes() else readModel(path)
    }

    /**
     * Called before loading any model on startup. A patch that was activated but
     * never rendered is rolled back. Old resources remain available until confirmed.
     */
    fun reconcileAfterCrash() {
        val pending = prefs.getInt("pending",0)
        if (pending != 0) {
            val previous = prefs.getInt("previous", BASE_VERSION)
            prefs.edit().putInt("active",previous).remove("pending").commit()
            updateStatus="Откат патча $pending: запуск не был подтверждён. Активен $previous"
            Log.w(TAG,"Unconfirmed asset version $pending rolled back to $previous")
        } else {
            updateStatus="Активен патч ${installedPatchVersion()}; проверяем обновления"
        }
    }

    /** Called after the first successful Filament frame of a newly activated patch. */
    fun confirmFrameRendered() {
        val pending=prefs.getInt("pending",0)
        if (pending != 0 && pending == installedPatchVersion()) {
            // Persist an actual successful render confirmation, not just a downloaded version.
            if (!prefs.edit().remove("pending").putInt("lastConfirmed",pending).commit()) {
                updateStatus="Патч $pending: ошибка сохранения подтверждения"
                return
            }
            updateStatus="Патч $pending установлен и подтверждён отрисовкой"
            val previous=prefs.getInt("previous",BASE_VERSION)
            patchRoot.listFiles()?.filter {
                it.name.startsWith("staging_") ||
                (it.name.startsWith("v") && it.name != "v$pending" && it.name != "v$previous")
            }?.forEach { it.deleteRecursively() }
        }
    }

    /** HTTP is done off the main thread; UI callback only for verified, staged updates. */
    fun checkOnStart(onCandidate: (Candidate) -> Unit) {
        executor.execute {
            try {
                val base = BuildConfig.UPDATE_BASE_URL.trimEnd('/')
                val bytes = fetch("$base/latest.json", 1024 * 128)
                val j = JSONObject(String(bytes, StandardCharsets.UTF_8))
                require(j.getString("format") == FEED_FORMAT) { "Unrecognized update feed" }
                val feed = Feed(j.optInt("patchVersion", 0),
                    j.optString("patchName").takeIf { it.isNotBlank() },
                    j.optString("patchSha256").takeIf { it.isNotBlank() })
                serverPatchVersion=feed.patchVersion
                updateStatus=if(feed.patchVersion>installedPatchVersion())
                    "Найден патч ${feed.patchVersion}, загружаем"
                else "Обновлений нет: установлен ${installedPatchVersion()}, сервер ${feed.patchVersion}"
                // Never accept an arbitrary URL from the feed: only validated file names on this host.
                if (feed.patchVersion > installedPatchVersion() && feed.patchName == "GlassStudy_Filament_${feed.patchVersion}.pupdate") {
                    try {
                        require(feed.patchSha256?.matches(Regex("[a-f0-9]{64}")) == true)
                        val patch = fetch("$base/${feed.patchName}", MAX_PATCH_BYTES)
                        require(sha(patch) == feed.patchSha256) { "Patch download SHA mismatch" }
                        val candidate = stage(patch)
                        require(candidate.version == feed.patchVersion)
                        updateStatus="Патч ${candidate.version}: загрузка и подпись проверены, применяем"
                        main.post { onCandidate(candidate) }
                    } catch (e:Exception) {
                        updateStatus="Ошибка патча ${feed.patchVersion}: ${e.message}"
                        Log.w(TAG,"Rejected model patch, keeping existing assets",e)
                    }
                }
            } catch (e: Exception) {
                // Fail closed: do not replace a working model or interrupt app startup.
                updateStatus="Ошибка проверки обновлений: ${e.message}"
                Log.w(TAG,"Background update check failed; keeping installed assets",e)
            }
        }
    }

    private fun stage(bytes: ByteArray): Candidate {
        val j = JSONObject(String(bytes, StandardCharsets.UTF_8))
        require(j.getString("format") == FORMAT) { "Patch format mismatch" }
        val version = j.getInt("version")
        require(version > installedPatchVersion()) { "Downgrade or repeat update" }
        val entries = j.getJSONArray("entries")
        require(entries.length() in 1..MAX_ENTRIES) { "Invalid number of entries" }
        val records = mutableListOf<Triple<String,String,String>>()
        for (i in 0 until entries.length()) {
            val v = entries.getJSONObject(i)
            val path=v.getString("path")
            val hash=v.getString("sha256")
            require(MODEL_PATH.matches(path) || path==UI_PATH) { "Not a supported patch path: $path" }
            require(hash.matches(Regex("[a-f0-9]{64}")))
            records.add(Triple(path,hash,v.getString("data")))
        }
        require(records.map { it.first }.distinct().size == records.size) { "Duplicate model path" }
        // Key always signs only the stable manifest of allowed model paths + checksums.
        val canonical = canonicalMessage(version,records.map { Pair(it.first,it.second) })
        val signature = Signature.getInstance("SHA256withRSA")
        val pub=KeyFactory.getInstance("RSA").generatePublic(
            X509EncodedKeySpec(Base64.decode(PUBLIC_KEY_BASE64, Base64.DEFAULT)))
        signature.initVerify(pub)
        signature.update(canonical.toByteArray(StandardCharsets.UTF_8))
        require(signature.verify(Base64.decode(j.getString("signature"),Base64.DEFAULT))) { "Bad update signature" }

        patchRoot.mkdirs()
        val staging=File(patchRoot,"staging_${version}_${System.nanoTime()}")
        try {
            require(staging.mkdirs())
            // A .pupdate is incremental: carry forward previously verified assets,
            // otherwise a UI-only update would silently remove earlier model changes.
            val prior=File(patchRoot,"v${installedPatchVersion()}")
            if (prior.isDirectory) {
                prior.walkTopDown().filter { it.isFile }.forEach { source ->
                    val path=source.relativeTo(prior).invariantSeparatorsPath
                    require(MODEL_PATH.matches(path) || path==UI_PATH) { "Unknown carried patch path" }
                    val target=File(staging,path)
                    require(target.parentFile?.let { it.isDirectory || it.mkdirs() } == true)
                    source.copyTo(target,overwrite=false)
                }
            }
            var total=0L
            for ((path,hash,encoded) in records) {
                require(encoded.length <= MAX_ENTRY_BYTES * 4L / 3 + 8)
                val raw=Base64.decode(encoded, Base64.DEFAULT)
                require(raw.size in 1..MAX_ENTRY_BYTES)
                total += raw.size
                require(total <= MAX_PATCH_BYTES)
                require(sha(raw)==hash) { "Invalid SHA256: $path" }
                if(path==UI_PATH) UiLayout.parse(raw) // Reject malformed or non-declarative UI before activation.
                val f=File(staging,path)
                require(f.parentFile?.let { it.isDirectory || it.mkdirs() } == true)
                FileOutputStream(f).use { it.write(raw);it.fd.sync() }
            }
            require(staging.walkTopDown().filter { it.isFile }.sumOf { it.length() } <= MAX_PATCH_BYTES) { "Installed assets exceed limit" }
            return Candidate(version,staging,records.map { it.first }.toSet())
        } catch (e:Exception) { staging.deleteRecursively(); throw e }
    }

    /** Call ONLY after Filament successfully loads a model from staging. */
    fun activate(candidate: Candidate): Boolean {
        val current=installedPatchVersion()
        if (candidate.version <= current) {
            updateStatus="Патч ${candidate.version} не новее установленного $current"
            return false
        }
        val destination=File(patchRoot,"v${candidate.version}")
        if (destination.exists() || !candidate.staging.renameTo(destination)) {
            updateStatus="Патч ${candidate.version}: ошибка сохранения ресурсов"
            return false
        }
        // Synchronous commit ensures rollback state survives process death.
        if (!prefs.edit().putInt("previous",current).putInt("active",candidate.version)
                .putInt("pending",candidate.version).commit()) {
            updateStatus="Патч ${candidate.version}: ошибка фиксации установки"
            destination.deleteRecursively()
            return false
        }
        updateStatus="Патч ${candidate.version} установлен, ожидает подтверждения отрисовкой"
        return true
    }

    fun reject(candidate: Candidate) {
        candidate.staging.deleteRecursively()
        updateStatus="Патч ${candidate.version} отклонён, сохранён установленный ${installedPatchVersion()}"
    }

    private fun fetch(url: String, limit: Int): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            require(c.url.protocol == "https")
            c.connectTimeout=8_000;c.readTimeout=15_000;c.instanceFollowRedirects=false
            require(c.responseCode==200) { "HTTP ${c.responseCode}" }
            val stream=c.inputStream
            return stream.use {
                val out=java.io.ByteArrayOutputStream()
                val buf=ByteArray(16_384)
                while (true) {
                    val n=it.read(buf);if(n<0)break
                    require(out.size()+n <= limit) { "Update too large" }
                    out.write(buf,0,n)
                }
                out.toByteArray()
            }
        } finally { c.disconnect() }
    }
    private fun sha(data: ByteArray) = MessageDigest.getInstance("SHA-256").digest(data)
        .joinToString("") { "%02x".format(it) }
}

/** The exact same signed message format is used by tools/publish_patch.py. */
fun canonicalMessage(version: Int, hashes: List<Pair<String,String>>): String = buildString {
    append("glassstudy-filament-assets-v2\n")
    append(version).append('\n')
    hashes.sortedBy { it.first }.forEach { (name,hash) -> append(name).append('\t').append(hash).append('\n') }
}