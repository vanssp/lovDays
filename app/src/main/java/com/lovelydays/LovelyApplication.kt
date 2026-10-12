package com.lovelydays

import android.app.Application
import android.content.Context
import androidx.work.Configuration
import com.lovelydays.data.auth.AuthRepository
import com.lovelydays.data.calendar.DeviceCalendarSync
import com.lovelydays.data.db.LovelyDatabase
import com.lovelydays.data.repo.UserDataRepository
import com.lovelydays.data.session.ScheduleStore
import com.lovelydays.data.session.SessionStore
import com.lovelydays.notify.Channels
import com.lovelydays.notify.NotificationScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val database: LovelyDatabase = LovelyDatabase.create(appContext)
    val session = SessionStore(appContext)
    val scheduleStore = ScheduleStore(appContext)
    val users = UserDataRepository(database)
    val auth = AuthRepository(database.users(), session)
    val scheduler = NotificationScheduler(appContext, session, users, scheduleStore)
    val calendars = DeviceCalendarSync(appContext, users)

    suspend fun logout() {
        scheduler.cancelAll()
        session.clear()
    }
}

class LovelyApplication : Application(), Configuration.Provider {
    lateinit var container: AppContainer
        private set

    override val workManagerConfiguration: Configuration =
        Configuration.Builder().build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base.withRussianLocale())
    }

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Channels.ensure(this)
        scope.launch {
            runCatching {
                container.scheduler.ensurePeriodic()
                container.scheduler.rescheduleCurrent()
            }
        }
    }

    companion object {
        fun from(context: Context): LovelyApplication =
            context.applicationContext as LovelyApplication
    }
}
