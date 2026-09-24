package app.qrmenu.driver.location.permission

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle

/**
 * Whether the device's location toggle (GPS/network positioning) is on at
 * all — distinct from, and never checked by, [LocationPermissions.readState]
 * above. A driver can hold every permission this app asks for and still have
 * the SYSTEM toggle off (airplane mode remnants, saving battery, an
 * accidental swipe in quick settings): the app then shows "متاح ✓" for a
 * whole hour with zero warning while the server sees no live fix, because
 * nothing here was ever checking for it.
 *
 * `LocationManagerCompat.isLocationEnabled` is the toggle itself (works down
 * to API 21, unlike `LocationManager.isLocationEnabled` added in API 28).
 */
fun isDeviceLocationEnabled(context: Context): Boolean {
    val locationManager = ContextCompat.getSystemService(context, LocationManager::class.java)
        ?: return false
    return LocationManagerCompat.isLocationEnabled(locationManager)
}

/**
 * Tracks [isDeviceLocationEnabled], kept fresh two ways:
 *
 *  - **Live**: a [LocationManager.PROVIDERS_CHANGED_ACTION] broadcast fires
 *    the instant the driver flips the toggle from quick settings or system
 *    Settings, so the banner this backs can disappear without the driver
 *    having to background/foreground the app.
 *  - **On resume**: mirrors `NotificationHealthBanner`'s own reasoning for
 *    doing the same — a driver who fixes this in system Settings and comes
 *    straight back must see the banner gone immediately, not on the next
 *    broadcast (there may not be one, if the toggle was already correct by
 *    the time this screen re-registers its receiver).
 */
@Composable
fun rememberLocationServicesEnabled(): State<Boolean> {
    val context = LocalContext.current
    val enabled = remember { mutableStateOf(isDeviceLocationEnabled(context)) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receivedContext: Context, intent: Intent) {
                enabled.value = isDeviceLocationEnabled(receivedContext)
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { context.unregisterReceiver(receiver) }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            enabled.value = isDeviceLocationEnabled(context)
        }
    }

    return enabled
}
