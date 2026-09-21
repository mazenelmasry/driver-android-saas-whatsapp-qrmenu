package app.qrmenu.driver.network.errors

import java.net.SocketTimeoutException
import java.net.UnknownHostException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class DriverApiErrorTest {

    private fun httpError(status: Int, body: String): HttpException =
        HttpException(Response.error<Any>(status, body.toResponseBody("application/json".toMediaType())))

    @Test
    fun `a named refusal becomes a typed code`() {
        val error = httpError(409, """{"code":"already_claimed","message":"Taken"}""")
            .toDriverApiError() as DriverApiError.Api

        assertEquals(409, error.httpStatus)
        assertEquals(DriverErrorCode.AlreadyClaimed, error.code)
    }

    /**
     * An older app meeting a newer server must degrade, not crash and not print
     * the server's ar/en sentence to a driver reading Urdu. `code` is null and
     * the raw string is kept only so the failure can be reported.
     */
    @Test
    fun `a code this build does not know is null, not a crash`() {
        val error = httpError(409, """{"code":"quantum_flux","message":"..."}""")
            .toDriverApiError() as DriverApiError.Api

        assertNull(error.code)
        assertEquals("quantum_flux", error.rawCode)
    }

    /** A proxy or an HTML error page must not throw inside the error path itself. */
    @Test
    fun `a body that is not the error envelope degrades quietly`() {
        val error = httpError(502, "<html>Bad Gateway</html>").toDriverApiError() as DriverApiError.Api

        assertNull(error.code)
        assertNull(error.message)
    }

    @Test
    fun `validation field messages are carried`() {
        val error = httpError(
            422,
            """{"code":"validation_failed","message":"Invalid","errors":{"pin":["The pin must be 4 digits."]}}""",
        ).toDriverApiError() as DriverApiError.Api

        assertEquals(DriverErrorCode.ValidationFailed, error.code)
        assertEquals("The pin must be 4 digits.", error.fieldErrors["pin"])
    }

    @Test
    fun `a dead connection is offline, not a server refusal`() {
        assertTrue(UnknownHostException("no dns").toDriverApiError() is DriverApiError.Offline)
        assertTrue(SocketTimeoutException("timeout").toDriverApiError() is DriverApiError.Offline)
    }

    /**
     * kotlinx's SerializationException extends IllegalArgumentException, NOT
     * IOException. If it were classified as a network fault the app would retry
     * a permanent contract break forever and never surface it.
     */
    @Test
    fun `an undecodable body is a contract break, not a network blip`() {
        val thrown = kotlinx.serialization.SerializationException("missing field 'total'")

        assertTrue(thrown.toDriverApiError() is DriverApiError.Decode)
    }
}
