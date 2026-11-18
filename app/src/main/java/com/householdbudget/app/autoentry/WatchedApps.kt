package com.householdbudget.app.autoentry

import android.content.Context
import android.content.pm.ApplicationInfo
import com.householdbudget.app.domain.autoentry.KnownApps

object WatchedApps {
    const val SHELL_PACKAGE = "com.android.shell"

    // 설치되지 않은 결제 앱을 대상으로 세면 충전이 "기록 안 함"이 되어 지출이 사라진다.
    fun defaults(context: Context): Set<String> {
        val pm = context.packageManager
        val installed = KnownApps.FINANCE_PACKAGES.filterTo(mutableSetOf()) {
            runCatching { pm.getPackageInfo(it, 0) }.isSuccess
        }
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) installed += SHELL_PACKAGE
        return installed
    }

    fun effective(stored: Set<String>?, context: Context): Set<String> = stored ?: defaults(context)
}
