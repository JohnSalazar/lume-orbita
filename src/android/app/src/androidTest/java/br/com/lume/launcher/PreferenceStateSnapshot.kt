package br.com.lume.launcher

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

/** Test-only, typed state transfer. No dependency on the version of Lume installed on the device. */
internal object PreferenceStateSnapshot {
    private data class Entry(val key: String, val type: String, val value: Any)

    fun read(context: Context): JSONObject {
        val entries = context.getSharedPreferences("orbita", Context.MODE_PRIVATE).all.entries.map { (key, value) ->
            when (value) {
                is Boolean -> Entry(key, "boolean", value)
                is Int -> Entry(key, "int", value)
                is Long -> Entry(key, "long", value.toString())
                is Float -> Entry(key, "float", Integer.toHexString(java.lang.Float.floatToRawIntBits(value)).padStart(8, '0'))
                is String -> Entry(key, "string", value)
                is Set<*> -> {
                    require(value.all { it is String }) { "Unsupported preference set" }
                    Entry(key, "string_set", value.map { it as String }.toSet().sorted())
                }
                else -> error("Unsupported preference type")
            }
        }.sortedBy { it.key }
        return encode(entries)
    }

    fun write(context: Context, json: JSONObject) {
        // Build a complete validated copy before touching the editor or clearing any value.
        val entries = decode(json)
        val expected = hash(canonical(entries))
        val editor = context.getSharedPreferences("orbita", Context.MODE_PRIVATE).edit().clear()
        entries.forEach { entry ->
            when (entry.type) {
                "boolean" -> editor.putBoolean(entry.key, entry.value as Boolean)
                "int" -> editor.putInt(entry.key, entry.value as Int)
                "long" -> editor.putLong(entry.key, (entry.value as String).toLong())
                "float" -> editor.putFloat(entry.key, java.lang.Float.intBitsToFloat((entry.value as String).toLong(16).toInt()))
                "string" -> editor.putString(entry.key, entry.value as String)
                "string_set" -> editor.putStringSet(entry.key, strings(entry.value).toSet())
            }
        }
        check(editor.commit()) { "Preference restore commit failed" }
        check(digest(read(context)) == expected) { "Preference restore verification failed" }
    }

    fun digest(json: JSONObject): String = hash(canonical(decode(json)))

    fun matches(context: Context, json: JSONObject): Boolean = try {
        digest(read(context)) == digest(json)
    } catch (_: Exception) { false }

    /** Runs only against an exclusive UUID file in the target UID, never its actual orbita preferences. */
    fun selfTest(storageContext: Context): JSONObject {
        require(storageContext.packageName == "br.com.lume.launcher") { "Selftest requires the launcher UID" }
        val name = "snapshot_selftest_" + UUID.randomUUID().toString().replace("-", "")
        val prefs = storageContext.getSharedPreferences(name, Context.MODE_PRIVATE)
        check(prefs.all.isEmpty()) { "Selftest file must be empty" }
        val isolated = object : ContextWrapper(storageContext) {
            override fun getSharedPreferences(requested: String, mode: Int): SharedPreferences {
                require(requested == "orbita" && mode == Context.MODE_PRIVATE) { "Invalid selftest preference request" }
                return prefs
            }
        }
        try {
            check(prefs.edit().putBoolean("boolean_true", true).putBoolean("boolean_false", false)
                .putInt("int_min", Int.MIN_VALUE).putInt("int_max", Int.MAX_VALUE).putInt("int_zero", 0)
                .putLong("long_min", Long.MIN_VALUE).putLong("long_max", Long.MAX_VALUE)
                .putFloat("float_finite", 12.5f).putFloat("float_negative_zero", -0.0f)
                .putFloat("float_min", Float.MIN_VALUE).putFloat("float_max", Float.MAX_VALUE)
                .putFloat("float_nan", Float.NaN).putFloat("float_positive_infinity", Float.POSITIVE_INFINITY)
                .putFloat("float_negative_infinity", Float.NEGATIVE_INFINITY)
                .putString("string_empty", "").putString("string_unicode", "日本語 · العربية\n\"literal\" \\ %1\$s")
                .putStringSet("set_values", setOf("z", "a", "é", "日本語")).putStringSet("set_empty", emptySet())
                .commit()) { "Selftest fixture commit failed" }
            val baseline = read(isolated)
            val expected = digest(baseline)
            val rows = baseline.getJSONArray("entries")
            val keys = (0 until rows.length()).map { rows.getJSONObject(it).getString("key") }
            check(keys == keys.sorted() && keys.toSet() == prefs.all.keys && rows.length() == 18) { "Selftest key ordering or coverage failed" }
            val setRow = (0 until rows.length()).map { rows.getJSONObject(it) }.first { it.getString("key") == "set_values" }.getJSONArray("value")
            val setValues = (0 until setRow.length()).map { setRow.getString(it) }
            check(setValues == setValues.sorted()) { "Selftest set ordering failed" }
            val reversed = JSONObject().put("version", 1).put("entries", JSONArray().apply {
                for (index in rows.length() - 1 downTo 0) put(rows.getJSONObject(index))
            })
            check(digest(reversed) == expected) { "Selftest canonical ordering failed" }
            check(prefs.edit().putString("created_after_snapshot", "fixture").putInt("long_max", 1)
                .putStringSet("set_values", setOf("changed")).commit()) { "Selftest mutation commit failed" }
            check(digest(baseline) == expected && !matches(isolated, baseline)) { "Selftest snapshot copy failed" }
            write(isolated, JSONObject(baseline.toString()))
            check(!prefs.contains("created_after_snapshot") && prefs.getLong("long_max", 0) == Long.MAX_VALUE &&
                prefs.getLong("long_min", 0) == Long.MIN_VALUE) { "Selftest absence or long restore failed" }
            check(prefs.getBoolean("boolean_true", false) && !prefs.getBoolean("boolean_false", true)) { "Selftest boolean restore failed" }
            check(prefs.getInt("int_min", 0) == Int.MIN_VALUE && prefs.getInt("int_max", 0) == Int.MAX_VALUE) { "Selftest integer restore failed" }
            check(java.lang.Float.floatToRawIntBits(prefs.getFloat("float_negative_zero", 1f)) == Int.MIN_VALUE) { "Selftest float bits failed" }
            check(prefs.getStringSet("set_values", null) == setOf("z", "a", "é", "日本語")) { "Selftest set restore failed" }
            check(matches(isolated, baseline)) { "Selftest roundtrip failed" }

            fun reject(candidate: JSONObject) {
                var rejected = false
                try { write(isolated, candidate) } catch (_: Exception) { rejected = true }
                check(rejected && matches(isolated, baseline)) { "Selftest rejection changed preferences" }
            }
            fun invalidEntry(type: String, value: Any): JSONObject = JSONObject().put("version", 1).put("entries", JSONArray()
                .put(JSONObject().put("key", "invalid_fixture").put("type", type).put("value", value)))
            reject(JSONObject(baseline.toString()).put("version", 2))
            reject(JSONObject(baseline.toString()).put("unexpected", true))
            reject(JSONObject().put("version", 1))
            reject(JSONObject().put("version", 1).put("entries", "not_an_array"))
            reject(JSONObject().put("version", 1).put("entries", JSONArray().put("not_a_row")))
            val duplicate = JSONObject(rows.getJSONObject(0).toString())
            reject(JSONObject().put("version", 1).put("entries", JSONArray().put(duplicate).put(duplicate)))
            reject(invalidEntry("boolean", "true"))
            reject(invalidEntry("int", "1"))
            reject(invalidEntry("int", Long.MAX_VALUE))
            reject(invalidEntry("long", "01"))
            reject(invalidEntry("long", "9223372036854775808"))
            reject(invalidEntry("float", "not_bits"))
            reject(invalidEntry("string", JSONObject.NULL))
            reject(invalidEntry("string_set", JSONArray().put("a").put("a")))
            reject(invalidEntry("string_set", JSONArray().put(1)))
            reject(invalidEntry("unsupported", "fixture"))
            val extraField = invalidEntry("string", "fixture")
            extraField.getJSONArray("entries").getJSONObject(0).put("unexpected", true)
            reject(extraField)
            check(!matches(isolated, JSONObject().put("version", 2))) { "Selftest invalid match accepted" }
            return JSONObject().put("entries", rows.length()).put("digest", expected)
        } finally {
            check(storageContext.deleteSharedPreferences(name)) { "Selftest cleanup failed" }
        }
    }

    private fun encode(entries: List<Entry>): JSONObject = JSONObject().put("version", 1).put("entries", JSONArray().apply {
        entries.forEach { entry ->
            put(JSONObject().put("key", entry.key).put("type", entry.type)
                .put("value", if (entry.type == "string_set") JSONArray(strings(entry.value)) else entry.value))
        }
    })

    private fun decode(json: JSONObject): List<Entry> {
        require(keys(json) == setOf("version", "entries") && json.get("version") == 1) { "Invalid snapshot schema" }
        val rows = json.opt("entries")
        require(rows is JSONArray) { "Invalid snapshot entries" }
        val seen = mutableSetOf<String>()
        val entries = (0 until rows.length()).map { index ->
            val row = rows.opt(index)
            require(row is JSONObject) { "Invalid snapshot row" }
            require(keys(row) == setOf("key", "type", "value")) { "Invalid snapshot entry" }
            val key = row.get("key")
            val type = row.get("type")
            require(key is String && seen.add(key) && type is String) { "Invalid snapshot key or type" }
            val raw = row.get("value")
            val value: Any = when (type) {
                "boolean" -> { require(raw is Boolean) { "Invalid boolean" }; raw }
                "int" -> { require(raw is Int) { "Invalid integer" }; raw }
                "long" -> {
                    require(raw is String && raw.toLongOrNull()?.toString() == raw) { "Invalid long" }
                    raw
                }
                "float" -> {
                    require(raw is String && raw.matches(Regex("[0-9a-f]{8}"))) { "Invalid float bits" }
                    raw
                }
                "string" -> { require(raw is String) { "Invalid string" }; raw }
                "string_set" -> {
                    require(raw is JSONArray) { "Invalid string set" }
                    val items = (0 until raw.length()).map { position ->
                        val item = raw.get(position)
                        require(item is String) { "Invalid string set member" }
                        item
                    }
                    require(items.size == items.toSet().size) { "Duplicate string set member" }
                    items.sorted()
                }
                else -> error("Unsupported snapshot type")
            }
            Entry(key, type, value)
        }
        return entries.sortedBy { it.key }
    }

    private fun keys(json: JSONObject): Set<String> = json.keys().asSequence().toSet()

    private fun strings(value: Any): List<String> = (value as List<*>).map { it as String }

    // Explicit ordering and quoting keep the digest independent of JSONObject's map ordering.
    private fun canonical(entries: List<Entry>): String = entries.joinToString(prefix = "{\"version\":1,\"entries\":[", postfix = "]}", separator = ",") { entry ->
        val value = when (entry.type) {
            "boolean", "int" -> entry.value.toString()
            "string_set" -> strings(entry.value).joinToString(prefix = "[", postfix = "]", separator = ",") { JSONObject.quote(it) }
            else -> JSONObject.quote(entry.value as String)
        }
        "{\"key\":" + JSONObject.quote(entry.key) + ",\"type\":" + JSONObject.quote(entry.type) + ",\"value\":" + value + "}"
    }

    private fun hash(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
}
