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
}
