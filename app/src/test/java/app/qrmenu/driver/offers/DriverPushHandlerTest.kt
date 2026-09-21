package app.qrmenu.driver.offers

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import app.qrmenu.driver.alerts.OfferAlarm
import app.qrmenu.driver.alerts.OfferNotifier
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import app.qrmenu.driver.push.OfferPushPayload

/**
 * `Intent` construction and `PendingIntent.getActivity` are real Android
 * framework calls even in a "pure JVM" unit test — the android.jar stub
 * throws on any method invocation it does not recognise as mocked. Rather
 * than pull in Robolectric for what is otherwise pure decision logic, both
 * are intercepted at the bytecode level via mockk's constructor/static
 * mocking (`mockkConstructor` / `mockkStatic`), which works without a device
 * or an Android runtime — the same technique this project would need for
 * any other Android-framework-touching class under test.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DriverPushHandlerTest {

    private val dispatcher = StandardTestDispatcher()

    private lateinit var context: Context
    private lateinit var offerGate: OfferGate
    private lateinit var offerCoordinator: OfferCoordinator
    private lateinit var alarm: OfferAlarm
    private lateinit var notifier: OfferNotifier
    private lateinit var deviceTokenRegistrar: DeviceTokenRegistrar
    private lateinit var handler: DriverPushHandler

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)

        context = mockk(relaxed = true)
        offerGate = OfferGate()
        offerCoordinator = OfferCoordinator()
        alarm = mockk(relaxed = true)
        notifier = mockk(relaxed = true)
        deviceTokenRegistrar = mockk(relaxed = true)

        mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().setFlags(any()) } returns mockk(relaxed = true)

        mockkStatic(PendingIntent::class)
        every {
            PendingIntent.getActivity(any(), any(), any(), any())
        } returns mockk(relaxed = true)

        // This class's own `Log.i`/`Log.w` calls are real android.util.Log
        // invocations too — same android.jar stub problem as Intent/PendingIntent.
        mockkStatic(Log::class)
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0

        handler = DriverPushHandler(context, offerGate, offerCoordinator, alarm, notifier, deviceTokenRegistrar)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun payload(
        orderId: Long = 1L,
        offerId: Long = 100L,
        expiresAt: Instant = Instant.now().plusSeconds(45),
    ) = OfferPushPayload(orderId = orderId, offerId = offerId, wave = 1, expiresAt = expiresAt)

    @Test
    fun `an expired payload rings nothing`() {
        val expired = payload(expiresAt = Instant.now().minus(1, ChronoUnit.SECONDS))

        handler.onOfferPush(expired)

        verify(exactly = 0) { alarm.start() }
        verify(exactly = 0) { notifier.notifyOffer(any(), any(), any(), any(), any()) }
        assert(offerCoordinator.pending.value == null)
    }

    @Test
    fun `a live payload rings exactly once`() {
        val live = payload()

        handler.onOfferPush(live)

        verify(exactly = 1) { alarm.start() }
        verify(exactly = 1) { notifier.notifyOffer(any(), any(), any(), any(), any()) }
        assert(offerCoordinator.pending.value == PendingOffer(orderId = 1L, offerId = 100L))
    }

    @Test
    fun `a payload for an offer the poll already raised rings nothing`() {
        // Simulates the poll arm winning the race first.
        offerGate.shouldRaise(offerId = 100L)

        handler.onOfferPush(payload(offerId = 100L))

        verify(exactly = 0) { alarm.start() }
        verify(exactly = 0) { notifier.notifyOffer(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a second push for the same offer id rings only once`() {
        val live = payload()

        handler.onOfferPush(live)
        handler.onOfferPush(live)

        verify(exactly = 1) { alarm.start() }
        verify(exactly = 1) { notifier.notifyOffer(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a new wave with a fresh offer id for the same order rings again after resolve`() {
        val firstWave = payload(orderId = 1L, offerId = 100L)
        handler.onOfferPush(firstWave)
        offerGate.resolve(offerId = 100L)

        val secondWave = payload(orderId = 1L, offerId = 200L)
        handler.onOfferPush(secondWave)

        verify(exactly = 2) { alarm.start() }
        verify(exactly = 2) { notifier.notifyOffer(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `onTokenRefreshed forwards to the device token registrar`() = runTest(dispatcher) {
        handler.onTokenRefreshed("new-token")
        dispatcher.scheduler.advanceUntilIdle()

        io.mockk.coVerify { deviceTokenRegistrar.onTokenRefreshed("new-token") }
    }
}
