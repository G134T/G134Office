package org.example.fanfic

import java.io.File
import java.net.CookieHandler
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.HttpCookie
import java.net.URI

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
            file.parentFile.mkdirs()
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
            file.writeText(lines.joinToString("\n"))
        }
    }

    fun clear(): Boolean = runCatching {
        install()
        manager.cookieStore.removeAll()
        if (file.exists() && !file.delete()) file.writeText("")
    }.isSuccess

    private fun load() {
        if (!file.isFile) return
        runCatching {
            file.readLines().forEach { line ->
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
