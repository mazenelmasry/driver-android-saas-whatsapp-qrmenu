package app.qrmenu.driver

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.qrmenu.driver.common.locale.SupportedLocales
import app.qrmenu.driver.datastore.AppLocaleStore
import app.qrmenu.driver.designsystem.theme.DriverTheme
import app.qrmenu.driver.designsystem.theme.Spacing
import app.qrmenu.driver.onboarding.language.LanguageRoute
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

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
                DriverApp()
            }
        }
    }
}

/**
 * Phase-0 entry point.
 *
 * There is deliberately no `NavHost` yet: a navigation graph with one real
 * destination is scaffolding that has to be rewritten the moment login lands,
 * and it would hide which screen actually decides what comes first. That
 * decision is here and is explicit — the language picker runs until a choice
 * exists (decision 9), and nothing else can run before it.
 */
@Composable
private fun DriverApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var languageChosen by remember {
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
    } else {
        ScaffoldPlaceholder()
    }
}

/**
 * Phase-0 placeholder for everything after the language picker. Replaced by the
 * login screen next.
 */
@Composable
private fun ScaffoldPlaceholder() {
    Scaffold { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .padding(Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = BuildConfig.API_BASE_URL,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private fun Context.withLanguage(language: String): Context {
    val configuration = android.content.res.Configuration(resources.configuration)
    configuration.setLocale(SupportedLocales.buildLatinLocale(language))
    return createConfigurationContext(configuration)
}
