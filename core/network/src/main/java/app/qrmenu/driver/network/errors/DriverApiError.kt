package app.qrmenu.driver.network.errors

import java.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException

/**
 * What every network call fails with, once classified.
 *
 * The point of having a type at all is that no screen ever renders a raw
 * OkHttp/Retrofit string: "failed to connect to app.meniura.com … after 15000ms"
 * is technical English a driver cannot act on, and it is not in their language.
 */
sealed class DriverApiError {

    /**
     * The server refused and named a reason. [code] is null when the server sent
     * a code this build does not know — the UI must then fall back to a generic
     * translated line and report it, NOT print [message].
     */
    data class Api(
        val httpStatus: Int,
        val code: DriverErrorCode?,
        /** ar/en, for logs and as a last resort only. Never branch on it. */
        val message: String?,
        val rawCode: String?,
        val fieldErrors: Map<String, String> = emptyMap(),
    ) : DriverApiError()

    /** No usable connection: DNS, connect, timeout. Retryable. */
    data object Offline : DriverApiError()

    /**
     * The body could not be decoded — a PERMANENT contract break (the backend
     * diverged from these DTOs), never a dropped packet. Kept apart from
     * [Offline] because retrying it forever hides the break; the contract mirror
     * test exists to catch this before a driver does.
     */
    data class Decode(val cause: Throwable) : DriverApiError()

    data class Unknown(val cause: Throwable) : DriverApiError()

    /**
     * A phone-verification failure Firebase itself reported on-device — never
     * a server round-trip, so it has no [DriverErrorCode] of its own (that
     * enum is the FROZEN mirror of `openapi/driver.v1.yaml`'s `ErrorCode`,
     * see [DriverErrorCode]'s doc; a client-local reason must not be added
     * there or [BackendContractMirrorTest][app.qrmenu.driver.network.dto.BackendContractMirrorTest]
     * fails the moment it drifts from that list).
     *
     * [PhoneVerificationFailureReason] carries just enough for `:core:ui` to
     * pick a sentence (`DriverErrorText`), the same way [Api.code] does for a
     * server rejection.
     */
    data class PhoneVerification(val reason: PhoneVerificationFailureReason) : DriverApiError()
}

/** See [DriverApiError.PhoneVerification]. */
enum class PhoneVerificationFailureReason {
    /**
     * Play Integrity AND reCAPTCHA both failed to attest this app/device
     * (`ERROR_MISSING_CLIENT_IDENTIFIER`, `ERROR_INVALID_APP_CREDENTIAL`).
     * Usually transient — a stale Play Integrity token, a flaky reCAPTCHA
     * page — so sending again is the right advice.
     */
    Unavailable,

    /**
     * `ERROR_APP_NOT_AUTHORIZED` — the SHA fingerprint/package this build
     * ships does not match what is registered with Firebase. Retrying
     * changes nothing; only a config fix (by us) does.
     */
    ConfigError,

    /** `ERROR_WEB_CONTEXT_CANCELED` — the driver closed the reCAPTCHA tab/page themselves. */
    Cancelled,

    /**
     * [com.google.firebase.FirebaseTooManyRequestsException] — Firebase has
     * blocked ALL verification requests from this device for a period
     * (hours), distinct from [DriverErrorCode.TooManyAttempts]'s "wait a
     * little and try again" (a single code's short-lived attempt limit).
     */
    Blocked,
}

/**
 * Classifies a thrown [Throwable] from a Retrofit call.
 *
 * kotlinx's `SerializationException` extends `IllegalArgumentException`, NOT
 * `IOException`, so it must be tested before the IO branch or it would be
 * mistaken for a network blip and retried forever.
 */
fun Throwable.toDriverApiError(): DriverApiError = when {
    this is HttpException -> {
        val envelope = ErrorEnvelopeParser.parse(
            runCatching { response()?.errorBody()?.string() }.getOrNull(),
        )
        DriverApiError.Api(
            httpStatus = code(),
            code = DriverErrorCode.fromWire(envelope.code),
            message = envelope.message,
            rawCode = envelope.code,
            fieldErrors = envelope.fieldErrors,
        )
    }
    this is kotlinx.serialization.SerializationException -> DriverApiError.Decode(this)
    this is IOException -> DriverApiError.Offline
    else -> DriverApiError.Unknown(this)
}

/**
 * Reads the `{code, message, errors}` envelope the contract guarantees on every
 * 4xx. Tolerant on purpose: a proxy or an HTML error page must degrade to "no
 * code" rather than throw inside the error path.
 */
object ErrorEnvelopeParser {

    data class Envelope(
        val code: String? = null,
        val message: String? = null,
        val fieldErrors: Map<String, String> = emptyMap(),
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(body: String?): Envelope {
        if (body.isNullOrBlank()) return Envelope()
        return runCatching {
            val root = json.parseToJsonElement(body).jsonObject
            Envelope(
                code = root["code"]?.jsonPrimitive?.contentOrNullSafe(),
                message = root["message"]?.jsonPrimitive?.contentOrNullSafe(),
                fieldErrors = (root["errors"] as? JsonObject)?.mapNotNull { (field, value) ->
                    firstMessage(value)?.let { field to it }
                }?.toMap().orEmpty(),
            )
        }.getOrElse { Envelope() }
    }

    private fun firstMessage(value: kotlinx.serialization.json.JsonElement): String? = runCatching {
        when (value) {
            is kotlinx.serialization.json.JsonArray -> value.firstOrNull()?.jsonPrimitive?.contentOrNullSafe()
            is kotlinx.serialization.json.JsonPrimitive -> value.contentOrNullSafe()
            else -> null
        }
    }.getOrNull()

    private fun kotlinx.serialization.json.JsonPrimitive.contentOrNullSafe(): String? =
        if (this is kotlinx.serialization.json.JsonNull) null else content.takeIf { it.isNotBlank() }
}
