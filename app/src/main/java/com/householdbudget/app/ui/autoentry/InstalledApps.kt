package com.householdbudget.app.ui.autoentry

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import com.householdbudget.app.R
import com.householdbudget.app.autoentry.WatchedApps
import com.householdbudget.app.domain.autoentry.KnownApps

data class AppInfo(val packageName: String, val label: String)

fun sortSelectableApps(apps: List<AppInfo>, known: List<String>): List<AppInfo> {
    val unique = apps.distinctBy { it.packageName }
    val (knownApps, others) = unique.partition { it.packageName in known }
    return knownApps.sortedBy { known.indexOf(it.packageName) } + others.sortedBy { it.label }
}

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
    return sortSelectableApps(apps, KnownApps.FINANCE_PACKAGES)
}
