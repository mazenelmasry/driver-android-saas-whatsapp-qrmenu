package app.qrmenu.driver.wallet

import app.qrmenu.driver.network.api.AuthApi
import app.qrmenu.driver.network.api.LedgerApi
import app.qrmenu.driver.network.dto.LedgerResponse
import app.qrmenu.driver.network.dto.RestaurantLinkDto
import app.qrmenu.driver.network.dto.SettlementDto
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One row of the restaurant switcher.
 *
 * 🔴 [currency] follows the company, never the device or a remembered
 * default (binding rule 2) — it is what [WalletFormatting] uses to render
 * every figure for this restaurant's book.
 */
data class RestaurantOption(
    val companyId: Long,
    val name: String,
    val logo: String?,
    val currency: String?,
)

fun RestaurantLinkDto.toOption(): RestaurantOption =
    RestaurantOption(companyId = company.id, name = company.name, logo = company.logo, currency = company.currency)

/**
 * The linked restaurants (for the switcher) plus, separately, ONE restaurant's
 * book at a time.
 *
 * 🔴 Never a combined figure across restaurants (binding rule 2, CLAUDE.md's
 * «الدفتر المالى»): [ledger] takes a single `companyId` and returns that
 * company's [LedgerResponse] alone — there is no method on this class that
 * could sum two, and the screen never asks for more than one at once.
 */
@Singleton
class WalletRepository @Inject constructor(
    private val authApi: AuthApi,
    private val ledgerApi: LedgerApi,
) {
    /** The driver's linked restaurants, for the switcher — never their ledger data. */
    suspend fun restaurants(): List<RestaurantOption> = authApi.me().restaurants.map { it.toOption() }

    /** ONE restaurant's book — summary + entries, newest first per the contract. */
    suspend fun ledger(companyId: Long): LedgerResponse = ledgerApi.ledger(companyId)

    /**
     * Settlements already made with the driver. [companyId] narrows to one
     * restaurant's history when the switcher has a selection; `null` (the
     * contract's optional param) returns every restaurant's settlements
     * together — each row still carries its own [SettlementDto.currency], so
     * this is a list of separate figures, never a summed one.
     */
    suspend fun settlements(companyId: Long?): List<SettlementDto> = ledgerApi.settlements(companyId).data
}
