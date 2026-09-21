package app.qrmenu.driver.location.di

import android.content.Context
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LocationModule {

    @Provides @Singleton
    fun provideFusedLocationProviderClient(@ApplicationContext context: Context): FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    @Provides @Singleton
    fun provideClock(): Clock = Clock.systemUTC()

    // LocationPointBatcher, LocationConnectivityState, LocationUploader and
    // DriverTripActivityState are constructor-injected @Singleton classes —
    // Hilt needs no @Provides for them, only this module's two leaf
    // dependencies above.
}
