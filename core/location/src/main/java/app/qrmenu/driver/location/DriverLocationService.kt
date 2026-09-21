package app.qrmenu.driver.location

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.util.Log
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.getSystemService
import app.qrmenu.driver.location.upload.LocationPointBatcher
import app.qrmenu.driver.location.upload.LocationUploader
import app.qrmenu.driver.network.dto.LocationPointDto
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The one place location is actually sampled, and the one process boundary
 * that makes CLAUDE.md's promise real: this service only EXISTS while the
 * driver is available — start it on going online, stop it on going offline —
 * so "غير متاح = لا تتبّع إطلاقاً" is not a filter applied to a running
 * tracker, it is the tracker simply not running.
 *
 * `:feature:availability` (not built yet) owns starting/stopping this via
 * [start]/[stop] — this class does not decide availability itself.
 *
 * Cadence is re-evaluated on every fix using [LocationCadencePolicy], fed by
 * [DriverTripActivityState] (external, see its doc) and the fused speed
 * reading of the LAST fix (see [isMoving]) — so a driver who stops moving
 * mid-trip drifts from the 7s tier to the 30s tier within one sample, not on a
 * fixed timer that could lag behind reality by up to a full cycle.
 */
@AndroidEntryPoint
class DriverLocationService : Service() {

    @Inject lateinit var batcher: LocationPointBatcher
    @Inject lateinit var uploader: LocationUploader
    @Inject lateinit var tripActivity: DriverTripActivityState
    @Inject lateinit var fusedClient: FusedLocationProviderClient

    private val serviceScope = CoroutineScope(SupervisorJob())
    private var uploadLoopJob: Job? = null
    private var currentIntervalMillis: Long = LocationConstants.MOVING_INTERVAL_MS
    private var isMoving = false

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            isMoving = LocationCadencePolicy.isMoving(location.speed)

            batcher.enqueue(
                LocationPointDto(
                    lat = location.latitude,
                    lng = location.longitude,
                    accuracy = location.accuracy.toDouble(),
                    speed = location.speed.toDouble(),
                    heading = if (location.hasBearing()) location.bearing.toDouble() else null,
                    recordedAt = deviceTimeIso8601WithOffset(),
                ),
            )

            reconcileRequestInterval()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Type "location" per CLAUDE.md. Android 14+ REFUSES to start it — with
        // a SecurityException that takes the whole app down — unless location is
        // granted at this instant. The caller gates on that, but the grant can
        // also be withdrawn from the notification shade while this is running, or
        // withdrawn between START_STICKY restarting us and the system delivering
        // the intent. A driver must never lose the app to that: refuse to run,
        // quietly, and let the availability screen's standing warning explain.
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        } catch (error: Exception) {
            Log.w(TAG, "Refused to start location tracking; permission is not granted right now.", error)
            stopSelf()
            return START_NOT_STICKY
        }
        beginSampling()
        beginUploadLoop()
        return START_STICKY
    }

    override fun onDestroy() {
        fusedClient.removeLocationUpdates(locationCallback)
        uploadLoopJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun beginSampling() {
        requestLocationUpdates(currentIntervalMillis)
    }

    /**
     * Called after every fix. Availability is implicit — this service is only
     * running while available — so only [tripActivity] and [isMoving] feed the
     * policy. When the resolved interval differs from what the last
     * `requestLocationUpdates` call used, the request is reissued at the new
     * interval; FusedLocationProviderClient has no "change interval in place"
     * API, so this restarts the request rather than tearing down the service.
     */
    private fun reconcileRequestInterval() {
        val resolved = LocationCadencePolicy.resolveIntervalMillis(
            isAvailable = true,
            hasActiveTrip = tripActivity.hasActiveTrip.value,
            isMoving = isMoving,
        ) ?: return // unreachable while this service is alive, but never crash on it.

        if (resolved != currentIntervalMillis) {
            currentIntervalMillis = resolved
            requestLocationUpdates(resolved)
        }
    }

    private fun requestLocationUpdates(intervalMillis: Long) {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMillis)
            .setMinUpdateIntervalMillis(intervalMillis / 2)
            .build()
        fusedClient.removeLocationUpdates(locationCallback)
        runCatching {
            fusedClient.requestLocationUpdates(request, locationCallback, mainLooper)
        }
        // A SecurityException here means the ladder in :feature:availability let
        // this service start without ACCESS_FINE_LOCATION — a contract break in
        // that module, not something recoverable here. Swallowed rather than
        // crashing the foreground service, since a crash would drop the
        // permanent notification the driver is relying on to know sharing is on.
    }

    private fun beginUploadLoop() {
        uploadLoopJob = serviceScope.launch {
            while (isActive) {
                uploader.uploadPendingBatch()
                delay(LocationConstants.UPLOAD_INTERVAL_MS)
            }
        }
    }

    private fun buildNotification(): Notification {
        ensureChannel()
        val openAppIntent = packageManager.getLaunchIntentForPackage(packageName)
        val contentIntent = openAppIntent?.let {
            PendingIntent.getActivity(
                this,
                0,
                it,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(getString(R.string.location_notification_title))
            .setContentText(getString(R.string.location_notification_body))
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService<NotificationManager>() ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.location_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    companion object {
        private const val TAG = "DriverLocationService"
        private const val CHANNEL_ID = "driver_location_tracking"
        private const val NOTIFICATION_ID = 4711

        /** Starts the permanent, honest "sharing your location" notification and sampling. */
        fun start(context: Context) {
            context.startForegroundService(Intent(context, DriverLocationService::class.java))
        }

        /** Stops sampling immediately — CLAUDE.md: going unavailable means no tracking at all. */
        fun stop(context: Context) {
            context.stopService(Intent(context, DriverLocationService::class.java))
        }
    }
}
