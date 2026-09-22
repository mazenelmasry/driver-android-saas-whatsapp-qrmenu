package app.qrmenu.driver.database

import androidx.room.TypeConverter
import app.qrmenu.driver.database.entity.NotificationHistoryType

class NotificationHistoryTypeConverter {
    @TypeConverter
    fun fromNotificationHistoryType(value: NotificationHistoryType): String = value.name

    @TypeConverter
    fun toNotificationHistoryType(value: String): NotificationHistoryType =
        NotificationHistoryType.valueOf(value)
}
