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
}
