package com.householdbudget.app.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockGateTest {
    @Test fun lockGateState_loadingUntilPreferenceKnown() {
        assertEquals(LockGateState.LOADING, lockGateState(null, unlocked = false, canAuthenticate = true))
        assertEquals(LockGateState.LOADING, lockGateState(null, unlocked = true, canAuthenticate = false))
    }

    @Test fun lockGateState_lockedOnlyWhenEnabledAndAuthAvailable() {
        assertEquals(LockGateState.LOCKED, lockGateState(true, false, true))
        assertEquals(LockGateState.OPEN, lockGateState(true, true, true))
        assertEquals(LockGateState.OPEN, lockGateState(true, false, false)) // 인증 수단 없으면 잠그지 않는다(기존 동작)
        assertEquals(LockGateState.OPEN, lockGateState(false, false, true))
    }

    @Test fun shouldRelockAfterBackground_onlyFromSixtySeconds() {
        assertEquals(60_000L, RELOCK_AFTER_BACKGROUND_MILLIS)
        assertFalse(shouldRelockAfterBackground(0L))
        assertFalse(shouldRelockAfterBackground(59_999L)) // 공유 시트·은행 앱 잠깐 다녀옴
        assertTrue(shouldRelockAfterBackground(60_000L)) // 정확히 60초는 잠근다
        assertTrue(shouldRelockAfterBackground(3_600_000L))
    }
}
