package com.lovelydays.data.repo

import com.lovelydays.data.db.CalendarLinkEntity
import com.lovelydays.data.db.FiredNotificationEntity
import com.lovelydays.data.db.LovelyDatabase
import com.lovelydays.data.db.NotificationPrefEntity
import com.lovelydays.data.db.ProfileEntity
import com.lovelydays.data.db.SyncedEventEntity
import com.lovelydays.domain.NotificationPrefs
import com.lovelydays.domain.Profile
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class CalendarLink(
    val userId: Long,
    val calendarId: Long,
    val displayName: String,
    val accountName: String,
)

data class StoredEvent(
    val eventId: Long,
    val calendarId: Long,
)

class UserDataRepository(private val db: LovelyDatabase) {
    fun observeProfile(userId: Long): Flow<Profile?> =
        db.profiles().observe(userId).map { it?.toModel() }

    suspend fun getProfile(userId: Long): Profile? = db.profiles().get(userId)?.toModel()

    suspend fun saveProfile(profile: Profile) {
        db.profiles().upsert(profile.toEntity())
        if (db.notificationPrefs().get(profile.userId) == null) {
            db.notificationPrefs().upsert(NotificationPrefs.allOn(profile.userId).toEntity())
        }
    }

    fun observePrefs(userId: Long): Flow<NotificationPrefs> =
        db.notificationPrefs().observe(userId).map { it?.toModel() ?: NotificationPrefs.allOn(userId) }

    suspend fun getPrefs(userId: Long): NotificationPrefs =
        db.notificationPrefs().get(userId)?.toModel() ?: NotificationPrefs.allOn(userId)

    suspend fun savePrefs(prefs: NotificationPrefs) {
        db.notificationPrefs().upsert(prefs.toEntity())
    }

    fun observeLink(userId: Long): Flow<CalendarLink?> =
        db.calendarLinks().observe(userId).map { it?.toModel() }

    suspend fun getLink(userId: Long): CalendarLink? = db.calendarLinks().get(userId)?.toModel()

    suspend fun saveLink(link: CalendarLink) {
        db.calendarLinks().upsert(link.toEntity())
    }

    suspend fun syncedEvents(userId: Long): List<StoredEvent> =
        db.syncedEvents().forUser(userId).map { StoredEvent(it.eventId, it.calendarId) }

    suspend fun replaceSyncedEvents(userId: Long, events: List<StoredEvent>) {
        db.syncedEvents().deleteForUser(userId)
        events.forEach { event ->
            db.syncedEvents().insert(
                SyncedEventEntity(
                    eventId = event.eventId,
                    userId = userId,
                    calendarId = event.calendarId,
                ),
            )
        }
    }

    suspend fun clearSyncedEvents(userId: Long) {
        db.syncedEvents().deleteForUser(userId)
    }

    suspend fun firedKeys(userId: Long): Set<String> = db.firedNotifications().keys(userId).toSet()

    suspend fun isFired(userId: Long, key: String): Boolean =
        db.firedNotifications().count(userId, key) > 0

    suspend fun markFired(userId: Long, key: String) {
        db.firedNotifications().insert(
            FiredNotificationEntity(
                userId = userId,
                dedupeKey = key,
                firedAtEpochMs = System.currentTimeMillis(),
            ),
        )
        val horizon = System.currentTimeMillis() - 120L * 24 * 60 * 60 * 1000
        db.firedNotifications().deleteOlderThan(horizon)
    }
}

private fun ProfileEntity.toModel() = Profile(
    userId = userId,
    birth = LocalDate.ofEpochDay(birthEpochDay),
    weightKg = weightKg,
    heightCm = heightCm,
    lastPeriod = LocalDate.ofEpochDay(lastPeriodEpochDay),
    cycleLength = cycleLength,
    periodLength = periodLength,
)

private fun Profile.toEntity() = ProfileEntity(
    userId = userId,
    birthEpochDay = birth.toEpochDay(),
    weightKg = weightKg,
    heightCm = heightCm,
    lastPeriodEpochDay = lastPeriod.toEpochDay(),
    cycleLength = cycleLength,
    periodLength = periodLength,
)

private fun NotificationPrefEntity.toModel() = NotificationPrefs(userId, period, ovulation, mood, skin)

private fun NotificationPrefs.toEntity() = NotificationPrefEntity(userId, period, ovulation, mood, skin)

private fun CalendarLinkEntity.toModel() = CalendarLink(userId, calendarId, displayName, accountName)

private fun CalendarLink.toEntity() = CalendarLinkEntity(userId, calendarId, displayName, accountName)
