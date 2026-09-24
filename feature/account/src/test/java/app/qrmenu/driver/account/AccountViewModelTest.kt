package app.qrmenu.driver.account

import app.qrmenu.driver.database.dao.DriverActionOutboxDao
import app.qrmenu.driver.database.dao.NotificationHistoryDao
import app.qrmenu.driver.datastore.LocaleManager
import app.qrmenu.driver.datastore.TokenStore
import app.qrmenu.driver.datastore.UiScale
import app.qrmenu.driver.datastore.UiScaleStore
import app.qrmenu.driver.network.api.AuthApi
import app.qrmenu.driver.network.dto.AcceptedDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Two shared-device fixes: signing out purges `notification_history` (a
 * device's next driver must not see the previous driver's offers), and the
 * sign-out dialog can tell the driver whether THEY still have unsent trip
 * actions queued. See [AccountViewModel.signOut] and
 * [AccountViewModel.hasUnsentActions]'s own docs.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private lateinit var authApi: AuthApi
    private lateinit var tokenStore: TokenStore
    private lateinit var localeManager: LocaleManager
    private lateinit var uiScaleStore: UiScaleStore
    private lateinit var notificationHistoryDao: NotificationHistoryDao
    private lateinit var outboxDao: DriverActionOutboxDao

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        authApi = mockk()
        coEvery { authApi.me() } throws IllegalStateException("not needed for these tests")
        coEvery { authApi.accountDeletionRequest() } throws IllegalStateException("not needed")
        coEvery { authApi.logout() } returns AcceptedDto(ok = true)

        tokenStore = mockk(relaxed = true)
        every { tokenStore.driverId } returns MutableStateFlow(42L)

        localeManager = mockk(relaxed = true)
        every { localeManager.language } returns MutableStateFlow(null)
        every { localeManager.default } returns "ar"

        uiScaleStore = mockk(relaxed = true)
        every { uiScaleStore.scale } returns MutableStateFlow(UiScale.Default)

        notificationHistoryDao = mockk(relaxed = true)
        outboxDao = mockk()
        every { outboxDao.observePendingCountForDriver(any()) } returns flowOf(0)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(): AccountViewModel = AccountViewModel(
        authApi = authApi,
        tokenStore = tokenStore,
        localeManager = localeManager,
        uiScaleStore = uiScaleStore,
        notificationHistoryDao = notificationHistoryDao,
        outboxDao = outboxDao,
    )

    @Test
    fun `signing out purges notification history so the next driver on this device sees none of it`() = runTest(dispatcher) {
        val model = viewModel()
        var signedOut = false

        model.signOut { signedOut = true }
        dispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { notificationHistoryDao.clearAll() }
        assertTrue(signedOut)
    }

    @Test
    fun `signing out purges notification history even when the server logout call fails`() = runTest(dispatcher) {
        coEvery { authApi.logout() } throws IllegalStateException("dead zone")
        val model = viewModel()
        var signedOut = false

        model.signOut { signedOut = true }
        dispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { notificationHistoryDao.clearAll() }
        assertTrue("a driver on a dead network must still get off a shared phone", signedOut)
    }

    @Test
    fun `hasUnsentActions is scoped to the currently signed-in driver`() = runTest(dispatcher) {
        every { outboxDao.observePendingCountForDriver(42L) } returns flowOf(3)

        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(model.hasUnsentActions.value)
    }
}
