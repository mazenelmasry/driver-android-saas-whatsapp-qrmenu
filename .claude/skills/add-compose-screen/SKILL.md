---
name: add-compose-screen
description: Use this skill when adding a new Compose screen inside an existing feature module of the driver app. Triggers — "add a screen for X", "build the Y page", "create the Z UI". Enforces the project's MVI/UDF state pattern, five-locale (ar/en/ur/bn/hi) string handling, and phone-first 64dp touch targets — NOT the two-locale/56dp POS convention.
---

# Skill: add-compose-screen

Build a new Compose screen that follows the MVI/UDF pattern, supports **five locales** (not two), and is phone/one-hand-tuned (not tablet-tuned).

## ⚠️ MANDATORY PRE-READ

Before writing a single line, invoke the `driver-ui-standards` skill (or read driver CLAUDE.md's design-system section directly) and internalize:

- The **Three Hard Rules**: no `CircularProgressIndicator` mid-screen, no `tween()` for user motion, no screen ships without its four states (loading/empty/error/**offline**).
- **64dp minimum touch targets for trip actions** — this project's number, not POS's 56dp.
- **Five locales**, not two: ar, en, ur (RTL), bn, hi.
- Pre-acceptance order screens must never render customer name/phone/full address (backend contract enforces this structurally, but don't try to backfill it from a stale cache either).

A screen that "compiles and works" but skips this is **not done**.

```
ScreenComposable  ←collects←  StateFlow<UiState>  from  ViewModel
       ↓                                                    ↑
   onIntent(...)  ────────────────────────────────────────→ │
                              (single entry point)           │
```

- **One** `UiState` data class per screen.
- **One** sealed interface `Intent` enumerating user actions.
- **One** `ViewModel` exposing `state: StateFlow<UiState>` and `onIntent(intent: Intent)`.
- Screen is stateless — receives `state` + `onIntent`, easy to preview.

## Steps

### 1. Files to create

Inside `feature/<name>/src/main/java/app/qrmenu/driver/feature/<name>/`:

```
<Screen>State.kt        ← UiState + Intent
<Screen>ViewModel.kt    ← @HiltViewModel
<Screen>Screen.kt       ← Stateful + Stateless composables
```

### 2. State + Intent

```kotlin
data class TripOfferState(
    val isLoading: Boolean = false,
    val offer: DriverOrderOffered? = null,
    val secondsRemaining: Int = 45,
    val isOffline: Boolean = false,
    val error: String? = null,
)

sealed interface TripOfferIntent {
    data object Accept : TripOfferIntent
    data class Decline(val reason: String) : TripOfferIntent
    data object Ack : TripOfferIntent
}
```

### 3. ViewModel

```kotlin
@HiltViewModel
class TripOfferViewModel @Inject constructor(
    private val repo: OrderRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(TripOfferState())
    val state: StateFlow<TripOfferState> = _state.asStateFlow()

    init { onIntent(TripOfferIntent.Ack) }

    fun onIntent(intent: TripOfferIntent) {
        when (intent) {
            TripOfferIntent.Ack -> ack()
            TripOfferIntent.Accept -> accept()
            is TripOfferIntent.Decline -> decline(intent.reason)
        }
    }
    // …
}
```

### 4. Composables — stateful + stateless split, FIVE locale previews

```kotlin
@Composable
fun TripOfferScreen(viewModel: TripOfferViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    TripOfferScreenContent(state = state, onIntent = viewModel::onIntent)
}

@Composable
fun TripOfferScreenContent(state: TripOfferState, onIntent: (TripOfferIntent) -> Unit) {
    // pure UI here, no DI, no side effects beyond callbacks
}

@Preview(name = "AR", locale = "ar", showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "EN", locale = "en", showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "UR", locale = "ur", showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "BN", locale = "bn", showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "HI", locale = "hi", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun TripOfferPreview() = DriverTheme {
    TripOfferScreenContent(state = TripOfferState(offer = fakeOffer()), onIntent = {})
}
```

**Note the `widthDp`/`heightDp`:** a phone portrait frame (~390×844), not POS's `1024×600` tablet-landscape frame. Copying POS's preview dimensions here previews the wrong device shape.

### 5. Phone + RTL + locale rules

| Concern | Rule |
|---|---|
| Touch targets | **64.dp minimum for trip actions** (accept/decline/picked-up/delivered/navigate/call). Secondary/non-trip controls may use 48dp per Material minimum, but never a trip action. |
| Layout direction | `Modifier.padding(start=…, end=…)` — never `left/right`. RTL for ar/ur, LTR for en/bn/hi. |
| Strings | Every literal in `res/values/strings.xml` **and** `values-ar`, `values-ur`, `values-bn`, `values-hi`. Zero hardcoded Kotlin strings. |
| Numbers/money/phone | Centralized formatter forcing Latin digits (`-u-nu-latn` via `LocaleManager`) regardless of locale — never `String.format` ad-hoc. Wrap in a direction-isolating composable (`BidiText.ltr`-equivalent) so an RTL layout doesn't reverse a phone number or amount. |
| Full-screen offer | The trip-offer screen (screen #9 in CLAUDE.md's 18-screen map) is a special case: full-screen intent, continuous sound, vibration, visible countdown — build it like an alarm UI, not a normal screen. |
| Keep screen on | Already enabled globally where appropriate (trip screens); don't toggle per-screen unless intentional. |
| Haptics | `LocalHapticFeedback.current.performHapticFeedback(LongPress)` on the delivered-confirmation long-press (driver CLAUDE.md requires a long-press confirm for cash collection). |

### 6. Wire into navigation

In the feature's `*Navigation.kt`:

```kotlin
@Serializable data object TripOfferRoute

fun NavGraphBuilder.tripGraph() {
    composable<TripOfferRoute> { TripOfferScreen() }
}
```

Then call `tripGraph()` from `app/.../navigation/DriverNavHost.kt`, and **add the route to CLAUDE.md's 18-screen map (§🗺️ خريطة الشاشات) in the same change** — that table is the canonical screen inventory for this app, unlike POS which tracks routes in a separate §7.

### 7. Test

```kotlin
@Test fun `ack fires once on screen open`() = runTest {
    val vm = TripOfferViewModel(fakeRepo)
    vm.state.test {
        assertThat(awaitItem().isLoading).isTrue()
    }
}
```

### 8. Update CLAUDE.md (BLOCKING)

- §🗺️ خريطة الشاشات (18-screen map) — add or update the row for this screen.
- §🔤 مسرد المصطلحات — if this screen introduces a new user-facing term, add it in ar/en immediately and mark ur/bn/hi ⏳ (do not invent those three yourself — they need a native-speaker review, per the file's own warning).
- Session-end handoff section — same as every change here.

## Anti-patterns

- ❌ Reusing POS's 56dp touch target for a trip action — this app's floor is 64dp.
- ❌ Reusing POS's `1024×600` landscape preview dimensions — use a phone portrait frame.
- ❌ Only ar/en `@Preview`s — this app ships five locales.
- ❌ Rendering customer name/phone/address on a pre-acceptance screen.
- ❌ Reading DataStore/repos directly from Composables — go through the ViewModel.
- ❌ Hardcoded strings or ad-hoc number formatting.
- ❌ A trip-offer screen that isn't full-screen with sound + vibration + countdown.
- ❌ Skipping CLAUDE.md's screen-map update.
