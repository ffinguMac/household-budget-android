package com.householdbudget.app.data.export

import org.junit.Assert.assertEquals
import org.junit.Test

class CsvExporterTest {
    @Test fun csvCell_prefixesFormulaTriggers() {
        assertEquals("'=1+1", CsvExporter.csvCell("=1+1"))
        assertEquals("'+82", CsvExporter.csvCell("+82"))
        assertEquals("'-5000 환불", CsvExporter.csvCell("-5000 환불"))
        assertEquals("\"'@SUM(A1,B1)\"", CsvExporter.csvCell("@SUM(A1,B1)"))
    }

    @Test fun csvCell_leavesOrdinaryTextAlone() {
        assertEquals("스타벅스", CsvExporter.csvCell("스타벅스")); assertEquals("", CsvExporter.csvCell(""))
        assertEquals("a=b", CsvExporter.csvCell("a=b"))
    }
}
