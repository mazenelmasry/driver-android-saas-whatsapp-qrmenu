package app.qrmenu.driver

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
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
import app.qrmenu.driver.designsystem.theme.DriverTheme
import app.qrmenu.driver.home.HomeRoute
import app.qrmenu.driver.onboarding.language.LanguageRoute
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var tokenStore: TokenStore

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
            DriverTheme {
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

    when {
        !signedIn -> AuthFlow(onSignedIn = { signedIn = true })

        redeemingInvite -> RedeemInviteFlow(onRedeemed = { redeemingInvite = false })

        else -> HomeRoute(
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
