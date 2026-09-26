package org.example.fanfic

import com.sun.jna.platform.win32.Crypt32Util
import java.io.File
import java.nio.file.Files
import java.sql.DriverManager
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class BrowserCookie(
    val name: String,
    val value: String,
    val domain: String,
    val path: String,
    val secure: Boolean,
    val httpOnly: Boolean,
    val expiresAtEpochSec: Long? = null
)

data class BrowserSession(
    val browser: String,
    val profile: String,
    val cookies: List<BrowserCookie>,
    val note: String = ""
) {
    fun label(): String {
        val extra = if (note.isBlank()) "" else " — $note"
        return "$browser ($profile): ${cookies.size} cookie$extra"
    }
}

object FicbookBrowserAuth {
    private const val HOST = "ficbook.net"
    private const val CHROME_EPOCH_OFFSET = 11644473600L

    fun discover(): List<BrowserSession> {
        Class.forName("org.sqlite.JDBC")
        val found = mutableListOf<BrowserSession>()
        found += firefoxSessions()
        found += chromiumSessions()
        return found.filter { it.cookies.isNotEmpty() || it.note.isNotBlank() }
    }

    fun chromeExpiryToEpoch(expiresUtc: Long): Long? {
        if (expiresUtc <= 0L) return null
        val seconds = expiresUtc / 1_000_000L - CHROME_EPOCH_OFFSET
        return seconds.takeIf { it > 0 }
    }

    fun isFicbookHost(host: String): Boolean =
        host.trimStart('.').equals(HOST, true) || host.trimStart('.').endsWith(".$HOST", true)

    private fun firefoxSessions(): List<BrowserSession> {
        val root = File(appData(), "Mozilla/Firefox")
        val ini = File(root, "profiles.ini")
        if (!ini.isFile) return emptyList()
        val sessions = mutableListOf<BrowserSession>()
        firefoxProfiles(ini, root).forEach { (name, dir) ->
            val db = File(dir, "cookies.sqlite")
            if (!db.isFile) return@forEach
            val cookies = readSqlite(db) { rs ->
                val host = rs.getString("host").orEmpty()
                if (!isFicbookHost(host)) return@readSqlite null
                BrowserCookie(
                    name = rs.getString("name").orEmpty(),
                    value = rs.getString("value").orEmpty(),
                    domain = host,
                    path = rs.getString("path").orEmpty().ifBlank { "/" },
                    secure = rs.getInt("isSecure") == 1,
                    httpOnly = rs.getInt("isHttpOnly") == 1,
                    expiresAtEpochSec = rs.getLong("expiry").takeIf { it > 0 }
                )
            }
            if (cookies.isNotEmpty()) {
                sessions += BrowserSession("Firefox", name, cookies)
            }
        }
        return sessions
    }

    private fun firefoxProfiles(ini: File, root: File): List<Pair<String, File>> {
        val result = mutableListOf<Pair<String, File>>()
        var name = "default"
        var path: String? = null
        var relative = true
        fun flush() {
            val raw = path ?: return
            val dir = if (relative) File(root, raw) else File(raw)
            if (dir.isDirectory) result += name to dir
            path = null
            relative = true
            name = "profile"
        }
        ini.readLines().forEach { line ->
            val trimmed = line.trim()
            when {
                trimmed.startsWith("[") && trimmed.contains("Profile", true) -> flush()
                trimmed.startsWith("Name=", true) -> name = trimmed.substringAfter("=")
                trimmed.startsWith("Path=", true) -> path = trimmed.substringAfter("=")
                trimmed.startsWith("IsRelative=", true) -> relative = trimmed.substringAfter("=") != "0"
            }
        }
        flush()
        return result
    }

    private fun chromiumSessions(): List<BrowserSession> {
        val local = localAppData()
        val roaming = appData()
        val browsers = listOf(
            "Chrome" to File(local, "Google/Chrome/User Data"),
            "Edge" to File(local, "Microsoft/Edge/User Data"),
            "Яндекс" to File(local, "Yandex/YandexBrowser/User Data"),
            "Opera" to File(roaming, "Opera Software/Opera Stable"),
            "Brave" to File(local, "BraveSoftware/Brave-Browser/User Data")
        )
        val sessions = mutableListOf<BrowserSession>()
        browsers.forEach { (browser, root) ->
            if (!root.isDirectory) return@forEach
            val profiles = if (File(root, "Local State").isFile || File(root, "Default").isDirectory) {
                root.listFiles().orEmpty().filter { dir ->
                    dir.isDirectory && (dir.name == "Default" || dir.name.startsWith("Profile"))
                }
            } else listOf(root)
            val key = chromiumKey(File(root, "Local State").takeIf { it.isFile }
                ?: File(root.parentFile, "Local State"))
            profiles.forEach { profileDir ->
                val db = File(profileDir, "Network/Cookies").takeIf { it.isFile }
                    ?: File(profileDir, "Cookies")
                if (!db.isFile) return@forEach
                var v20 = 0
                var failed = 0
                val cookies = readSqlite(db) { rs ->
                    val host = rs.getString("host_key").orEmpty()
                    if (!isFicbookHost(host)) return@readSqlite null
                    val encrypted = rs.getBytes("encrypted_value") ?: ByteArray(0)
                    val value = decryptChromium(encrypted, key)
                    when {
                        value == null && encrypted.size >= 3 && String(encrypted.copyOf(3)) == "v20" -> {
                            v20++
                            null
                        }
                        value == null -> {
                            failed++
                            null
                        }
                        else -> BrowserCookie(
                            name = rs.getString("name").orEmpty(),
                            value = value,
                            domain = host,
                            path = rs.getString("path").orEmpty().ifBlank { "/" },
                            secure = intFlag(rs, "is_secure", "secure"),
                            httpOnly = intFlag(rs, "is_httponly", "httponly"),
                            expiresAtEpochSec = chromeExpiryToEpoch(longFlag(rs, "expires_utc"))
                        )
                    }
                }
                val note = when {
                    cookies.isNotEmpty() && v20 > 0 -> "часть cookies закрыта браузером"
                    cookies.isEmpty() && v20 > 0 ->
                        "браузер скрыл вход (защита v20). Возьмите Firefox или войдите в окне Фикбука"
                    cookies.isEmpty() && failed > 0 -> "не удалось расшифровать cookies"
                    else -> ""
                }
                if (cookies.isNotEmpty() || note.isNotBlank()) {
                    sessions += BrowserSession(browser, profileDir.name, cookies, note)
                }
            }
        }
        return sessions
    }

    private fun intFlag(rs: java.sql.ResultSet, vararg names: String): Boolean {
        names.forEach { name ->
            runCatching { return rs.getInt(name) == 1 }
        }
        return false
    }

    private fun longFlag(rs: java.sql.ResultSet, name: String): Long =
        runCatching { rs.getLong(name) }.getOrDefault(0L)

    private fun chromiumKey(localState: File): ByteArray? {
        if (!localState.isFile || !isWindows()) return null
        val json = localState.readText()
        val encoded = Regex("\"encrypted_key\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1) ?: return null
        val raw = Base64.getDecoder().decode(encoded)
        if (raw.size < 6) return null
        val payload = raw.copyOfRange(5, raw.size)
        return runCatching { Crypt32Util.cryptUnprotectData(payload) }.getOrNull()
    }

    private fun decryptChromium(blob: ByteArray, key: ByteArray?): String? {
        if (blob.isEmpty()) return ""
        if (blob.size >= 3) {
            val prefix = String(blob, 0, 3)
            if (prefix == "v20") return null
            if ((prefix == "v10" || prefix == "v11") && key != null && blob.size > 31) {
                val nonce = blob.copyOfRange(3, 15)
                val cipherText = blob.copyOfRange(15, blob.size)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
                return runCatching { String(cipher.doFinal(cipherText), Charsets.UTF_8) }.getOrNull()
            }
        }
        if (!isWindows()) return null
        return runCatching { String(Crypt32Util.cryptUnprotectData(blob), Charsets.UTF_8) }.getOrNull()
    }

    private fun readSqlite(db: File, map: (java.sql.ResultSet) -> BrowserCookie?): List<BrowserCookie> {
        val urls = buildList {
            copySqlite(db)?.let { add(sqliteUrl(it, "mode=ro")) }
            add(sqliteUrl(db, "mode=ro&nolock=1"))
        }
        urls.forEach { url ->
            val rows = querySqlite(url, map)
            if (rows.isNotEmpty()) return rows
        }
        return emptyList()
    }

    private fun sqliteUrl(db: File, options: String): String =
        "jdbc:sqlite:file:${db.absolutePath.replace('\\', '/')}?$options"

    private fun querySqlite(url: String, map: (java.sql.ResultSet) -> BrowserCookie?): List<BrowserCookie> {
        return runCatching {
            DriverManager.getConnection(url).use { connection ->
                val tables = connection.metaData.getTables(null, null, "%", arrayOf("TABLE"))
                val names = mutableListOf<String>()
                while (tables.next()) names += tables.getString("TABLE_NAME").orEmpty()
                tables.close()
                val table = when {
                    names.any { it.equals("cookies", true) } -> "cookies"
                    names.any { it.equals("moz_cookies", true) } -> "moz_cookies"
                    else -> return@use emptyList()
                }
                connection.createStatement().use { statement ->
                    statement.executeQuery("SELECT * FROM $table").use { rs ->
                        val rows = mutableListOf<BrowserCookie>()
                        while (rs.next()) {
                            val cookie = runCatching { map(rs) }.getOrNull()
                            if (cookie != null && cookie.name.isNotBlank()) rows += cookie
                        }
                        rows
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun copySqlite(db: File): File? {
        if (!db.isFile) return null
        return runCatching {
            val dir = Files.createTempDirectory("g134-cookies").toFile()
            dir.deleteOnExit()
            val dest = File(dir, db.name)
            db.copyTo(dest, overwrite = true)
            listOf("-wal", "-shm").forEach { suffix ->
                val extra = File(db.path + suffix)
                if (extra.isFile) extra.copyTo(File(dir, db.name + suffix), overwrite = true)
            }
            dest
        }.getOrNull()
    }

    private fun isWindows(): Boolean =
        System.getProperty("os.name").orEmpty().contains("win", true)

    private fun appData(): String =
        System.getenv("APPDATA") ?: File(System.getProperty("user.home"), "AppData/Roaming").absolutePath

    private fun localAppData(): String =
        System.getenv("LOCALAPPDATA") ?: File(System.getProperty("user.home"), "AppData/Local").absolutePath
}
