package org.example.fanfic

import com.sun.jna.platform.win32.Crypt32Util
import java.io.File
import java.net.CookieHandler
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.HttpCookie
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.AclEntry
import java.nio.file.attribute.AclEntryPermission
import java.nio.file.attribute.AclEntryType
import java.nio.file.attribute.AclFileAttributeView

object FicbookCookies {
    private val file = File(System.getProperty("user.home"), ".g134office/ficbook-cookies.txt")
    private val manager = CookieManager(null, CookiePolicy.ACCEPT_ALL)
    private val ficbookHost = "ficbook.net"

    fun install() {
        if (CookieHandler.getDefault() !== manager) {
            load()
            CookieHandler.setDefault(manager)
        }
    }

    fun save() {
        runCatching {
            val lines = manager.cookieStore.cookies.map { cookie ->
                listOf(
                    cookie.name.orEmpty(),
                    cookie.value.orEmpty(),
                    cookie.domain.orEmpty(),
                    cookie.path ?: "/",
                    cookie.maxAge.toString(),
                    cookie.secure.toString(),
                    cookie.isHttpOnly.toString()
                ).joinToString("\t")
            }
            CookieVault.write(file, lines.joinToString("\n"))
        }
    }

    fun clear(): Boolean = runCatching {
        install()
        manager.cookieStore.removeAll()
        CookieVault.wipe(file)
    }.isSuccess

    private fun load() {
        if (!file.isFile) return
        runCatching {
            CookieVault.read(file).lineSequence().forEach { line ->
                val parts = line.split('\t')
                if (parts.size < 4) return@forEach
                val cookie = HttpCookie(parts[0], parts[1]).apply {
                    domain = parts[2]
                    path = parts[3]
                    if (parts.size > 4) maxAge = parts[4].toLongOrNull() ?: -1
                    if (parts.size > 5) secure = parts[5].toBooleanStrictOrNull() ?: false
                    if (parts.size > 6) isHttpOnly = parts[6].toBooleanStrictOrNull() ?: false
                }
                val host = cookie.domain.trimStart('.')
                if (host.isNotBlank()) {
                    manager.cookieStore.add(URI("https://$host/"), cookie)
                }
            }
        }
    }

    fun replaceFicbook(cookies: List<BrowserCookie>): Int {
        install()
        val store = manager.cookieStore
        store.cookies.filter { it.domain.orEmpty().contains(ficbookHost, ignoreCase = true) }.toList().forEach { cookie ->
            val host = cookie.domain.trimStart('.')
            store.remove(URI("https://$host/"), cookie)
        }
        var added = 0
        cookies.filter { it.domain.contains(ficbookHost, ignoreCase = true) && it.value.isNotBlank() }.forEach { item ->
            val cookie = HttpCookie(item.name, item.value).apply {
                domain = item.domain
                path = item.path.ifBlank { "/" }
                secure = item.secure
                isHttpOnly = item.httpOnly
                version = 0
                if (item.expiresAtEpochSec != null && item.expiresAtEpochSec > 0) {
                    val age = item.expiresAtEpochSec - System.currentTimeMillis() / 1000
                    if (age <= 0) return@forEach
                    maxAge = age
                }
            }
            val host = cookie.domain.trimStart('.')
            store.add(URI("https://$host/"), cookie)
            added++
        }
        save()
        return added
    }
}

/** DPAPI blob on Windows. Older installs stored the same TSV as plain text and still load. */
internal object CookieVault {
    private val magic = "G134CK01".toByteArray(Charsets.US_ASCII)

    fun write(file: File, text: String) {
        if (text.isEmpty()) {
            wipe(file)
            return
        }
        file.parentFile?.mkdirs()
        val raw = text.toByteArray(Charsets.UTF_8)
        val stored = if (isWindows()) magic + Crypt32Util.cryptProtectData(raw) else raw
        file.writeBytes(stored)
        restrictToCurrentUser(file.toPath())
    }

    fun read(file: File): String {
        if (!file.isFile || file.length() == 0L) return ""
        val bytes = file.readBytes()
        val plain = if (bytes.size > magic.size && bytes.copyOf(magic.size).contentEquals(magic)) {
            Crypt32Util.cryptUnprotectData(bytes.copyOfRange(magic.size, bytes.size))
        } else {
            bytes
        }
        return String(plain, Charsets.UTF_8)
    }

    fun wipe(file: File) {
        if (!file.isFile) return
        val length = file.length().toInt().coerceAtLeast(0)
        if (length > 0) file.writeBytes(ByteArray(length.coerceAtMost(1024 * 1024)))
        if (!file.delete()) file.writeBytes(ByteArray(0))
    }

    private fun isWindows(): Boolean = System.getProperty("os.name").orEmpty().contains("win", true)
}

internal fun restrictToCurrentUser(path: Path) {
    val view = Files.getFileAttributeView(path, AclFileAttributeView::class.java) ?: return
    val owner = runCatching { view.owner }.getOrNull() ?: return
    val entry = AclEntry.newBuilder()
        .setType(AclEntryType.ALLOW)
        .setPrincipal(owner)
        .setPermissions(*AclEntryPermission.entries.toTypedArray())
        .build()
    runCatching { view.acl = listOf(entry) }
}
