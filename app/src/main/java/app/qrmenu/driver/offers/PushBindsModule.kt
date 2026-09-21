package app.qrmenu.driver.offers

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import app.qrmenu.driver.push.PushHandler

/**
 * `:app`'s half of the `:core:push` seam — binds [PushHandler] to
 * [DriverPushHandler] so `:core:push`'s `DriverMessagingService` can be
 * `@Inject lateinit var pushHandler: PushHandler` without that module ever
 * depending on `:app`. See [PushHandler]'s own doc for why the default is
 * deliberately NOT provided in `:core:push` itself.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PushBindsModule {

    @Binds
    abstract fun bindPushHandler(impl: DriverPushHandler): PushHandler
}
