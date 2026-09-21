package app.qrmenu.driver.auth.di

import android.content.Context
import app.qrmenu.driver.auth.phone.DriverPhoneVerifier
import app.qrmenu.driver.auth.phone.FirebaseDriverPhoneVerifier
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.michaelrocks.libphonenumber.android.PhoneNumberUtil
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PhoneModule {

    /**
     * Singleton because [PhoneNumberUtil.createInstance] loads its region
     * metadata from assets — a few hundred kilobytes parsed on first use. Built
     * once per process, never per screen.
     */
    @Provides
    @Singleton
    fun providePhoneNumberUtil(@ApplicationContext context: Context): PhoneNumberUtil =
        PhoneNumberUtil.createInstance(context)
}

/**
 * A separate `abstract class` module: Hilt's `@Binds` needs an abstract
 * function, which an `object` (used above for the `@Provides` factory) cannot
 * declare.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PhoneVerifierModule {

    @Binds
    @Singleton
    abstract fun bindDriverPhoneVerifier(impl: FirebaseDriverPhoneVerifier): DriverPhoneVerifier
}
