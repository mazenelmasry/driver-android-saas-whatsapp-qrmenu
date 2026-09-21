package app.qrmenu.driver.trip

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.qrmenu.driver.network.dto.DeliveredResponse
import app.qrmenu.driver.network.dto.DriverIssueCode
import app.qrmenu.driver.network.dto.DriverOrderDto
import app.qrmenu.driver.network.errors.DriverApiError
import app.qrmenu.driver.network.errors.DriverErrorCode
import app.qrmenu.driver.network.errors.toDriverApiError
import app.qrmenu.driver.ui.text.TripMoneyFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The four mandatory states (driver-ui-standards), plus a live trip's own decidable content. */
sealed interface TripPhase {
    /**
     * Fetching the trip's own data — distinct in NAME only from
     * [OfferPhase.Loading]; what it looks like on screen is `driver-ui-standards`'
     * "resuming a trip in progress" shape (a card skeleton for a screen the
     * driver has usually already seen once this shift), not the first-open shape.
     */
    data object Loading : TripPhase

    /**
     * A trip this driver holds right now. [error] is the pick-up attempt's OWN
     * inline failure — kept apart from [LoadFailed] so a failed retry never
     * throws away [order] or the driver's place on the screen.
     */
    data class Content(
        val order: DriverOrderDto,
        val isPickingUp: Boolean = false,
        val error: DriverApiError? = null,
    ) : TripPhase

    /** The trip's own GET failed outright — nothing to act on yet. */
    data class LoadFailed(val error: DriverApiError) : TripPhase
}

/** Screen 11 — collects `cash_collected` before it is sent. */
data class DeliverySheetState(
    val visible: Boolean = false,
    /** The raw text field content — pre-filled with `cash_to_collect` the instant the sheet opens. */
    val amountText: String = "",
    val note: String = "",
    /**
     * The four digits the driver typed after asking the customer. Empty by
     * default and on every fresh open of the sheet — see [codeRequired]'s own
     * doc for why the app never pre-fills or pre-shows this.
     */
    val deliveryCode: String = "",
    /**
     * 🔴 `false` until the FIRST `delivered` attempt (always sent with
     * `delivery_code = null`) comes back 422 `delivery_code_required` or
     * `delivery_code_mismatch`. The app has no wire signal that tells it ahead
     * of time whether an order carries a code (decision 48) — so rather than
     * showing a field that is irrelevant for the common case, [TripScreen]
     * shows NO code field at all until the server says one is needed, at
     * which point this flips to `true` and stays `true` for the rest of this
     * delivery attempt (dismissing the sheet resets it, since that abandons
     * the attempt entirely).
     */
    val codeRequired: Boolean = false,
    val isSubmitting: Boolean = false,
    val error: DriverApiError? = null,
)

/** Screen 12 — a problem report that never touches order status. */
data class IssueSheetState(
    val visible: Boolean = false,
    val selectedCode: DriverIssueCode? = null,
    val note: String = "",
    val isSubmitting: Boolean = false,
    val error: DriverApiError? = null,
)

data class TripUiState(
    val phase: TripPhase = TripPhase.Loading,
    val deliverySheet: DeliverySheetState = DeliverySheetState(),
    val issueSheet: IssueSheetState = IssueSheetState(),
    /**
     * One-shot: set the instant `delivered` succeeds, so [TripRoute] can show
     * what the driver just earned and then leave — this is also the exact
     * moment [TripPhase.Content.order] is rewritten via [strippedOfPii], so
     * the two always change together (see that function's own doc).
     */
    val deliveredResult: DeliveredResponse? = null,
    /** One-shot: a successful `issue` report — the sheet shows a brief confirmation, then closes itself. */
    val issueReported: Boolean = false,
)

/**
 * Screens 10–12 — the trip a driver holds between accepting an offer and
 * handing the order over.
 *
 * Owns three things a Composable must not: that [pickUp] is blocked before
 * the order is `ready` (never merely disabled on screen — a stale render must
 * not let a tap through), that every command's `Idempotency-Key` is minted
 * ONCE per driver-committed action and REUSED across every retry of that same
 * action (never regenerated on a tap the driver makes to try again), and that
 * the customer's phone number and address are stripped from [TripUiState] the
 * instant `delivered` succeeds — not eventually, not on the next screen, but
 * in the same state update that carries the ledger back.
 */
@HiltViewModel
class TripViewModel @Inject constructor(
    private val repository: TripRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(TripUiState())
    val state: StateFlow<TripUiState> = _state.asStateFlow()

    private var hasStarted = false

    // 🔴 Minted once per driver-committed action, reused across every retry of
    // THAT action, cleared only once the action succeeds (or the sheet it
    // belongs to is dismissed unsent, which means no request carrying it was
    // ever made). A fresh UUID on every tap would let a lost response's retry
    // double-credit the ledger — see [TripRepository]'s own doc.
    private var pickedUpKey: String? = null
    private var deliveredKey: String? = null
    private var issueKey: String? = null

    /**
     * 🔴 [seed] is the ASSIGNED [DriverOrderDto] `OfferRoute.onAccepted` already
     * handed the caller — when it is present this makes NO network call at
     * all, per the task brief: the accept response already carried everything
     * this screen needs. [seed] is null only when the trip screen is entered
     * without that hand-off (the app was killed mid-trip and reopened), and
     * that is the one case a GET is actually warranted — driver-ui-standards'
     * "state comes back from Room + a server refetch, not from process memory."
     */
    fun start(orderId: Long, seed: DriverOrderDto? = null) {
        if (hasStarted) return
        hasStarted = true
        if (seed != null) {
            _state.update { it.copy(phase = TripPhase.Content(order = seed)) }
        } else {
            load(orderId)
        }
    }

    fun retryLoad(orderId: Long) = load(orderId)

    private fun load(orderId: Long) {
        _state.update { it.copy(phase = TripPhase.Loading) }
        viewModelScope.launch {
            runCatching { repository.fetch(orderId) }
                .onSuccess { dto -> _state.update { it.copy(phase = TripPhase.Content(order = dto)) } }
                .onFailure { thrown ->
                    _state.update { it.copy(phase = TripPhase.LoadFailed(thrown.toDriverApiError())) }
                }
        }
    }

    // ───────────────────────────── picked-up ─────────────────────────────

    /**
     * A no-op, never a thrown error, when [TripPhase.Content.order] is not yet
     * `ready` — the button that calls this is disabled for the same reason
     * (see [TripScreen]), and this guard is what keeps a stale recomposition
     * or a fast double-tap from sneaking a call through anyway.
     */
    fun pickUp(orderId: Long) {
        val content = _state.value.phase as? TripPhase.Content ?: return
        if (content.isPickingUp || !content.order.isReadyForPickup()) return

        val key = pickedUpKey ?: UUID.randomUUID().toString().also { pickedUpKey = it }
        _state.update { it.copy(phase = content.copy(isPickingUp = true, error = null)) }
        viewModelScope.launch {
            runCatching { repository.pickedUp(orderId, key) }
                .onSuccess { dto ->
                    pickedUpKey = null
                    _state.update { it.copy(phase = TripPhase.Content(order = dto)) }
                }
                .onFailure { thrown ->
                    _state.update { current ->
                        val currentContent = current.phase as? TripPhase.Content ?: return@update current
                        current.copy(phase = currentContent.copy(isPickingUp = false, error = thrown.toDriverApiError()))
                    }
                }
        }
    }

    // ───────────────────────────── delivery sheet ─────────────────────────────

    /** Pre-fills the amount with `cash_to_collect` (contract's own words) — editable from here on. */
    fun openDeliverySheet() {
        val content = _state.value.phase as? TripPhase.Content ?: return
        deliveredKey = null
        _state.update {
            it.copy(
                deliverySheet = DeliverySheetState(
                    visible = true,
                    amountText = TripMoneyFormat.formatAmount(content.order.cashToCollect),
                    // 🔴 Asked for UP FRONT, from the order's own
                    // `requires_delivery_code`, not discovered from a
                    // rejected `delivered`. The reactive version of this put
                    // the driver at a door, refused once, only then turning
                    // to the customer to ask for a code. The server still
                    // decides; this flag only decides whether to ask.
                    codeRequired = content.order.requiresDeliveryCode,
                ),
            )
        }
    }

    fun dismissDeliverySheet() {
        deliveredKey = null
        _state.update { it.copy(deliverySheet = DeliverySheetState(visible = false)) }
    }

    fun updateDeliveryAmount(text: String) {
        _state.update { it.copy(deliverySheet = it.deliverySheet.copy(amountText = text, error = null)) }
    }

    fun updateDeliveryNote(text: String) {
        _state.update { it.copy(deliverySheet = it.deliverySheet.copy(note = text, error = null)) }
    }

    fun updateDeliveryCode(text: String) {
        _state.update { it.copy(deliverySheet = it.deliverySheet.copy(deliveryCode = text, error = null)) }
    }

    /**
     * Confirmed by a LONG PRESS on the caller's side ([TripScreen] — a tap
     * next to a moving car delivers an order that was not delivered), not by
     * anything this function is responsible for enforcing.
     *
     * `cashCollected` is `null` here whenever the order was already paid
     * online — see [DeliverySheetState] callers ([TripScreen]) for where that
     * branch is decided; this function only ever sees the number the driver
     * was shown and validated against.
     */
    fun confirmDelivery(orderId: Long, isCashOrder: Boolean, cashToCollect: Double) {
        val sheet = _state.value.deliverySheet
        if (sheet.isSubmitting) return
        val amount = sheet.amountText.toDoubleOrNull()
        val enteredCode = sheet.deliveryCode.ifBlank { null }
        val blockReason = deliveryBlockReason(
            isCashOrder = isCashOrder,
            cashToCollect = cashToCollect,
            enteredAmount = amount,
            note = sheet.note,
            codeRequired = sheet.codeRequired,
            enteredCode = enteredCode,
        )
        if (blockReason != null) return

        val key = deliveredKey ?: UUID.randomUUID().toString().also { deliveredKey = it }
        _state.update { it.copy(deliverySheet = it.deliverySheet.copy(isSubmitting = true, error = null)) }
        viewModelScope.launch {
            runCatching {
                repository.delivered(
                    orderId = orderId,
                    idempotencyKey = key,
                    cashCollected = if (isCashOrder) amount else null,
                    deliveryCode = enteredCode,
                    note = sheet.note.ifBlank { null },
                )
            }
                .onSuccess { response ->
                    deliveredKey = null
                    // 🔴 THE PRIVACY GUARANTEE, in the same update as the
                    // result: the moment this driver's job with this customer
                    // is over, the phone number and address leave state —
                    // see [strippedOfPii]'s own doc.
                    _state.update {
                        it.copy(
                            phase = TripPhase.Content(order = response.order.strippedOfPii()),
                            deliverySheet = DeliverySheetState(visible = false),
                            deliveredResult = response,
                        )
                    }
                }
                .onFailure { thrown ->
                    val apiError = thrown.toDriverApiError()
                    val failureCode = (apiError as? DriverApiError.Api)?.code
                    val isAboutTheDeliveryCode = failureCode == DriverErrorCode.DeliveryCodeRequired ||
                        failureCode == DriverErrorCode.DeliveryCodeMismatch
                    if (isAboutTheDeliveryCode) {
                        // 🔴 Nothing was applied server-side — a 422 is a pure
                        // rejection, never a partial write — and the retry
                        // that follows carries a DIFFERENT body (it adds or
                        // fixes `delivery_code`). Reusing this key for that
                        // different request risks an idempotency layer either
                        // rejecting the body mismatch outright or, worse,
                        // replaying the cached 422 forever even after the
                        // driver enters the right code. Minting a fresh key
                        // here costs nothing (nothing to double-apply) and
                        // avoids both. Contrast the generic branch below,
                        // where the key IS kept: an offline/500 failure means
                        // the app cannot tell whether the server actually
                        // applied the SAME, unchanged request, so the retry
                        // must still look identical to the server.
                        deliveredKey = null
                    }
                    _state.update {
                        it.copy(
                            deliverySheet = it.deliverySheet.copy(
                                isSubmitting = false,
                                error = apiError,
                                codeRequired = it.deliverySheet.codeRequired || isAboutTheDeliveryCode,
                                // A wrong code is cleared so the driver retypes
                                // it rather than re-submitting the same wrong
                                // digits by mistake — see driver-ui-standards.
                                // `delivery_code_required` leaves it as-is:
                                // there was nothing to clear, the field is
                                // only just now appearing.
                                deliveryCode = if (failureCode == DriverErrorCode.DeliveryCodeMismatch) {
                                    ""
                                } else {
                                    it.deliverySheet.deliveryCode
                                },
                            ),
                        )
                    }
                }
        }
    }

    /** [TripRoute] calls this once it has shown the earnings and is ready to navigate away. */
    fun consumeDeliveredResult() {
        _state.update { it.copy(deliveredResult = null) }
    }

    // ───────────────────────────── issue sheet ─────────────────────────────

    fun openIssueSheet() {
        issueKey = null
        _state.update { it.copy(issueSheet = IssueSheetState(visible = true)) }
    }

    fun dismissIssueSheet() {
        issueKey = null
        _state.update { it.copy(issueSheet = IssueSheetState(visible = false)) }
    }

    fun selectIssueCode(code: DriverIssueCode) {
        _state.update { it.copy(issueSheet = it.issueSheet.copy(selectedCode = code, error = null)) }
    }

    fun updateIssueNote(text: String) {
        _state.update { it.copy(issueSheet = it.issueSheet.copy(note = text)) }
    }

    /**
     * Deliberately does not touch [TripPhase.Content.order] or its status on
     * success — reporting a problem is information for the restaurant, never
     * a state change the driver gets to decide (contract's own words on
     * `issue`; also CLAUDE.md's "لا يستطيع السائق إلغاء طلب").
     */
    fun submitIssue(orderId: Long) {
        val sheet = _state.value.issueSheet
        val code = sheet.selectedCode ?: return
        if (sheet.isSubmitting) return

        val key = issueKey ?: UUID.randomUUID().toString().also { issueKey = it }
        _state.update { it.copy(issueSheet = it.issueSheet.copy(isSubmitting = true, error = null)) }
        viewModelScope.launch {
            runCatching { repository.issue(orderId, key, code.wire, sheet.note.ifBlank { null }) }
                .onSuccess {
                    issueKey = null
                    _state.update { it.copy(issueSheet = IssueSheetState(visible = false), issueReported = true) }
                }
                .onFailure { thrown ->
                    _state.update {
                        it.copy(issueSheet = it.issueSheet.copy(isSubmitting = false, error = thrown.toDriverApiError()))
                    }
                }
        }
    }

    fun consumeIssueReported() {
        _state.update { it.copy(issueReported = false) }
    }
}
