package app.qrmenu.driver.push.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Every class in `:core:push` (PushTokenProvider) is constructor-injected
 * and only asks for `@ApplicationContext Context` — a binding Hilt already
 * provides by default. `PushHandler` is deliberately NOT bound here (see
 * its own doc) — `:app` owns that binding. This module exists so `:core:push`
 * is discoverable as a Hilt entry point, matching `:core:notifications`'
 * `NotificationsModule`.
 */
@Module
@InstallIn(SingletonComponent::class)
object PushModule
