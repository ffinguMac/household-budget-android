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

    /** 뮤텍스 앞 사전 검사. PackageManager를 부르지 않는다. */
    fun isCandidate(pkg: String, stored: Set<String>?, debuggable: Boolean): Boolean =
        if (stored != null) pkg in stored else pkg in KnownApps.FINANCE_PACKAGES || (debuggable && pkg == SHELL_PACKAGE)

    // shell은 런처가 없어 <queries> 가시성 밖이라 debuggable 빌드에서는 설치 확인 없이 둔다.
    fun filterInstalled(packages: Set<String>, debuggable: Boolean, isInstalled: (String) -> Boolean): Set<String> =
        packages.filterTo(mutableSetOf()) { isInstalled(it) || (debuggable && it == SHELL_PACKAGE) }

    // 저장값도 설치된 앱만 센다. 지운 페이 앱이 남아 있으면 충전이 "기록 안 함"이 된다.
    fun effective(stored: Set<String>?, context: Context): Set<String> {
        if (stored == null) return defaults(context)
        val pm = context.packageManager
        val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        return filterInstalled(stored, debuggable) { runCatching { pm.getPackageInfo(it, 0) }.isSuccess }
    }
}
