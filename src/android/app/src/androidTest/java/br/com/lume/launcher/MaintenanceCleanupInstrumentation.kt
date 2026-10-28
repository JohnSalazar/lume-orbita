package br.com.lume.launcher

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.UUID

/** Test APK only. No Activity, preference restore, package removal or protected-setting writes. */
class MaintenanceCleanupInstrumentation : Instrumentation() {
    private var options = Bundle()
    // Add evidence directories only after a reviewed on-device inventory; never accept a caller path.
    private val approvedDirectories = setOf(
        "maintenance-state", "audit-pre-inapp-044", "audit-post-inapp-044", "audit-pre-inapp-045", "audit-post-inapp-045",
        "konka-041-locales-a", "konka-041-locales-b", "konka-041-locales-c",
        "konka-041-offline", "konka-041-online", "konka-041-short", "konka-041-short2",
        "konka-042-clock", "konka-043-updater2"
    )
    private val apkName = Regex("lume-update-[A-Za-z0-9-]+\\.apk")
    private val directoryName = Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,95}")
    private data class Entry(val path: Path, val directory: Boolean, val bytes: Long)

    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); options = arguments ?: Bundle(); start() }

    override fun onStart() {
        val report = JSONObject()
        var failed: Throwable? = null
        var beforeDigest: String? = null
        var beforeHome: String? = null
        try {
            check(context.packageName == "br.com.lume.launcher.test" && targetContext.packageName == "br.com.lume.launcher")
            beforeDigest = PreferenceStateSnapshot.digest(PreferenceStateSnapshot.read(targetContext))
            beforeHome = home()
            val action = options.getString("action") ?: "inventory"
            require(action in setOf("inventory", "cleanup", "selftest"))
            report.put("action", action)
            val external = requireNotNull(targetContext.getExternalFilesDir(null))
            val updates = File(targetContext.cacheDir, "updates")
            when (action) {
                "inventory" -> report.put("external_files", inventory(external)).put("updates", inventory(updates))
                "selftest" -> report.put("selftest", selfTest())
                "cleanup" -> {
                    @Suppress("DEPRECATION")
                    check(targetContext.packageManager.getPackageInfo(targetContext.packageName, 0).longVersionCode == 31L)
                    check(beforeHome != null)
                    val dirs = options.getString("dirs")?.split(',') ?: emptyList()
                    val updateOption = options.getString("updates")
                    require(updateOption == null || updateOption in setOf("true", "false"))
                    val cleanUpdates = updateOption == "true"
                    require(dirs.isNotEmpty() || cleanUpdates)
                    if (cleanUpdates) check(targetContext.packageManager.packageInstaller.mySessions.isEmpty())
                    val entries = plan(external, dirs, if (cleanUpdates) updates else null)
                    // No deletion happens until every selected directory and update file passed preflight.
                    if (cleanUpdates) check(targetContext.packageManager.packageInstaller.mySessions.isEmpty())
                    delete(entries)
                    report.put("removed_entries", entries.size).put("removed_files", entries.count { !it.directory })
                        .put("removed_bytes", entries.sumOf { it.bytes })
                }
            }
        } catch (error: Throwable) { failed = error }
        finally {
            val prefsEqual = runCatching {
                beforeDigest != null && beforeDigest == PreferenceStateSnapshot.digest(PreferenceStateSnapshot.read(targetContext))
            }.getOrDefault(false)
            val homeEqual = runCatching { beforeHome == home() }.getOrDefault(false)
            report.put("preferences_unchanged", prefsEqual).put("home_unchanged", homeEqual)
            if (!prefsEqual || !homeEqual) failed = failed ?: IllegalStateException("State changed")
        }
        report.put("status", if (failed == null) "ok" else "failed")
        failed?.let { report.put("error", it.javaClass.simpleName) }
        finish(if (failed == null) Activity.RESULT_OK else Activity.RESULT_CANCELED,
            Bundle().apply { putString("report", report.toString()) })
    }

    private fun home(): String? = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        .resolveActivity(targetContext.packageManager)?.flattenToString()

    /** Only top-level names and aggregate sizes/counts, never file contents or nested names. */
    private fun inventory(base: File): JSONArray {
        val result = JSONArray()
        if (!Files.exists(base.toPath(), LinkOption.NOFOLLOW_LINKS)) return result
        check(!Files.isSymbolicLink(base.toPath()) && base.isDirectory)
        base.listFiles()?.sortedBy { it.name }?.forEach { child ->
            val item = JSONObject().put("name", child.name)
            try {
                val entries = scan(child, base.canonicalFile)
                item.put("files", entries.count { !it.directory }).put("bytes", entries.sumOf { it.bytes })
                    .put("directories", entries.count { it.directory }).put("status", "ok")
            } catch (_: Exception) { item.put("status", "blocked") }
            result.put(item)
        } ?: error("Cannot list directory")
        return result
    }

    private fun plan(external: File, dirs: List<String>, updates: File?): List<Entry> {
        require(dirs.size == dirs.distinct().size)
        dirs.forEach { require(directoryName.matches(it) && it in approvedDirectories) }
        val base = external.canonicalFile
        check(!Files.isSymbolicLink(external.toPath()) && base.isDirectory)
        val entries = mutableListOf<Entry>()
        dirs.forEach { name ->
            val child = File(base, name)
            check(child.canonicalFile.parentFile == base && !Files.isSymbolicLink(child.toPath()))
            check(Files.isDirectory(child.toPath(), LinkOption.NOFOLLOW_LINKS))
            entries.addAll(scan(child, base))
        }
        if (updates != null && Files.exists(updates.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            check(!Files.isSymbolicLink(updates.toPath()) && updates.canonicalFile.parentFile == targetCacheParent(updates))
            val cache = updates.canonicalFile
            check(cache.isDirectory)
            val files = requireNotNull(cache.listFiles())
            files.filter { apkName.matches(it.name) }.forEach { file ->
                check(!Files.isSymbolicLink(file.toPath()) && Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS))
                entries.addAll(scan(file, cache))
            }
        }
        check(entries.size <= 50_000)
        return entries
    }

    private fun targetCacheParent(updates: File): File = requireNotNull(updates.absoluteFile.parentFile).canonicalFile

    /** Walk without FOLLOW_LINKS; collect post-order, and reject the entire plan on any unsafe entry. */
    private fun scan(target: File, parent: File): List<Entry> {
        check(!Files.isSymbolicLink(target.toPath()) && target.canonicalFile.parentFile == parent.canonicalFile)
        val root = target.canonicalFile.toPath()
        val boundary = root
        val entries = mutableListOf<Entry>()
        fun safe(path: Path) {
            check(!Files.isSymbolicLink(path) && path.toFile().canonicalFile.toPath().startsWith(boundary))
            check(entries.size < 50_000 && path.nameCount - root.nameCount <= 32)
        }
        Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                safe(dir); check(attrs.isDirectory); return FileVisitResult.CONTINUE
            }
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                safe(file); check(attrs.isRegularFile && !attrs.isSymbolicLink)
                entries.add(Entry(file, false, attrs.size())); return FileVisitResult.CONTINUE
            }
            override fun postVisitDirectory(dir: Path, error: java.io.IOException?): FileVisitResult {
                if (error != null) throw error
                safe(dir); entries.add(Entry(dir, true, 0)); return FileVisitResult.CONTINUE
            }
        })
        return entries
    }

    private fun delete(entries: List<Entry>) {
        // Recheck every path before deleting the first one, then again before its own deletion.
        fun unchanged(entry: Entry) {
            check(!Files.isSymbolicLink(entry.path) && entry.path.toFile().canonicalFile.toPath() == entry.path.toAbsolutePath().normalize())
            val attrs = Files.readAttributes(entry.path, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
            check(if (entry.directory) attrs.isDirectory else attrs.isRegularFile && attrs.size() == entry.bytes)
        }
        entries.forEach(::unchanged)
        entries.forEach { unchanged(it); Files.delete(it.path) }
    }

    private fun selfTest(): JSONObject {
        val fixture = File(targetContext.cacheDir, "maintenance-selftest-${UUID.randomUUID()}")
        check(!fixture.exists() && fixture.mkdirs() && fixture.canonicalFile.parentFile == targetContext.cacheDir.canonicalFile)
        val external = File(fixture, "external").apply { check(mkdir()) }
        val updates = File(fixture, "updates").apply { check(mkdir()) }
        val keep = File(external, "unrelated.txt").apply { writeText("keep") }
        val outside = File(fixture, "outside.txt").apply { writeText("outside") }
        val approved = File(external, "maintenance-state").apply { check(mkdir()) }
        val partial = File(approved, "backup.json").apply { writeText("fixture") }
        val apk = File(updates, "lume-update-fixture.apk").apply { writeText("fixture") }
        val cacheKeep = File(updates, "keep.db").apply { writeText("keep") }
        val link = File(approved, "link").toPath()
        val cacheLink = File(updates, "lume-update-link.apk").toPath()
        var rejected = 0
        fun blocked(action: () -> Unit) { check(runCatching(action).isFailure); rejected++ }
        try {
            listOf("..", "../maintenance-state", "maintenance-state/", "a/b", "a\\b", "/", "unrelated.txt").forEach { name ->
                blocked { plan(external, listOf(name), null) }
            }
            Files.createSymbolicLink(link, outside.toPath())
            blocked { plan(external, listOf("maintenance-state"), null) }
            check(partial.isFile && outside.readText() == "outside")
            Files.delete(link)
            Files.createSymbolicLink(cacheLink, outside.toPath())
            blocked { plan(external, listOf("maintenance-state"), updates) }
            check(partial.isFile && apk.isFile && outside.readText() == "outside")
            Files.delete(cacheLink)
            delete(plan(external, listOf("maintenance-state"), updates))
            check(!approved.exists() && !apk.exists() && keep.readText() == "keep" && cacheKeep.readText() == "keep")
            return JSONObject().put("blocked_cases", rejected).put("sentinels_preserved", true)
        } finally {
            if (Files.isSymbolicLink(link)) Files.delete(link)
            if (Files.isSymbolicLink(cacheLink)) Files.delete(cacheLink)
            delete(scan(fixture, targetContext.cacheDir.canonicalFile))
        }
    }
}
