package com.householdbudget.app.data.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** 매일 [schedule] 에 지정된 시각에 기록 리마인더 알림을 예약한다. */
object ReminderScheduler {
    private const val UNIQUE_NAME = "record_reminder"

    fun schedule(context: Context, hourOfDay: Int, replace: Boolean = true) {
        require(hourOfDay in 0..23)
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(hourOfDay, 0)
        if (!next.isAfter(now)) {
            next = next.plusDays(1)
        }
        val initialDelayMs = Duration.between(now, next).toMillis()
        val request =
            PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
                .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                // shortcut: KEEP 이면 Doze·시간대 변경으로 밀린 시각이 설정에서 다시 저장할 때까지 유지됨, 정시가 중요해지면 시작 때 재정렬 추가
                // 앱 시작 때는 KEEP: 리마인더 시각에 WorkManager 가 프로세스를 깨워도 오늘 실행이 내일로 밀리지 않게.
                if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_NAME)
    }
}
