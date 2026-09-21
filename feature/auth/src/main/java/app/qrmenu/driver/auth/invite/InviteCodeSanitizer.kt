package app.qrmenu.driver.auth.invite

import java.util.Locale

/**
 * Cleans up whatever the driver typed or pasted into the invite-code field.
 *
 * Pure JVM, no Compose — the same reasoning as `TokenExpiry`: this is the part
 * worth a unit test, the field around it is not.
 *
 * The code is read out loud over the phone or pasted from a WhatsApp message
 * (CLAUDE.md, decision 16), so it must accept both a slow, careful dictation
 * AND a paste of "your code is: XG7K4P" — hence filtering to the allowed
 * alphabet rather than rejecting the whole input on the first stray character.
 */
object InviteCodeSanitizer {

    /** Six characters — long enough to avoid guessing, short enough to read aloud once. */
    const val LENGTH = 6

    /**
     * No `0`/`O` and no `1`/`I`/`L` (CLAUDE.md, decision 16): those pairs are
     * indistinguishable when dictated over a phone call or printed in a small
     * WhatsApp font, and a driver who transcribes the wrong one gets a silent
     * "invalid invite" instead of a working code.
     */
    private const val ALLOWED_CHARACTERS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

    /**
     * Upper-cases, strips anything outside [ALLOWED_CHARACTERS] (spaces from a
     * pasted "XG7K 4P", the ambiguous letters themselves), and caps at
     * [LENGTH].
     *
     * Takes the LAST [LENGTH] allowed characters, not the first: a pasted
     * WhatsApp message is a sentence that ENDS with the code ("Your invite
     * code is: XG7K4P"), and the sentence itself is full of other allowed
     * letters — truncating from the front would keep the explanation and
     * discard the code it was explaining.
     */
    fun sanitize(raw: String): String =
        raw.uppercase(Locale.ROOT)
            .filter { it in ALLOWED_CHARACTERS }
            .takeLast(LENGTH)

    fun isComplete(code: String): Boolean = code.length == LENGTH
}
