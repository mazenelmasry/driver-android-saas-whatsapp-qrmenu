package app.qrmenu.driver.auth.invite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InviteCodeSanitizerTest {

    @Test
    fun `lowercase input is upper-cased`() {
        assertEquals("XG7K4P", InviteCodeSanitizer.sanitize("xg7k4p"))
    }

    @Test
    fun `a pasted whatsapp message is reduced to just the code`() {
        assertEquals("XG7K4P", InviteCodeSanitizer.sanitize("your code is: XG7K4P."))
    }

    @Test
    fun `ambiguous characters never survive sanitisation`() {
        val sanitized = InviteCodeSanitizer.sanitize("0O1IL")
        assertTrue("must contain none of 0/O/1/I/L, was '$sanitized'", sanitized.isEmpty())
    }

    /** The LAST six survive — see the doc on why (a pasted sentence ends with the code). */
    @Test
    fun `input longer than six characters keeps the trailing six`() {
        assertEquals("CDEFGH", InviteCodeSanitizer.sanitize("ABCDEFGH"))
    }

    @Test
    fun `spaces from a dictated code are dropped`() {
        assertEquals("XG7K4P", InviteCodeSanitizer.sanitize("XG 7K 4P"))
    }

    @Test
    fun `completeness is exactly six sanitised characters`() {
        assertFalse(InviteCodeSanitizer.isComplete("XG7K4"))
        assertTrue(InviteCodeSanitizer.isComplete("XG7K4P"))
    }
}
