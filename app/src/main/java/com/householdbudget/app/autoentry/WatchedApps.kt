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

    /** 알림을 읽을 수 있는 앱은 정해 둔 금융 앱(+debug 빌드의 shell)뿐이다. 그 밖의 앱은 저장돼 있어도 무시한다. */
    fun isAllowed(pkg: String, debuggable: Boolean): Boolean =
        pkg in KnownApps.FINANCE_PACKAGES || (debuggable && pkg == SHELL_PACKAGE)
}
