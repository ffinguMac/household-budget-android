package com.householdbudget.app.ui.autoentry

import com.householdbudget.app.domain.autoentry.KnownApps
import org.junit.Assert.assertEquals
import org.junit.Test

class InstalledAppsTest {
    @Test fun knownAppsComeFirst_thenLabelOrder() = assertEquals(
        listOf("com.kbankwith.smartbank", "viva.republica.toss", "com.fake.a", "com.fake.b"),
        sortSelectableApps(
            listOf(AppInfo("com.fake.b", "나"), AppInfo("viva.republica.toss", "토스"), AppInfo("com.fake.a", "가"),
                AppInfo("com.kbankwith.smartbank", "케이뱅크"), AppInfo("com.fake.a", "가")),
            KnownApps.FINANCE_PACKAGES).map { it.packageName })

    @Test fun splitWatchableApps_primaryIsFinanceWatchedAndShell_othersRest() {
        val apps = sortSelectableApps(
            listOf(AppInfo("com.fake.b", "나"), AppInfo("viva.republica.toss", "토스"), AppInfo("com.fake.a", "가"),
                AppInfo("com.kbankwith.smartbank", "케이뱅크"), AppInfo("com.fake.w", "다"),
                AppInfo(com.householdbudget.app.autoentry.WatchedApps.SHELL_PACKAGE, "셸")),
            KnownApps.FINANCE_PACKAGES)
        val (primary, others) = splitWatchableApps(apps, setOf("com.fake.w"), KnownApps.FINANCE_PACKAGES)
        assertEquals(
            listOf("com.kbankwith.smartbank", "viva.republica.toss", "com.fake.w",
                com.householdbudget.app.autoentry.WatchedApps.SHELL_PACKAGE).sorted(),
            primary.map { it.packageName }.sorted())
        assertEquals(listOf("com.kbankwith.smartbank", "viva.republica.toss"), primary.map { it.packageName }.take(2))
        assertEquals(listOf("com.fake.a", "com.fake.b"), others.map { it.packageName })
    }
}
