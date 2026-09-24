package app.qrmenu.driver.auth

import android.app.Activity
import androidx.lifecycle.SavedStateHandle
import app.qrmenu.driver.auth.phone.DriverPhoneVerifier
import app.qrmenu.driver.auth.phone.PhoneVerificationOutcome
import app.qrmenu.driver.datastore.TokenStore
import app.qrmenu.driver.network.api.AuthApi
import app.qrmenu.driver.network.dto.AcceptedDto
import app.qrmenu.driver.network.dto.DriverDto
import app.qrmenu.driver.network.dto.RequestOtpRequest
import app.qrmenu.driver.network.dto.VerifyOtpResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Process death mid-OTP: [AuthStepSaver] already carries the phone back
 * through `rememberSaveable`, so the screen is re-entered for the same
 * number — but a fresh [OtpViewModel] instance starts with an empty
 * `verificationId` unless it is restored from [SavedStateHandle]. These
 * prove the three shapes that restoration can take.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OtpViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val phone = "+201234567890"

    private lateinit var authApi: AuthApi
    private lateinit var tokenStore: TokenStore
    private lateinit var phoneVerifier: DriverPhoneVerifier
    private lateinit var activity: Activity

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        authApi = mockk()
        tokenStore = mockk(relaxed = true)
        phoneVerifier = mockk()
        activity = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(savedStateHandle: SavedStateHandle = SavedStateHandle()): OtpViewModel =
        OtpViewModel(
            authApi = authApi,
            tokenStore = tokenStore,
            phoneVerifier = phoneVerifier,
            savedStateHandle = savedStateHandle,
        )

    @Test
    fun `a fresh screen with nothing saved requests a code as normal`() = runTest(dispatcher) {
        coEvery { authApi.requestOtp(RequestOtpRequest(phone)) } returns AcceptedDto(ok = true)
        every { phoneVerifier.verificationEvents(activity, phone) } returns
            flowOf(PhoneVerificationOutcome.CodeSent("vid-1"))

        val model = viewModel()
        model.start(phone, activity) { _, _ -> }
        dispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { authApi.requestOtp(RequestOtpRequest(phone)) }
        assertEquals("vid-1", model.state.value.verificationId)
        assertFalse(model.state.value.needsFreshCode)
    }

    /**
     * The core of the fix: a `verificationId` for THIS exact phone was
     * already in the saved state (Firebase already sent a code before the
     * process died) — restoring it must be enough to let `verify()` work,
     * with no second SMS spent.
     */
    @Test
    fun `a restored verificationId for the same phone is reused, no new code requested`() = runTest(dispatcher) {
        val restored = SavedStateHandle(mapOf("otp_pending_phone" to phone, "otp_verification_id" to "vid-restored"))

        val model = viewModel(restored)
        model.start(phone, activity) { _, _ -> }
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("vid-restored", model.state.value.verificationId)
        assertFalse(model.state.value.needsFreshCode)
        coVerify(exactly = 0) { authApi.requestOtp(any()) }
    }

    /**
     * The unlucky case: a request for this phone was in flight when the
     * process died, so there is a phone but no id — neither "resend
     * automatically" (might double-send) nor "do nothing" (strands the
     * driver) is safe, so the screen must say so explicitly.
     */
    @Test
    fun `a restored phone with no verificationId asks for a fresh code instead of guessing`() = runTest(dispatcher) {
        val restored = SavedStateHandle(mapOf("otp_pending_phone" to phone))

        val model = viewModel(restored)
        model.start(phone, activity) { _, _ -> }
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(model.state.value.needsFreshCode)
        assertNull(model.state.value.verificationId)
        coVerify(exactly = 0) { authApi.requestOtp(any()) }
    }

    /** A restored id for a DIFFERENT phone (driver tapped "change number") must never be offered up. */
    @Test
    fun `a restored verificationId for a different phone is discarded and a new code is requested`() =
        runTest(dispatcher) {
            val restored =
                SavedStateHandle(mapOf("otp_pending_phone" to "+201111111111", "otp_verification_id" to "vid-old"))
            coEvery { authApi.requestOtp(RequestOtpRequest(phone)) } returns AcceptedDto(ok = true)
            every { phoneVerifier.verificationEvents(activity, phone) } returns
                flowOf(PhoneVerificationOutcome.CodeSent("vid-2"))

            val model = viewModel(restored)
            model.start(phone, activity) { _, _ -> }
            dispatcher.scheduler.advanceUntilIdle()

            coVerify(exactly = 1) { authApi.requestOtp(RequestOtpRequest(phone)) }
            assertEquals("vid-2", model.state.value.verificationId)
            assertFalse(model.state.value.needsFreshCode)
        }

    /** A `CodeSent` event must be written back to the handle, not just to in-memory state. */
    @Test
    fun `a code being sent persists the verificationId into the saved state handle`() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        coEvery { authApi.requestOtp(RequestOtpRequest(phone)) } returns AcceptedDto(ok = true)
        val events = MutableSharedFlow<PhoneVerificationOutcome>(replay = 1)
        every { phoneVerifier.verificationEvents(activity, phone) } returns events

        val model = viewModel(handle)
        model.start(phone, activity) { _, _ -> }
        events.emit(PhoneVerificationOutcome.CodeSent("vid-live"))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("vid-live", handle.get<String>("otp_verification_id"))
        assertEquals(phone, handle.get<String>("otp_pending_phone"))
    }

    /** Once verification actually succeeds, the spent id must not be left around to be "restored" later. */
    @Test
    fun `a successful verification clears the saved verification state`() = runTest(dispatcher) {
        val handle = SavedStateHandle(mapOf("otp_pending_phone" to phone, "otp_verification_id" to "vid-restored"))
        coEvery {
            authApi.verifyOtp(match { it.phone == phone && it.firebaseToken == "id-token" })
        } returns VerifyOtpResponse(
            token = "session-token",
            expiresAt = null,
            driver = DriverDto(id = 1, name = "Driver", phone = phone, isActive = true, isOnline = false),
            restaurants = emptyList(),
            needsPassword = true,
        )
        coEvery { phoneVerifier.confirmCode("vid-restored", "123456") } returns Result.success("id-token")

        val model = viewModel(handle)
        model.start(phone, activity) { _, _ -> }
        model.onCodeChange("123456")
        model.verify(phone)
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(handle.get<String>("otp_verification_id"))
        assertNull(handle.get<String>("otp_pending_phone"))
    }
}
