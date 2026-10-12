package com.lovelydays.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.lovelydays.LovelyApplication
import com.lovelydays.MainActivity
import com.lovelydays.R
import com.lovelydays.data.repo.UserDataRepository
import com.lovelydays.data.session.ScheduleStore
import com.lovelydays.data.session.SessionStore
import com.lovelydays.domain.ReminderCopy
import com.lovelydays.domain.ReminderPlanner
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

object Channels {
    const val PERIOD = "lovely_period"
    const val OVULATION = "lovely_ovulation"
    const val MOOD = "lovely_mood"
    const val SKIN = "lovely_skin"

    fun idFor(type: String): String = when (type) {
        "ovulation" -> OVULATION
        "mood" -> MOOD
        "skin" -> SKIN
        else -> PERIOD
    }

    fun ensure(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        fun channel(id: String, name: String, description: String) =
            NotificationChannel(id, name, NotificationManager.IMPORTANCE_DEFAULT).apply {
                this.description = description
            }
        manager.createNotificationChannel(
            channel(PERIOD, "Менструация", "Напоминание о возможном начале менструации"),
        )
        manager.createNotificationChannel(
            channel(OVULATION, "Овуляция", "Напоминание об ориентировочной овуляции"),
        )
        manager.createNotificationChannel(
            channel(MOOD, "Настроение", "Напоминание об окне, когда настроение может меняться"),
        )
        manager.createNotificationChannel(
            channel(SKIN, "Кожа", "Напоминание о днях, когда выше шанс высыпаний"),
        )
    }
}

object Notifier {
    fun canPost(context: Context): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun show(context: Context, type: String, phase: String, eventDay: LocalDate) {
        Channels.ensure(context)
        val title = ReminderCopy.title(type, phase)
        val text = ReminderCopy.body(type, phase, eventDay)
        val notificationId = notificationId(type, phase, eventDay)
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            notificationId,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, Channels.idFor(type))
            .setSmallIcon(R.drawable.ic_stat_moon)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    private fun notificationId(type: String, phase: String, eventDay: LocalDate): Int {
        val raw = "$type|$phase|${eventDay.toEpochDay()}".hashCode() and 0x7fffffff
        return if (raw == 0) 1 else raw
    }
}

class NotificationScheduler(
    private val context: Context,
    private val session: SessionStore,
    private val users: UserDataRepository,
    private val scheduleStore: ScheduleStore,
) {
    private val work = WorkManager.getInstance(context)

    fun ensurePeriodic() {
        val request = PeriodicWorkRequestBuilder<RescheduleWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(12, TimeUnit.HOURS)
            .build()
        work.enqueueUniquePeriodicWork(
            PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    suspend fun cancelAll() {
        scheduleStore.names().forEach { name -> work.cancelUniqueWork(name) }
        scheduleStore.save(emptySet())
    }

    suspend fun reschedule(userId: Long) {
        val profile = users.getProfile(userId)
        if (profile == null) {
            cancelAll()
            return
        }
        val prefs = users.getPrefs(userId)
        val fired = users.firedKeys(userId)
        val planned = ReminderPlanner.plan(
            userId = userId,
            profile = profile,
            prefs = prefs,
            now = ZonedDateTime.now(),
            fired = fired,
        )
        val previous = scheduleStore.names()
        val next = planned.map { it.workName }.toSet()
        (previous - next).forEach { name -> work.cancelUniqueWork(name) }
        planned.forEach { item ->
            val data = workDataOf(
                KEY_USER to userId,
                KEY_TYPE to item.type,
                KEY_PHASE to item.phase,
                KEY_EPOCH to item.eventDay.toEpochDay(),
            )
            val request = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(item.delayMillis, TimeUnit.MILLISECONDS)
                .setInputData(data)
                .build()
            work.enqueueUniqueWork(item.workName, ExistingWorkPolicy.REPLACE, request)
        }
        scheduleStore.save(next)
    }

    suspend fun rescheduleCurrent() {
        val id = session.currentUserId() ?: return
        if (users.getProfile(id) == null) return
        reschedule(id)
    }

    companion object {
        const val PERIODIC_NAME = "lovely-reschedule"
        const val KEY_USER = "userId"
        const val KEY_TYPE = "type"
        const val KEY_PHASE = "phase"
        const val KEY_EPOCH = "eventEpochDay"
    }
}

class ReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val userId = inputData.getLong(NotificationScheduler.KEY_USER, -1L)
        val type = inputData.getString(NotificationScheduler.KEY_TYPE) ?: return Result.success()
        val phase = inputData.getString(NotificationScheduler.KEY_PHASE) ?: return Result.success()
        val epoch = inputData.getLong(NotificationScheduler.KEY_EPOCH, Long.MIN_VALUE)
        if (userId < 0L || epoch == Long.MIN_VALUE) return Result.success()

        val container = (applicationContext as? LovelyApplication)?.container ?: return Result.retry()
        if (container.session.currentUserId() != userId) return Result.success()
        val key = ReminderPlanner.dedupeKey(userId, type, phase, LocalDate.ofEpochDay(epoch))
        if (container.users.isFired(userId, key)) return Result.success()
        if (!Notifier.canPost(applicationContext)) return Result.success()

        val profile = container.users.getProfile(userId) ?: return Result.success()
        val prefs = container.users.getPrefs(userId)
        val eventDay = LocalDate.ofEpochDay(epoch)
        if (!ReminderPlanner.stillRelevant(profile, prefs, type, phase, eventDay, LocalDate.now())) {
            return Result.success()
        }
        try {
            Notifier.show(applicationContext, type, phase, eventDay)
        } catch (_: SecurityException) {
            return Result.success()
        }
        container.users.markFired(userId, key)
        return Result.success()
    }
}

class RescheduleWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as? LovelyApplication)?.container ?: return Result.retry()
        container.scheduler.rescheduleCurrent()
        return Result.success()
    }
}
