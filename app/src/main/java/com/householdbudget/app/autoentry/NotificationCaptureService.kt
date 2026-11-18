package com.householdbudget.app.autoentry

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.householdbudget.app.BudgetApplication
import com.householdbudget.app.data.repository.IncomingNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class NotificationCaptureService : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val repo = (application as? BudgetApplication)?.container?.autoEntryRepository ?: return
        val n = sbn.notification
        val extras = n.extras
        val incoming = IncomingNotification(
            packageName = sbn.packageName,
            key = sbn.key,
            postTimeMs = sbn.postTime,
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
            text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
            bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty(),
            isGroupSummary = (n.flags and Notification.FLAG_GROUP_SUMMARY) != 0,
        )
        // 내용은 로그에 남기지 않고 실패는 삼킨다.
        scope.launch { runCatching { repo.handleNotification(incoming) } }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
