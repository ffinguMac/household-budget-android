package com.householdbudget.app.data.export

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.householdbudget.app.data.repository.BudgetRepository
import com.householdbudget.app.domain.CategoryKind
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** 기간 내 거래를 CSV 파일로 내보내고 공유 Intent 를 만든다. */
object CsvExporter {
    private const val AUTHORITY = "com.householdbudget.app.fileprovider"
    private val FILE_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val ROW_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    /**
     * [start]..[endInclusive] 의 거래를 CSV(UTF-8 BOM, 엑셀 한글 호환)로 cacheDir/exports 에 쓰고,
     * FileProvider URI 를 담은 공유 chooser Intent 를 반환한다.
     */
    suspend fun exportTransactionsCsv(
        context: Context,
        repository: BudgetRepository,
        start: LocalDate,
        endInclusive: LocalDate,
    ): Intent = withContext(Dispatchers.IO) {
        val rows =
            repository
                .observeTransactionsInRange(start.toEpochDay(), endInclusive.toEpochDay() + 1)
                .first()
                .sortedWith(compareBy({ it.occurredEpochDay }, { it.id }))

        val sb = StringBuilder()
        // UTF-8 BOM: 엑셀에서 한글이 깨지지 않도록.
        sb.append('\uFEFF')
        sb.append("날짜,종류,대분류,소분류,금액,메모\r\n")
        for (row in rows) {
            val date = ROW_DATE.format(LocalDate.ofEpochDay(row.occurredEpochDay))
            val kind = kindLabel(row.kind)
            val parent = row.parentCategoryName ?: row.categoryName
            val leaf = if (row.parentCategoryName == null) "" else row.categoryName
            sb.append(escape(date)).append(',')
                .append(escape(kind)).append(',')
                .append(escape(parent)).append(',')
                .append(escape(leaf)).append(',')
                .append(row.amountMinor.toString()).append(',')
                .append(escape(row.memo))
                .append("\r\n")
        }

        val dir = File(context.cacheDir, "exports")
        dir.mkdirs()
        val fileName = "가계부_${FILE_DATE.format(start)}-${FILE_DATE.format(endInclusive)}.csv"
        val file = File(dir, fileName)
        file.writeText(sb.toString(), Charsets.UTF_8)

        val uri = FileProvider.getUriForFile(context, AUTHORITY, file)
        val send =
            Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri(fileName, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        Intent.createChooser(send, fileName).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun kindLabel(storage: String): String =
        when (CategoryKind.fromStorage(storage)) {
            CategoryKind.INCOME -> "수입"
            CategoryKind.EXPENSE -> "지출"
            CategoryKind.SAVINGS -> "저축"
        }

    /** RFC 4180: 콤마/따옴표/개행 포함 시 따옴표로 감싸고 내부 따옴표는 이중화. */
    private fun escape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
}
