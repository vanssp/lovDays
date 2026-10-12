package com.lovelydays.data.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.lovelydays.data.repo.StoredEvent
import com.lovelydays.data.repo.UserDataRepository
import com.lovelydays.domain.CycleCalculator
import com.lovelydays.domain.DateRange
import com.lovelydays.domain.Profile
import java.time.LocalDate
import java.time.ZoneOffset

data class DeviceCalendar(
    val id: Long,
    val displayName: String,
    val accountName: String,
    val accountType: String,
)

sealed interface SyncResult {
    data class Written(val count: Int) : SyncResult
    data class Removed(val count: Int) : SyncResult
    data class Failed(val message: String) : SyncResult
}

/**
 * Writes predicted events through the on-device calendar provider.
 * A Google account already on the phone shows up here; no cloud API key is used.
 * Our rows are tagged with a description marker, the app package, and a private extended property.
 */
class DeviceCalendarSync(
    private val context: Context,
    private val users: UserDataRepository,
) {
    fun hasPermissions(): Boolean {
        val read = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
        val write = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR)
        return read == PackageManager.PERMISSION_GRANTED && write == PackageManager.PERMISSION_GRANTED
    }

    fun listCalendars(): List<DeviceCalendar> {
        if (!hasPermissions()) return emptyList()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
        )
        val selection = "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL}>=?"
        val args = arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString())
        val found = mutableListOf<DeviceCalendar>()
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            selection,
            args,
            null,
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID)
            val nameIdx = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
            val accountIdx = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_NAME)
            val typeIdx = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_TYPE)
            while (cursor.moveToNext()) {
                found += DeviceCalendar(
                    id = cursor.getLong(idIdx),
                    displayName = cursor.getString(nameIdx) ?: "Календарь",
                    accountName = cursor.getString(accountIdx).orEmpty(),
                    accountType = cursor.getString(typeIdx).orEmpty(),
                )
            }
        }
        return found.sortedWith(
            compareByDescending<DeviceCalendar> { it.accountType == GOOGLE }
                .thenBy { it.displayName.lowercase() },
        )
    }

    suspend fun sync(userId: Long, calendar: DeviceCalendar): SyncResult {
        if (!hasPermissions()) return SyncResult.Failed(NO_PERMISSION)
        val profile = users.getProfile(userId) ?: return SyncResult.Failed(NO_PROFILE)
        return try {
            val removedIds = collectOurEventIds(userId)
            deleteEvents(removedIds)
            users.clearSyncedEvents(userId)
            val today = LocalDate.now()
            val drafts = drafts(profile, today)
            if (drafts.isEmpty()) {
                return SyncResult.Written(0)
            }
            val stored = mutableListOf<StoredEvent>()
            for (draft in drafts) {
                val eventId = insertEvent(calendar.id, draft) ?: continue
                stored += StoredEvent(eventId, calendar.id)
            }
            users.replaceSyncedEvents(userId, stored)
            if (stored.isEmpty()) SyncResult.Failed(FAILED) else SyncResult.Written(stored.size)
        } catch (_: SecurityException) {
            SyncResult.Failed(NO_PERMISSION)
        } catch (_: IllegalArgumentException) {
            SyncResult.Failed(NOT_FOUND)
        } catch (_: Exception) {
            SyncResult.Failed(FAILED)
        }
    }

    suspend fun remove(userId: Long): SyncResult {
        if (!hasPermissions()) return SyncResult.Failed(NO_PERMISSION)
        return try {
            val ids = collectOurEventIds(userId)
            val removed = deleteEvents(ids)
            users.clearSyncedEvents(userId)
            SyncResult.Removed(removed)
        } catch (_: SecurityException) {
            SyncResult.Failed(NO_PERMISSION)
        } catch (_: Exception) {
            SyncResult.Failed(FAILED)
        }
    }

    private suspend fun collectOurEventIds(userId: Long): Set<Long> {
        val ids = linkedSetOf<Long>()
        users.syncedEvents(userId).forEach { ids += it.eventId }
        ids += findByMarker()
        ids += findByExtendedProperty()
        return ids
    }

    private fun findByMarker(): List<Long> {
        val ids = mutableListOf<Long>()
        val selection = "(${CalendarContract.Events.TITLE} LIKE ? OR ${CalendarContract.Events.TITLE} LIKE ?) AND (${CalendarContract.Events.DESCRIPTION} LIKE ? OR ${CalendarContract.Events.DESCRIPTION} LIKE ?)"
        val args = arrayOf("$TITLE_PREFIX%", "$LEGACY_TITLE_PREFIX%", "%$MARKER%", "%$LEGACY_MARKER%")
        context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            arrayOf(CalendarContract.Events._ID),
            selection,
            args,
            null,
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(CalendarContract.Events._ID)
            while (cursor.moveToNext()) ids += cursor.getLong(idIdx)
        }
        return ids
    }

    private fun findByExtendedProperty(): List<Long> {
        val ids = mutableListOf<Long>()
        try {
            context.contentResolver.query(
                CalendarContract.ExtendedProperties.CONTENT_URI,
                arrayOf(CalendarContract.ExtendedProperties.EVENT_ID),
                "${CalendarContract.ExtendedProperties.NAME}=?",
                arrayOf(MARKER_NAME),
                null,
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(CalendarContract.ExtendedProperties.EVENT_ID)
                while (cursor.moveToNext()) ids += cursor.getLong(idIdx)
            }
        } catch (_: Exception) {
            // Some devices only expose extended properties to a sync adapter. The description marker still works.
        }
        return ids
    }

    private fun deleteEvents(ids: Set<Long>): Int {
        var removed = 0
        for (id in ids) {
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)
            val rows = try {
                context.contentResolver.delete(uri, null, null)
            } catch (_: Exception) {
                0
            }
            if (rows > 0) removed++
        }
        return removed
    }

    private fun insertEvent(calendarId: Long, draft: Draft): Long? {
        val start = draft.start
        val endExclusive = draft.endInclusive.plusDays(1)
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, draft.title)
            put(CalendarContract.Events.DESCRIPTION, draft.description)
            put(CalendarContract.Events.DTSTART, utcMillis(start))
            put(CalendarContract.Events.DTEND, utcMillis(endExclusive))
            put(CalendarContract.Events.ALL_DAY, 1)
            put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
            put(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_FREE)
            put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CONFIRMED)
            put(CalendarContract.Events.HAS_ALARM, 0)
            put(CalendarContract.Events.CUSTOM_APP_PACKAGE, context.packageName)
            put(CalendarContract.Events.CUSTOM_APP_URI, "lovelydays://event/${draft.tag}")
        }
        val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values) ?: return null
        val eventId = uri.lastPathSegment?.toLongOrNull() ?: return null
        try {
            val extra = ContentValues().apply {
                put(CalendarContract.ExtendedProperties.EVENT_ID, eventId)
                put(CalendarContract.ExtendedProperties.NAME, MARKER_NAME)
                put(CalendarContract.ExtendedProperties.VALUE, draft.tag)
            }
            context.contentResolver.insert(CalendarContract.ExtendedProperties.CONTENT_URI, extra)
        } catch (_: Exception) {
            // The description marker and title prefix are enough to delete the row later.
        }
        return eventId
    }

    private fun drafts(profile: Profile, today: LocalDate): List<Draft> {
        val resolved = CycleCalculator.resolve(profile, today)
        val cycles = CycleCalculator.project(resolved.params, today.plusDays(200))
        val relevant = cycles.filter { cycle ->
            val next = cycle.period.start.plusDays(resolved.params.cycleLength.toLong())
            today.isBefore(next)
        }.take(4)
        val out = mutableListOf<Draft>()
        for (cycle in relevant) {
            addWindow(out, today, "period-${cycle.index}", "$TITLE_PREFIX менструация", periodText(), cycle.period)
            addDay(out, today, "ovulation-${cycle.index}", "$TITLE_PREFIX овуляция", ovulationText(), cycle.ovulation)
            addWindow(out, today, "mood-${cycle.index}", "$TITLE_PREFIX настроение", moodText(), cycle.pmsMood)
            addWindow(
                out,
                today,
                "mood-mid-${cycle.index}",
                "$TITLE_PREFIX настроение в середине цикла",
                midText(),
                cycle.midCycleMood,
            )
            addWindow(out, today, "skin-${cycle.index}", "$TITLE_PREFIX кожа", skinText(), cycle.skin)
        }
        return out
    }

    private fun addWindow(
        out: MutableList<Draft>,
        today: LocalDate,
        tag: String,
        title: String,
        description: String,
        range: DateRange,
    ) {
        if (range.endInclusive.isBefore(today)) return
        val start = if (range.start.isBefore(today)) today else range.start
        out += Draft(tag, title, description, start, range.endInclusive)
    }

    private fun addDay(
        out: MutableList<Draft>,
        today: LocalDate,
        tag: String,
        title: String,
        description: String,
        day: LocalDate,
    ) {
        if (day.isBefore(today)) return
        out += Draft(tag, title, description, day, day)
    }

    private fun periodText() = describe("Ориентировочное окно менструации.")
    private fun ovulationText() = describe("Ориентировочный день овуляции. Не контрацепция.")
    private fun moodText() = describe("Окно, когда настроение может меняться перед менструацией.")
    private fun midText() = describe("Короткая заметка о настроении около овуляции.")
    private fun skinText() = describe("Дни, когда чуть выше шанс высыпаний: перед менструацией и в её начале.")

    private fun describe(what: String): String =
        "$what\n\nОценка приложения «Эти дни». Не медицинский прогноз и не контрацепция.\n$MARKER"

    private fun utcMillis(date: LocalDate): Long =
        date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    private data class Draft(
        val tag: String,
        val title: String,
        val description: String,
        val start: LocalDate,
        val endInclusive: LocalDate,
    )

    companion object {
        const val TITLE_PREFIX = "Эти дни:"
        const val LEGACY_TITLE_PREFIX = "lovelyDays:"
        const val MARKER = "[Эти дни]"
        const val LEGACY_MARKER = "[lovelyDays]"
        const val MARKER_NAME = "com.lovelydays.marker"
        private const val GOOGLE = "com.google"
        const val NO_PERMISSION = "Нет доступа к календарю. Разрешите чтение и запись, чтобы записать события."
        const val NO_PROFILE = "Сначала заполните профиль — без него нечего записывать."
        const val NOT_FOUND = "Календарь не найден или его нельзя изменить."
        const val FAILED = "Не получилось записать события. Попробуйте ещё раз."
    }
}
