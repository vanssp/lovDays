package com.lovelydays.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        UserEntity::class,
        ProfileEntity::class,
        NotificationPrefEntity::class,
        CalendarLinkEntity::class,
        SyncedEventEntity::class,
        FiredNotificationEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class LovelyDatabase : RoomDatabase() {
    abstract fun users(): UserDao
    abstract fun profiles(): ProfileDao
    abstract fun notificationPrefs(): NotificationPrefDao
    abstract fun calendarLinks(): CalendarLinkDao
    abstract fun syncedEvents(): SyncedEventDao
    abstract fun firedNotifications(): FiredNotificationDao

    companion object {
        fun create(context: Context): LovelyDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                LovelyDatabase::class.java,
                "lovely-days.db",
            ).build()
    }
}
