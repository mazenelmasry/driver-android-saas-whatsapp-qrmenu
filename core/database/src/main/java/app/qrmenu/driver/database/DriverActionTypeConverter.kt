package app.qrmenu.driver.database

import androidx.room.TypeConverter
import app.qrmenu.driver.database.entity.DriverActionType

class DriverActionTypeConverter {
    @TypeConverter
    fun fromDriverActionType(value: DriverActionType): String = value.name

    @TypeConverter
    fun toDriverActionType(value: String): DriverActionType = DriverActionType.valueOf(value)
}
