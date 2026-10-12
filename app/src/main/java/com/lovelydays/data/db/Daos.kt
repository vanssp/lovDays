package com.lovelydays.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE login = :login LIMIT 1")
    suspend fun findByLogin(login: String): UserEntity?

    @Insert
    suspend fun insert(user: UserEntity): Long
}

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles WHERE userId = :userId")
    fun observe(userId: Long): Flow<ProfileEntity?>

    @Query("SELECT * FROM profiles WHERE userId = :userId")
    suspend fun get(userId: Long): ProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: ProfileEntity)
}

@Dao
interface NotificationPrefDao {
    @Query("SELECT * FROM notification_prefs WHERE userId = :userId")
    fun observe(userId: Long): Flow<NotificationPrefEntity?>

    @Query("SELECT * FROM notification_prefs WHERE userId = :userId")
    suspend fun get(userId: Long): NotificationPrefEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(prefs: NotificationPrefEntity)
}

@Dao
interface CalendarLinkDao {
    @Query("SELECT * FROM calendar_links WHERE userId = :userId")
    fun observe(userId: Long): Flow<CalendarLinkEntity?>

    @Query("SELECT * FROM calendar_links WHERE userId = :userId")
    suspend fun get(userId: Long): CalendarLinkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(link: CalendarLinkEntity)
}

@Dao
interface SyncedEventDao {
    @Query("SELECT * FROM synced_events WHERE userId = :userId")
    suspend fun forUser(userId: Long): List<SyncedEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: SyncedEventEntity)

    @Query("DELETE FROM synced_events WHERE userId = :userId")
    suspend fun deleteForUser(userId: Long)
}

@Dao
interface FiredNotificationDao {
    @Query("SELECT dedupeKey FROM fired_notifications WHERE userId = :userId")
    suspend fun keys(userId: Long): List<String>

    @Query("SELECT COUNT(*) FROM fired_notifications WHERE userId = :userId AND dedupeKey = :key")
    suspend fun count(userId: Long, key: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: FiredNotificationEntity)

    @Query("DELETE FROM fired_notifications WHERE firedAtEpochMs < :before")
    suspend fun deleteOlderThan(before: Long)
}
