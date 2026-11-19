package com.householdbudget.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class KoreanHolidaysTest {

    private fun d(iso: String) = LocalDate.parse(iso)

    @Test
    fun countPerYear_matchesPlanTable() {
        val expected = mapOf(2024 to 19, 2025 to 19, 2026 to 22, 2027 to 24, 2028 to 18, 2029 to 20, 2030 to 19)
        assertEquals(expected, KoreanHolidays.dates.groupingBy { it.year }.eachCount().toSortedMap())
        assertEquals(141, KoreanHolidays.dates.size)
    }

    @Test
    fun seollalAndChuseok_eachYear() {
        listOf(
            "2024-02-10", "2024-09-17", "2025-01-29", "2025-10-06", "2026-02-17", "2026-09-25",
            "2027-02-07", "2027-09-15", "2028-01-27", "2028-10-03", "2029-02-13", "2029-09-22",
            "2030-02-03", "2030-09-12",
        ).forEach { assertTrue(it, KoreanHolidays.isHoliday(d(it))) }
    }

    @Test
    fun substituteTemporaryAndElectionDays() {
        listOf(
            "2024-02-12", "2024-04-10", "2024-05-06", "2024-10-01",
            "2025-01-27", "2025-03-03", "2025-05-06", "2025-06-03", "2025-10-08",
            "2026-03-02", "2026-05-25", "2026-06-03", "2026-08-17", "2026-10-05",
            "2027-02-09", "2027-05-03", "2027-07-19", "2027-08-16", "2027-10-04", "2027-10-11", "2027-12-27",
            "2028-04-12", "2028-10-05",
            "2029-05-07", "2029-05-21", "2029-09-24",
            "2030-02-05", "2030-05-06",
        ).forEach { assertTrue(it, KoreanHolidays.isHoliday(d(it))) }
    }

    @Test
    fun laborDayAndConstitutionDay_onlyFrom2026() {
        listOf("2024-05-01", "2025-05-01", "2024-07-17", "2025-07-17").forEach {
            assertFalse(it, KoreanHolidays.isHoliday(d(it)))
        }
        listOf("2026-05-01", "2026-07-17", "2030-05-01", "2030-07-17").forEach {
            assertTrue(it, KoreanHolidays.isHoliday(d(it)))
        }
    }

    @Test
    fun ordinaryDaysAndUnannouncedDays_areNotHolidays() {
        // 평일, 대체 없는 2028-01-01 다음 월요일, 미확정 2030 선거 추정일
        listOf("2026-11-20", "2028-01-03", "2030-03-27", "2030-06-12").forEach {
            assertFalse(it, KoreanHolidays.isHoliday(d(it)))
        }
    }

    @Test
    fun outsideTable_isNeverHoliday() {
        listOf("2023-12-25", "2031-01-01", "2031-03-01").forEach {
            assertFalse(it, KoreanHolidays.isHoliday(d(it)))
        }
    }
}
