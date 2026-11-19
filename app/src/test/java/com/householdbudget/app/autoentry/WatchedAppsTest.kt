package com.householdbudget.app.autoentry

import android.content.Context
import android.content.pm.PackageInfo
import androidx.test.core.app.ApplicationProvider
import com.householdbudget.app.domain.autoentry.NotificationParsers
import org.junit.Assert.assertEquals
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

    @Test fun effective_prefersStoredSet_evenWhenEmpty() =
        assertEquals(emptySet<String>(), WatchedApps.effective(emptySet(), ctx))

    @Test fun defaults_onlyIncludeInstalledKnownApps() {
        shadowOf(ctx.packageManager).installPackage(
            PackageInfo().apply { packageName = NotificationParsers.KBANK_PACKAGE },
        )
        val d = WatchedApps.defaults(ctx)
        assertTrue(NotificationParsers.KBANK_PACKAGE in d)
        assertFalse("viva.republica.toss" in d)
    }

    @Test fun filterInstalled_dropsUninstalledPayApp() =
        assertEquals(setOf("a"), WatchedApps.filterInstalled(setOf("a", "com.kakaopay.app"), false) { it == "a" })

    @Test fun filterInstalled_allUninstalled_isEmpty() =
        assertEquals(emptySet<String>(), WatchedApps.filterInstalled(setOf("x", "y"), false) { false })

    @Test fun filterInstalled_keepsShellOnlyWhenDebuggable() {
        assertEquals(setOf(WatchedApps.SHELL_PACKAGE), WatchedApps.filterInstalled(setOf(WatchedApps.SHELL_PACKAGE), true) { false })
        assertEquals(emptySet<String>(), WatchedApps.filterInstalled(setOf(WatchedApps.SHELL_PACKAGE), false) { false })
    }

    @Test fun effective_intersectsStoredWithInstalled() {
        shadowOf(ctx.packageManager).installPackage(PackageInfo().apply { packageName = NotificationParsers.KBANK_PACKAGE })
        assertEquals(setOf(NotificationParsers.KBANK_PACKAGE),
            WatchedApps.effective(setOf(NotificationParsers.KBANK_PACKAGE, "viva.republica.toss"), ctx))
    }

    @Test fun isCandidate_storedSetWins_elseKnownFinanceApps() {
        assertTrue(WatchedApps.isCandidate("x", setOf("x"), false))
        assertFalse(WatchedApps.isCandidate(NotificationParsers.KBANK_PACKAGE, setOf("x"), false))
        assertTrue(WatchedApps.isCandidate(NotificationParsers.KBANK_PACKAGE, null, false))
        assertFalse(WatchedApps.isCandidate(WatchedApps.SHELL_PACKAGE, null, false))
        assertTrue(WatchedApps.isCandidate(WatchedApps.SHELL_PACKAGE, null, true))
    }
}
