package app.qrmenu.driver.alerts.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Every class in `:core:notifications` (OfferNotificationChannels,
 * FullScreenIntentEligibility, OfferAlarm, OfferNotifier,
 * NotificationHealthProvider) is constructor-injected and only asks for
 * `@ApplicationContext Context` — a binding Hilt already provides by
 * default. There is nothing left for this module to `@Provides`; it exists
 * so the module is discoverable as a Hilt entry point and so a future
 * binding (e.g. an interface for a fake in tests) has an obvious home.
 */
@Module
@InstallIn(SingletonComponent::class)
object NotificationsModule
