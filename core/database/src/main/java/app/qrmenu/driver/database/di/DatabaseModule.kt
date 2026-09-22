package app.qrmenu.driver.database.di

import android.content.Context
import androidx.room.Room
import app.qrmenu.driver.database.DriverDatabase
import app.qrmenu.driver.database.dao.DriverActionOutboxDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDriverDatabase(@ApplicationContext context: Context): DriverDatabase =
        Room.databaseBuilder(context, DriverDatabase::class.java, "driver.db")
            // Deliberately no fallbackToDestructiveMigration() — see
            // DriverActionOutboxEntity's class doc: a row here can be unsent,
            // money-affecting driver state. Wiping it on a schema mismatch is
            // worse than crashing and forcing a real migration.
            .build()

    @Provides
    @Singleton
    fun provideDriverActionOutboxDao(database: DriverDatabase): DriverActionOutboxDao =
        database.driverActionOutboxDao()
}
