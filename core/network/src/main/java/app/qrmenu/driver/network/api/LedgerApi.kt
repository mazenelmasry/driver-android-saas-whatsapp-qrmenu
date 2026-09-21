package app.qrmenu.driver.network.api

import app.qrmenu.driver.network.dto.LedgerResponse
import app.qrmenu.driver.network.dto.SettlementsResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * The driver's own book — what they earned, what cash they hold, and the net.
 * Somebody paid per delivery does not use an app that hides their pay.
 *
 * `companyId` is REQUIRED on [ledger]: the book is per (driver × company) and
 * never nets across restaurants (binding rule 2), which is also what keeps a
 * multi-country driver's currencies from being added together.
 */
interface LedgerApi {

    @GET("driver/ledger")
    suspend fun ledger(@Query("company_id") companyId: Long): LedgerResponse

    @GET("driver/ledger/settlements")
    suspend fun settlements(@Query("company_id") companyId: Long? = null): SettlementsResponse
}
