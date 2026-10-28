package br.com.lume.launcher

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.os.Bundle
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.FileVisitResult
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Maintenance is explicit and headless. Never starts an Activity, changes Home or uses shell/root. */
open class DeviceStateInstrumentation : Instrumentation() {
    private var options = Bundle()

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        options = arguments ?: Bundle()
        start()
    }

    override fun onStart() {
        var count = 0
        var digest = ""
        var action = "invalid"
        try {
            val requested = options.getString("action") ?: error("Missing action")
            require(requested in setOf("backup", "verify", "restore", "selftest", "export")) { "Invalid action" }
            action = requested
            if (action == "selftest") {
                // Use the target's runtime with an exclusive UUID preference file, not its orbita file.
                check(context.packageName == "br.com.lume.launcher.test" && targetContext.packageName == "br.com.lume.launcher" &&
                    targetContext.applicationInfo.uid == android.os.Process.myUid()) { "Selftest requires the launcher UID" }
                val tested = PreferenceStateSnapshot.selfTest(targetContext)
                count = tested.getInt("entries")
                digest = tested.getString("digest")
                finish(Activity.RESULT_OK, result("selftest:ok", count, digest))
                return
            }
            if (action == "export") {
                finish(Activity.RESULT_OK, exportArchive())
                return
            }
            val id = options.getString("snapshot") ?: error("Missing snapshot")
            require(id.matches(Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,63}"))) { "Invalid snapshot ID" }
            val external = requireNotNull(targetContext.getExternalFilesDir(null)) { "External files unavailable" }.canonicalFile
            val root = File(external, "maintenance-state").canonicalFile
            check(root.toPath().startsWith(external.toPath()) && !Files.isSymbolicLink(root.toPath())) { "Invalid backup root" }
            check(root.isDirectory || root.mkdirs()) { "Backup root unavailable" }
            val directory = File(root, id)
            check(directory.canonicalFile.parentFile == root && !Files.isSymbolicLink(directory.toPath())) { "Invalid snapshot location" }
            val snapshot = if (action == "backup") backup(directory) else readJournal(directory)
            count = snapshot.getJSONArray("entries").length()
            digest = PreferenceStateSnapshot.digest(snapshot)
            when (action) {
                "backup" -> check(PreferenceStateSnapshot.matches(targetContext, snapshot)) { "Preferences changed during backup" }
                "verify" -> check(PreferenceStateSnapshot.matches(targetContext, snapshot)) { "Preferences differ" }
                "restore" -> PreferenceStateSnapshot.write(targetContext, snapshot)
            }
            finish(Activity.RESULT_OK, result("$action:ok", count, digest))
        } catch (_: Exception) {
            // Errors never echo values or paths. Only explicit export returns its private Base64 payload.
            finish(Activity.RESULT_CANCELED, result("$action:error", count, digest))
        }
    }

    private fun result(status: String, count: Int, digest: String) = Bundle().apply {
        putString("result", status)
        putInt("entries", count)
        putString("digest", digest)
    }

    private fun exportArchive(): Bundle {
        val snapshotId = options.getString("snapshot")
        val outputId = options.getString("output")
        require((snapshotId != null) != (outputId != null)) { "Choose exactly one export source" }
        val id = snapshotId ?: requireNotNull(outputId)
        require(id.matches(Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,63}"))) { "Invalid export ID" }
        val external = requireNotNull(targetContext.getExternalFilesDir(null)) { "External files unavailable" }.canonicalFile
        val parent = if (snapshotId != null) {
            val root = File(external, "maintenance-state")
            check(!Files.isSymbolicLink(root.toPath()) && root.canonicalFile.parentFile == external && root.isDirectory) { "Invalid snapshot root" }
            root.canonicalFile
        } else {
            // A generic output export must not bypass snapshot journal validation or export all baselines.
            require(id != "maintenance-state") { "Use the snapshot parameter for baselines" }
            external
        }
        val directory = File(parent, id)
        check(directory.isDirectory && !Files.isSymbolicLink(directory.toPath()) && directory.canonicalFile.parentFile == parent) { "Invalid export directory" }
        val snapshot = if (snapshotId != null) readJournal(directory) else null
        val archive = zipDirectory(directory)
        val archiveDigest = MessageDigest.getInstance("SHA-256").digest(archive)
            .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
        return result("export:ok", snapshot?.getJSONArray("entries")?.length() ?: 0,
            snapshot?.let { PreferenceStateSnapshot.digest(it) } ?: "").apply {
            putInt("archive_bytes", archive.size)
            putString("archive_sha256", archiveDigest)
            // Single bounded payload; caller must redirect instrumentation output to private host storage.
            putString("archive_base64", Base64.encodeToString(archive, Base64.NO_WRAP))
        }
    }

    private fun zipDirectory(directory: File): ByteArray {
        val root = directory.canonicalFile.toPath()
        val files = mutableListOf<Path>()
        var advertisedBytes = 0L
        var directories = 0
        Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                check(++directories <= 4096 && !Files.isSymbolicLink(dir) && dir.toRealPath().startsWith(root)) { "Invalid export tree" }
                return FileVisitResult.CONTINUE
            }
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                check(attrs.isRegularFile && !Files.isSymbolicLink(file) && file.toRealPath().startsWith(root)) { "Invalid export file" }
                check(files.size < 4096 && attrs.size() <= MAX_EXPORT_RAW - advertisedBytes) { "Export exceeds raw limit" }
                advertisedBytes += attrs.size()
                files.add(file)
                return FileVisitResult.CONTINUE
            }
        })
        check(files.isNotEmpty()) { "Export is empty" }
        val bytes = object : ByteArrayOutputStream(8192) {
            override fun write(value: Int) {
                check(size() < MAX_EXPORT_ZIP) { "Export exceeds compressed limit" }
                super.write(value)
            }
            override fun write(buffer: ByteArray, offset: Int, length: Int) {
                check(length <= MAX_EXPORT_ZIP - size()) { "Export exceeds compressed limit" }
                super.write(buffer, offset, length)
            }
        }
        var actualBytes = 0L
        ZipOutputStream(bytes).use { zip ->
            files.sortedBy { root.relativize(it).toString() }.forEach { file ->
                val name = root.relativize(file).toString().replace(File.separatorChar, '/')
                check(name.split('/').all { part -> part.isNotEmpty() && part != "." && part != ".." &&
                    part.none { it.isISOControl() || it in "\\:<>\"|?*" } }) { "Invalid archive path" }
                check(!Files.isSymbolicLink(file) && file.toRealPath().startsWith(root)) { "Export source changed" }
                zip.putNextEntry(ZipEntry(name).apply { time = 0L })
                Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS).use { input ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        check(read.toLong() <= MAX_EXPORT_RAW - actualBytes) { "Export exceeds raw limit" }
                        actualBytes += read
                        zip.write(buffer, 0, read)
                    }
                }
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    private companion object {
        const val MAX_EXPORT_ZIP = 192 * 1024
        const val MAX_EXPORT_RAW = 8L * 1024 * 1024
    }

    private fun backup(directory: File): JSONObject {
        // An exclusive directory claim prevents overwriting a completed or interrupted baseline.
        Files.createDirectory(directory.toPath())
        val prefs = targetContext.getSharedPreferences("orbita", Context.MODE_PRIVATE)
        check(prefs.edit().commit()) { "Preference flush failed" }
        val snapshot = PreferenceStateSnapshot.read(targetContext)
        val digest = PreferenceStateSnapshot.digest(snapshot)
        writeNew(File(directory, "preferences.json"), snapshot.toString(2).toByteArray(Charsets.UTF_8))
        val manifest = JSONArray()
        copyTree(File(targetContext.dataDir, "shared_prefs"), File(directory, "raw/shared_prefs"), manifest)
        copyTree(targetContext.filesDir, File(directory, "raw/files"), manifest)
        writeNew(File(directory, "raw-manifest.json"), manifest.toString(2).toByteArray(Charsets.UTF_8))
        check(PreferenceStateSnapshot.matches(targetContext, snapshot)) { "Preferences changed during backup" }
        // The digest marker is written last. An interrupted directory is never a valid restore source.
        writeNew(File(directory, "snapshot.sha256"), (digest + "\n").toByteArray(Charsets.US_ASCII))
        return readJournal(directory)
    }

    private fun readJournal(directory: File): JSONObject {
        check(directory.isDirectory && !Files.isSymbolicLink(directory.toPath())) { "Snapshot unavailable" }
        val jsonFile = File(directory, "preferences.json")
        val digestFile = File(directory, "snapshot.sha256")
        check(jsonFile.isFile && digestFile.isFile && !Files.isSymbolicLink(jsonFile.toPath()) && !Files.isSymbolicLink(digestFile.toPath())) { "Incomplete snapshot" }
        val expected = digestFile.readText(Charsets.US_ASCII).trim()
        require(expected.matches(Regex("[0-9a-f]{64}"))) { "Invalid journal digest" }
        val snapshot = JSONObject(jsonFile.readText(Charsets.UTF_8))
        check(PreferenceStateSnapshot.digest(snapshot) == expected) { "Journal verification failed" }
        return snapshot
    }

    private fun writeNew(file: File, bytes: ByteArray) {
        check(file.parentFile?.let { it.isDirectory || it.mkdirs() } == true) { "Backup directory unavailable" }
        FileChannel.open(file.toPath(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { channel ->
            val buffer = ByteBuffer.wrap(bytes)
            while (buffer.hasRemaining()) channel.write(buffer)
            channel.force(true)
        }
        // Emulated external storage may ignore chmod. Immutability is also enforced by CREATE_NEW.
        file.setReadOnly()
    }

    private fun copyTree(source: File, destination: File, manifest: JSONArray) {
        val from = source.toPath().toAbsolutePath().normalize()
        val to = destination.toPath().toAbsolutePath().normalize()
        check(!to.startsWith(from) && !from.startsWith(to)) { "Recursive backup location" }
        if (!Files.exists(from, LinkOption.NOFOLLOW_LINKS)) return
        check(!Files.isSymbolicLink(from) && Files.isDirectory(from, LinkOption.NOFOLLOW_LINKS)) { "Invalid backup source" }
        Files.walkFileTree(from, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                check(!Files.isSymbolicLink(dir)) { "Backup links are not supported" }
                val output = to.resolve(from.relativize(dir)).normalize()
                check(output.startsWith(to)) { "Invalid backup path" }
                Files.createDirectories(output)
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                check(attrs.isRegularFile && !Files.isSymbolicLink(file)) { "Unsupported backup file" }
                val relative = from.relativize(file)
                val output = to.resolve(relative).normalize()
                check(output.startsWith(to)) { "Invalid backup file location" }
                val hasher = MessageDigest.getInstance("SHA-256")
                var bytes = 0L
                Files.newInputStream(file).use { input ->
                    Files.newOutputStream(output, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { target ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            target.write(buffer, 0, read)
                            hasher.update(buffer, 0, read)
                            bytes += read
                        }
                    }
                }
                output.toFile().setReadOnly()
                val digest = hasher.digest().joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
                manifest.put(JSONObject().put("tree", source.name).put("path", relative.toString())
                    .put("bytes", bytes).put("sha256", digest))
                return FileVisitResult.CONTINUE
            }
        })
    }
}

/** Register with targetPackage=br.com.lume.launcher; the UUID-isolated selftest cannot run maintenance actions. */
class DeviceStateSelfTestInstrumentation : DeviceStateInstrumentation() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(Bundle(arguments ?: Bundle()).apply { putString("action", "selftest") })
    }
}
