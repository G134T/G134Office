package org.example.fanfic

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FicbookBrowserAuthTest {
    @Test
    fun `ficbook hosts are accepted`() {
        assertTrue(FicbookBrowserAuth.isFicbookHost("ficbook.net"))
        assertTrue(FicbookBrowserAuth.isFicbookHost(".ficbook.net"))
        assertTrue(FicbookBrowserAuth.isFicbookHost("www.ficbook.net"))
        assertFalse(FicbookBrowserAuth.isFicbookHost("example.com"))
        assertFalse(FicbookBrowserAuth.isFicbookHost("notficbook.net"))
    }

    @Test
    fun `chrome expiry converts from windows epoch`() {
        assertNull(FicbookBrowserAuth.chromeExpiryToEpoch(0))
        val unix = 1_800_000_000L
        val chrome = (unix + 11_644_473_600L) * 1_000_000L
        assertEquals(unix, FicbookBrowserAuth.chromeExpiryToEpoch(chrome))
    }

    @Test
    fun `a copied cookie database is deleted even when reading fails`() {
        val db = File.createTempFile("cookies", ".sqlite")
        db.writeBytes(byteArrayOf(1, 2, 3, 4))
        db.deleteOnExit()
        var parent: File? = null
        try {
            FicbookBrowserAuth.withPrivateDatabaseCopy(db) { copy ->
                parent = copy.parentFile
                assertTrue(copy.isFile)
                assertTrue(copy.readBytes().contentEquals(byteArrayOf(1, 2, 3, 4)))
                error("stop")
            }
        } catch (expected: IllegalStateException) {
            assertEquals("stop", expected.message)
        }
        assertFalse(parent!!.exists())
        assertTrue(db.isFile)
    }

    @Test
    fun `cookie vault hides values and still reads a legacy file`() {
        val file = File.createTempFile("ficbook-cookies", ".txt")
        file.deleteOnExit()
        CookieVault.write(file, "session\tsecret-value")
        val stored = file.readBytes()
        if (System.getProperty("os.name").orEmpty().contains("win", true)) {
            assertFalse(stored.toString(Charsets.ISO_8859_1).contains("secret-value"))
        }
        assertEquals("session\tsecret-value", CookieVault.read(file))
        file.writeText("legacy\tplain")
        assertEquals("legacy\tplain", CookieVault.read(file))
        CookieVault.wipe(file)
        assertFalse(file.exists())
    }
}
