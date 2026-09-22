package app.qrmenu.driver.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import app.qrmenu.driver.database.dao.DriverActionOutboxDao
import app.qrmenu.driver.database.entity.DriverActionOutboxEntity

// See CLAUDE.md's "سجلّ مخطط Room" — keep that table in sync with every
// version bump made here.
@Database(
    version = 1,
    exportSchema = true,
    entities = [DriverActionOutboxEntity::class],
)
@TypeConverters(DriverActionTypeConverter::class)
abstract class DriverDatabase : RoomDatabase() {
    abstract fun driverActionOutboxDao(): DriverActionOutboxDao
}
