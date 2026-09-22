package app.qrmenu.driver.updater

import app.qrmenu.driver.network.api.AuthApi
import app.qrmenu.driver.network.dto.AppVersionDto
import io.mockk.coEvery
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppVersionRepositoryTest {

    @Test
    fun `a successful call returns the dto`() = runTest {
        val authApi = mockk<AuthApi>()
        val dto = AppVersionDto(minVersionCode = 10_000, latestVersionCode = 10_100)
        coEvery { authApi.appVersion() } returns dto

        val result = AppVersionRepository(authApi).check()

        assertEquals(dto, result)
    }

    @Test
    fun `a failed call never throws — it returns null, so a lost connection cannot block anything`() = runTest {
        val authApi = mockk<AuthApi>()
        coEvery { authApi.appVersion() } throws IOException("no connection")

        val result = AppVersionRepository(authApi).check()

        assertNull(result)
    }
}
