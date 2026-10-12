package com.lovelydays.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "users",
    indices = [Index(value = ["login"], unique = true)],
)
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val login: String,
    val passwordHash: String,
    val salt: String,
    val createdAt: Long,
)

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey val userId: Long,
    val birthEpochDay: Long,
    val weightKg: Double,
    val heightCm: Double,
    val lastPeriodEpochDay: Long,
    val cycleLength: Int?,
    val periodLength: Int?,
)

@Entity(tableName = "notification_prefs")
data class NotificationPrefEntity(
    @PrimaryKey val userId: Long,
    val period: Boolean,
    val ovulation: Boolean,
    val mood: Boolean,
    val skin: Boolean,
)

@Entity(tableName = "calendar_links")
data class CalendarLinkEntity(
    @PrimaryKey val userId: Long,
    val calendarId: Long,
    val displayName: String,
    val accountName: String,
)

@Entity(tableName = "synced_events")
data class SyncedEventEntity(
    @PrimaryKey val eventId: Long,
    val userId: Long,
    val calendarId: Long,
)

@Entity(
    tableName = "fired_notifications",
    primaryKeys = ["userId", "dedupeKey"],
)
data class FiredNotificationEntity(
    val userId: Long,
    val dedupeKey: String,
    val firedAtEpochMs: Long,
)
