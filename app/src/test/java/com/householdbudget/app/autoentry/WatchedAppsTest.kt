package com.householdbudget.app.autoentry

import android.content.Context
import android.content.pm.PackageInfo
import androidx.test.core.app.ApplicationProvider
import com.householdbudget.app.domain.autoentry.NotificationParsers
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class WatchedAppsTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Test fun defaults_onlyIncludeInstalledKnownApps() {
        shadowOf(ctx.packageManager).installPackage(PackageInfo().apply { packageName = NotificationParsers.KBANK_PACKAGE })
        shadowOf(ctx.packageManager).installPackage(PackageInfo().apply { packageName = "com.fake.chat" })
        val d = WatchedApps.defaults(ctx)
        assertTrue(NotificationParsers.KBANK_PACKAGE in d)
        assertFalse("viva.republica.toss" in d)
        assertFalse("com.fake.chat" in d)
    }

    @Test fun isAllowed_onlyFinanceApps_shellOnlyWhenDebuggable() {
        assertTrue(WatchedApps.isAllowed(NotificationParsers.KBANK_PACKAGE, false))
        assertFalse(WatchedApps.isAllowed("com.fake.chat", false))
        assertFalse(WatchedApps.isAllowed(WatchedApps.SHELL_PACKAGE, false))
        assertTrue(WatchedApps.isAllowed(WatchedApps.SHELL_PACKAGE, true))
    }
}
