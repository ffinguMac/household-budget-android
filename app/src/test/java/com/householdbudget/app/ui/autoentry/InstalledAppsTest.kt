package com.householdbudget.app.ui.autoentry

import com.householdbudget.app.autoentry.WatchedApps
import com.householdbudget.app.domain.autoentry.KnownApps
import org.junit.Assert.assertEquals
import org.junit.Test

class InstalledAppsTest {
    @Test fun onlyKnownFinanceApps_inKnownOrder_shellLast() = assertEquals(
        listOf("com.kbankwith.smartbank", "viva.republica.toss", WatchedApps.SHELL_PACKAGE),
        selectableFinanceApps(
            listOf(AppInfo("com.fake.b", "나"), AppInfo(WatchedApps.SHELL_PACKAGE, "셸"), AppInfo("viva.republica.toss", "토스"),
                AppInfo("com.fake.a", "가"), AppInfo("com.kbankwith.smartbank", "케이뱅크"), AppInfo("com.kbankwith.smartbank", "케이뱅크")),
            KnownApps.FINANCE_PACKAGES).map { it.packageName })
}
