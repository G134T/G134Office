package org.example.fanfic

import org.junit.jupiter.api.Test
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
}
