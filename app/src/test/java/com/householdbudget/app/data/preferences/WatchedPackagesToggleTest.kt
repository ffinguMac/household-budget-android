package com.householdbudget.app.data.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class WatchedPackagesToggleTest {
    private fun repo() = UserPreferencesRepository(ApplicationProvider.getApplicationContext<Context>())

    @Test fun firstToggleWithNothingStored_writesDefaultsPlusPackage() = runBlocking {
        val r = repo()
        r.toggleWatchedPackage("com.fake.b", true, setOf("com.fake.a"))
        assertEquals(setOf("com.fake.a", "com.fake.b"), r.watchedPackagesOrNull.first())
    }

    @Test fun consecutiveToggles_bothPersist() = runBlocking {
        val r = repo()
        r.toggleWatchedPackage("com.fake.b", true, setOf("com.fake.a"))
        r.toggleWatchedPackage("com.fake.c", true, setOf("com.fake.a"))
        r.toggleWatchedPackage("com.fake.a", false, setOf("com.fake.a"))
        assertEquals(setOf("com.fake.b", "com.fake.c"), r.watchedPackagesOrNull.first())
    }
}
