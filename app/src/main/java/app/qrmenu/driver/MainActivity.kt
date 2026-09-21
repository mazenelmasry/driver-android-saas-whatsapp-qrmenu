package app.qrmenu.driver

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import app.qrmenu.driver.auth.AuthFlow
import app.qrmenu.driver.auth.RedeemInviteFlow
import app.qrmenu.driver.common.locale.SupportedLocales
import app.qrmenu.driver.datastore.AppLocaleStore
import app.qrmenu.driver.datastore.TokenStore
import app.qrmenu.driver.datastore.UiScaleStore
import app.qrmenu.driver.designsystem.theme.DriverTheme
import app.qrmenu.driver.onboarding.language.LanguageRoute
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var tokenStore: TokenStore

    /**
     * The driver's own size choice. Held here rather than inside a screen
     * because it has to wrap [DriverTheme] — a size setting that only takes
     * effect on the screen that sets it is not a size setting.
     */
    @Inject
    lateinit var uiScaleStore: UiScaleStore

    /**
     * Applies the stored language before any view is inflated.
     *
     * It reads [AppLocaleStore] STATICALLY rather than through Hilt: this runs
     * before the activity's component exists, and on Android 12 and below
     * AppCompat's override cannot reach a plain ComponentActivity at all. The
     * store is plain SharedPreferences precisely so it can answer here.
     */
    override fun attachBaseContext(newBase: Context) {
        val language = AppLocaleStore.readLanguage(newBase) ?: SupportedLocales.default
        super.attachBaseContext(newBase.withLanguage(language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            // `collectAsState` with the store's own current value as the seed:
            // the first frame must already be drawn at the chosen size, or the
            // app visibly resizes itself on every launch.
            val uiScale by uiScaleStore.scale.collectAsState(initial = uiScaleStore.current())

            DriverTheme(uiScaleFactor = uiScale.factor) {
                DriverApp(tokenStore)
            }
        }
    }
}

/**
 * What the app shows, and in what order it decides.
 *
 * There is deliberately still no `NavHost`: three top-level states with one
 * decision each is a `when`, and a navigation graph would spread that decision
 * across four files without adding a destination anyone can reach.
 *
 * 1. The language picker runs until a choice exists (decision 9). Nothing can
 *    run before it — a driver who cannot read the screen cannot sign in on it.
 * 2. Then sign-in, unless a session was already stored.
 * 3. Then home.
 */
@Composable
private fun DriverApp(tokenStore: TokenStore) {
    val context = LocalContext.current
    var languageChosen by rememberSaveable {
        mutableStateOf(AppLocaleStore.readLanguage(context) != null)
    }

    if (!languageChosen) {
        LanguageRoute(
            onContinue = {
                languageChosen = true
                // The picker already wrote and applied the language; recreating
                // re-runs attachBaseContext so every string below is resolved in
                // it, rather than leaving the previous locale's strings on
                // screen until the next process start.
                (context as? ComponentActivity)?.recreate()
            },
        )
        return
    }

    // 🔴 Read ONCE, at launch — not as a live flow off the token.
    //
    // `verify-otp` signs the driver in so that choosing a password is an
    // authenticated call; a token therefore exists in the MIDDLE of the sign-in
    // flow, before that flow has finished. Driving this screen off the token
    // would throw the driver onto home half-way through, with no password set.
    // What "skip sign-in" actually means is "a session survived from a previous
    // run", and that is what this reads.
    var signedIn by rememberSaveable { mutableStateOf(tokenStore.hasValidSession()) }
    var redeemingInvite by rememberSaveable { mutableStateOf(false) }

    // The inverse direction IS safe to drive off the token, and is the one
    // that matters: `SessionExpiryInterceptor` clears the store the moment the
    // server rejects the session (expired, suspended, signed in on another
    // phone). Without this the driver would sit on a screen of "something went
    // wrong" tapping a Try again that can never succeed.
    val token by tokenStore.token.collectAsState()
    LaunchedEffect(token, signedIn) {
        if (signedIn && token.isNullOrBlank()) {
            signedIn = false
            redeemingInvite = false
        }
    }

    when {
        !signedIn -> AuthFlow(onSignedIn = { signedIn = true })

        redeemingInvite -> RedeemInviteFlow(onRedeemed = { redeemingInvite = false })

        else -> SignedInScreen(
            onSignedOut = { signedIn = false },
            onEnterInviteCode = { redeemingInvite = true },
        )
    }
}

private fun Context.withLanguage(language: String): Context {
    val configuration = android.content.res.Configuration(resources.configuration)
    configuration.setLocale(SupportedLocales.buildLatinLocale(language))
    return createConfigurationContext(configuration)
}
