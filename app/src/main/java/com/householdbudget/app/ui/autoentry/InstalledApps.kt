package com.householdbudget.app.ui.autoentry

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import com.householdbudget.app.R
import com.householdbudget.app.autoentry.WatchedApps
import com.householdbudget.app.domain.autoentry.KnownApps

data class AppInfo(val packageName: String, val label: String)

/** 정해 둔 금융 앱만 [known] 순서로 남긴다. 그 밖의 앱은 고를 수 없다(shell 은 debug 테스트용으로 끝에). */
fun selectableFinanceApps(apps: List<AppInfo>, known: List<String>): List<AppInfo> =
    apps.distinctBy { it.packageName }
        .filter { it.packageName in known || it.packageName == WatchedApps.SHELL_PACKAGE }
        .sortedBy { known.indexOf(it.packageName).let { i -> if (i < 0) Int.MAX_VALUE else i } }

fun loadSelectableApps(context: Context): List<AppInfo> {
    val pm = context.packageManager
    val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val apps = pm.queryIntentActivities(launcher, 0)
        .filter { it.activityInfo.packageName != context.packageName }
        .map { AppInfo(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
        .toMutableList()
    if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
        apps += AppInfo(WatchedApps.SHELL_PACKAGE, context.getString(R.string.auto_shell_label))
    }
    return selectableFinanceApps(apps, KnownApps.FINANCE_PACKAGES)
}
