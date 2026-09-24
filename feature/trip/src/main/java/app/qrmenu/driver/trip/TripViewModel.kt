package app.qrmenu.driver.trip

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.qrmenu.driver.database.entity.DriverActionType
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

    /**
     * 🔴 Terminal: the restaurant ended this order while the driver was still
     * holding it. [order] is kept (not just the outcome) because the outcome
     * screen still needs to let the driver call the branch — the exact same
     * mechanism [TripContent]'s quick-actions row uses, not a second one —
     * and needs [DriverOrderDto.pickedUpAt] to say the one thing that
     * actually differs: whether there is food in the driver's hand that now
     * has to go back, or nothing to return at all. See [TripOutcome]'s own
     * doc for why this can happen at all.
     */
    data class Resolved(val order: DriverOrderDto, val outcome: TripOutcome) : TripPhase
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
    /**
     * 🔴 The backend permanently answers `too_many_attempts` for THIS order
     * once five wrong codes have been tried — only the restaurant waiving the
     * code (or, on the next load/poll, the order's own
     * `requires_delivery_code` flipping to `false` once they do) lifts it.
     * `false` on every fresh open of the sheet, same as [deliveryCode] — a
     * previous delivery attempt's lockout must not haunt a driver who just
     * reopened the trip. Set once by [TripViewModel.confirmDelivery] and left
     * `true` from then on: [DeliverySheet] disables the confirm button the
     * instant this is `true`, so no later call can come back and unset it
     * within the same sheet session (see that Composable's own doc).
     */
    val deliveryLocked: Boolean = false,
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
     * 🔴 The in-memory `var`s above only survive within ONE process lifetime.
     * A driver who tapped an action, had the app killed before the response
     * came back (a real crash, low-memory reclaim, or just swiping the app
     * away), and reopened the trip used to mint a BRAND NEW key here — even
     * though [TripRepository.enqueueAction] had already written a row for the
     * first tap to Room before the process died. If that first request had
     * only been slow rather than lost, both eventually reach the server: two
     * `delivered` calls, two ledger credits, for one delivery.
     *
     * Called before every fresh mint from here on: it asks Room whether a row
     * for this exact (order, action) is still queued from before the process
     * died and, if so, hands back ITS key instead of orphaning it under a
     * second, abandoned key. Only a cache miss (nothing queued) falls through
     * to minting a genuinely new [UUID].
     */
    private suspend fun resolveKey(
        cached: String?,
        orderId: Long,
        type: DriverActionType,
        remember: (String) -> Unit,
    ): String {
        cached?.let { return it }
        val key = repository.pendingKeyFor(orderId, type) ?: UUID.randomUUID().toString()
        remember(key)
        return key
    }

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
            _state.update { it.copy(phase = seed.toTripPhase()) }
        } else {
            load(orderId)
        }
        // 🔴 Drains whatever this driver's PREVIOUS session left queued (app
        // was killed mid-delivery, offline the whole time) — see
        // [TripRepository.flushPending]'s own doc. Fire-and-forget on
        // purpose: a queued row from an earlier trip must never block THIS
        // screen's own loading state, and a failure here (still offline)
        // just leaves the row queued for the next opportunity.
        flushQueuedActions()
    }

    fun retryLoad(orderId: Long) = load(orderId)

    /**
     * A successful command is proof this device is online RIGHT NOW — the
     * best signal this screen has without a connectivity listener or
     * WorkManager wired at the app level (see [TripRepository.flushPending]'s
     * own doc). Launched on its own, never awaited by the caller: a queued
     * row from an earlier failure must not delay the state update the driver
     * is already looking at.
     */
    private fun flushQueuedActions() {
        viewModelScope.launch {
            runCatching { repository.flushPending() }
        }
    }

    private fun load(orderId: Long) {
        _state.update { it.copy(phase = TripPhase.Loading) }
        viewModelScope.launch {
            runCatching { repository.fetch(orderId) }
                .onSuccess { dto -> _state.update { it.copy(phase = dto.toTripPhase()) } }
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
    /**
     * The resumed-screen poll (see [TripRoute]) — how a driver STANDING ON
     * this screen finds out the restaurant ended their order.
     *
     * 🔴 Without this the whole cancelled-trip screen was decorative. The
     * trip loaded exactly once and never asked again, so the one person who
     * most needs to know — someone holding the food, already driving — would
     * have sat looking at a live-looking trip screen for an order that no
     * longer existed, and found out only by leaving and coming back.
     *
     * Deliberately narrower than [load]:
     *
     *  · It NEVER shows a spinner. Replacing a trip the driver is reading
     *    with a skeleton every few seconds, on a phone in a car mount, would
     *    be its own bug.
     *  · A failure is SILENT. The driver's own actions («استلمت»/«سلّمت»)
     *    surface their own errors; a background fetch that could not reach
     *    the server must not paint a red banner over a trip that is
     *    proceeding perfectly well, and must certainly not imply the trip is
     *    in doubt.
     *  · It only ever moves the screen INTO [TripPhase.Resolved]. A poll is
     *    not allowed to rebuild Content underneath a driver mid-interaction
     *    — that would clear a half-typed cash amount or close a sheet they
     *    had open. The ONE thing worth interrupting them for is "this order
     *    is over", and that is the only thing this can do.
     */
    fun refreshQuietly(orderId: Long) {
        // Nothing to interrupt if they are not actually on a live trip.
        if (_state.value.phase !is TripPhase.Content) {
            return
        }

        viewModelScope.launch {
            runCatching { repository.fetch(orderId) }
                .onSuccess { dto ->
                    val phase = dto.toTripPhase()

                    if (phase is TripPhase.Resolved) {
                        _state.update { it.copy(phase = phase) }
                    }
                }
        }
    }

    fun pickUp(orderId: Long) {
        val content = _state.value.phase as? TripPhase.Content ?: return
        if (content.isPickingUp || !content.order.isReadyForPickup()) return

        _state.update { it.copy(phase = content.copy(isPickingUp = true, error = null)) }
        viewModelScope.launch {
            val key = resolveKey(pickedUpKey, orderId, DriverActionType.PickedUp) { pickedUpKey = it }
            runCatching { repository.pickedUp(orderId, key) }
                .onSuccess { dto ->
                    pickedUpKey = null
                    _state.update { it.copy(phase = dto.toTripPhase()) }
                    flushQueuedActions()
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
     * Confirmed by a plain tap on the caller's side ([TripScreen] — see that
     * Composable's own doc on why the long press it used to require was
     * dropped), not by anything this function is responsible for enforcing.
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

        _state.update { it.copy(deliverySheet = it.deliverySheet.copy(isSubmitting = true, error = null)) }
        viewModelScope.launch {
            val key = resolveKey(deliveredKey, orderId, DriverActionType.Delivered) { deliveredKey = it }
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
                    flushQueuedActions()
                }
                .onFailure { thrown ->
                    val apiError = thrown.toDriverApiError()
                    val failureCode = (apiError as? DriverApiError.Api)?.code
                    val isAboutTheDeliveryCode = failureCode == DriverErrorCode.DeliveryCodeRequired ||
                        failureCode == DriverErrorCode.DeliveryCodeMismatch
                    // 🔴 The order's five-attempt budget on the SERVER, not a
                    // client-side retry limit — from here on the server keeps
                    // answering `too_many_attempts` for this order no matter
                    // what is typed, so nothing is gained by leaving the
                    // field open for another try. See [DeliverySheetState.deliveryLocked].
                    val isTooManyAttempts = failureCode == DriverErrorCode.TooManyAttempts
                    if (isAboutTheDeliveryCode || isTooManyAttempts) {
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
                                deliveryLocked = it.deliverySheet.deliveryLocked || isTooManyAttempts,
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

        _state.update { it.copy(issueSheet = it.issueSheet.copy(isSubmitting = true, error = null)) }
        viewModelScope.launch {
            val key = resolveKey(issueKey, orderId, DriverActionType.Issue) { issueKey = it }
            runCatching { repository.issue(orderId, key, code.wire, sheet.note.ifBlank { null }) }
                .onSuccess {
                    issueKey = null
                    _state.update { it.copy(issueSheet = IssueSheetState(visible = false), issueReported = true) }
                    flushQueuedActions()
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

    /**
     * 🔴 THE one decision point (task brief): every place this ViewModel
     * receives a fresh [DriverOrderDto] for the trip it is displaying — the
     * accept hand-off, a fresh GET, a successful pick-up — routes through
     * here rather than each hand-rolling its own "is this still a trip?"
     * check. A driver observing the restaurant's cancel/reject from ANY of
     * those three moments must land on the exact same outcome screen, never
     * on [TripPhase.Content] rendering a dead order's action buttons.
     */
    private fun DriverOrderDto.toTripPhase(): TripPhase =
        toTripOutcome()?.let { TripPhase.Resolved(order = this, outcome = it) } ?: TripPhase.Content(order = this)
}

/** The resumed-trip poll interval — how quickly a cancelled order reaches the driver holding it. */
internal const val TRIP_POLL_INTERVAL_MS = 15_000L

/** Suspends forever, calling [onTick] every [TRIP_POLL_INTERVAL_MS] — cancelled with its scope. */
internal suspend fun pollTripForever(onTick: () -> Unit) {
    while (true) {
        kotlinx.coroutines.delay(TRIP_POLL_INTERVAL_MS)
        onTick()
    }
}
