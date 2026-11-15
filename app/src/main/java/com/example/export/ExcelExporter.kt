package com.example.export

import com.example.domain.model.AttendanceAuditLog
import com.example.domain.model.AttendanceRecord
import com.example.domain.model.ClassAttendanceSummary
import com.example.domain.model.StatsCalculator
import com.example.domain.model.StatsPeriod
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Pure Kotlin OOXML (.xlsx) workbook generator producing a multi-sheet Excel file
 * with full UTF-8 Vietnamese support and zero bloated third-party POI dependencies.
 */
object ExcelExporter {

    fun generateAttendanceWorkbookBytes(
        className: String,
        schoolYearName: String,
        period: StatsPeriod,
        summary: ClassAttendanceSummary,
        records: List<AttendanceRecord>,
        auditLogs: List<AttendanceAuditLog>
    ): ByteArray {
        val sheet1Rows = buildSummaryRows(className, schoolYearName, period, summary)
        val sheet2Rows = buildDetailRows(records)
        val sheet3Rows = buildAuditRows(auditLogs)

        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->
            writeZipEntry(zip, "[Content_Types].xml", contentTypesXml())
            writeZipEntry(zip, "_rels/.rels", rootRelsXml())
            writeZipEntry(zip, "xl/workbook.xml", workbookXml())
            writeZipEntry(zip, "xl/_rels/workbook.xml.rels", workbookRelsXml())
            writeZipEntry(zip, "xl/styles.xml", stylesXml())
            writeZipEntry(zip, "xl/worksheets/sheet1.xml", worksheetXml(sheet1Rows))
            writeZipEntry(zip, "xl/worksheets/sheet2.xml", worksheetXml(sheet2Rows))
            writeZipEntry(zip, "xl/worksheets/sheet3.xml", worksheetXml(sheet3Rows))
        }
        return baos.toByteArray()
    }

    private fun buildSummaryRows(
        className: String,
        schoolYearName: String,
        period: StatsPeriod,
        summary: ClassAttendanceSummary
    ): List<List<CellData>> {
        val rows = mutableListOf<List<CellData>>()
        rows.add(listOf(CellData.text("BÁO CÁO THỐNG KÊ CHUYÊN CẦN - QUẢN LÝ LỚP BẰNG MÃ QR - PP HẬU", bold = true)))
        rows.add(listOf(CellData.text("Lớp: $className | Năm học: $schoolYearName | Kỳ: ${period.labelVi}")))
        rows.add(listOf(CellData.text("Tổng số buổi: ${summary.totalSessions} | Tỉ lệ chuyên cần: ${"%.1f".format(summary.attendanceRatePercent)}%")))
        rows.add(emptyList())
        rows.add(
            listOf(
                CellData.text("STT", bold = true),
                CellData.text("Mã HS", bold = true),
                CellData.text("Họ và tên", bold = true),
                CellData.text("Giới tính", bold = true),
                CellData.text("Có mặt", bold = true),
                CellData.text("Đi trễ", bold = true),
                CellData.text("Vắng có phép", bold = true),
                CellData.text("Vắng không phép", bold = true),
                CellData.text("Tỉ lệ chuyên cần (%)", bold = true)
            )
        )
        summary.studentSummaries.forEachIndexed { idx, s ->
            rows.add(
                listOf(
                    CellData.number((idx + 1).toDouble()),
                    CellData.text(s.student.studentCode),
                    CellData.text(s.student.fullName),
                    CellData.text(s.student.gender),
                    CellData.number(s.presentCount.toDouble()),
                    CellData.number(s.lateCount.toDouble()),
                    CellData.number(s.excusedCount.toDouble()),
                    CellData.number(s.unexcusedCount.toDouble()),
                    CellData.number(Math.round(s.attendanceRatePercent * 10.0) / 10.0)
                )
            )
        }
        return rows
    }

    private fun buildDetailRows(records: List<AttendanceRecord>): List<List<CellData>> {
        val rows = mutableListOf<List<CellData>>()
        rows.add(
            listOf(
                CellData.text("Ngày học", bold = true),
                CellData.text("Lớp", bold = true),
                CellData.text("Môn học", bold = true),
                CellData.text("Mã HS", bold = true),
                CellData.text("Họ tên học sinh", bold = true),
                CellData.text("Trạng thái", bold = true),
                CellData.text("Hình thức", bold = true),
                CellData.text("Thời gian điểm danh", bold = true),
                CellData.text("Ghi chú", bold = true)
            )
        )
        records.forEach { r ->
            rows.add(
                listOf(
                    CellData.text(r.sessionDate),
                    CellData.text(r.className),
                    CellData.text(r.subject),
                    CellData.text(r.studentCode),
                    CellData.text(r.studentName),
                    CellData.text(r.attendanceStatus.labelVi),
                    CellData.text(r.method.labelVi),
                    CellData.text(StatsCalculator.formatDateTimeVi(r.checkedInAtMillis)),
                    CellData.text(r.note)
                )
            )
        }
        return rows
    }

    private fun buildAuditRows(auditLogs: List<AttendanceAuditLog>): List<List<CellData>> {
        val rows = mutableListOf<List<CellData>>()
        rows.add(
            listOf(
                CellData.text("Thời gian chỉnh sửa", bold = true),
                CellData.text("Lớp", bold = true),
                CellData.text("Học sinh", bold = true),
                CellData.text("Trạng thái cũ", bold = true),
                CellData.text("Trạng thái mới", bold = true),
                CellData.text("Lý do chỉnh sửa", bold = true),
                CellData.text("Người chỉnh sửa", bold = true)
            )
        )
        auditLogs.forEach { log ->
            val timeStr = log.createdAt?.toDate()?.time?.let { StatsCalculator.formatDateTimeVi(it) } ?: "--"
            rows.add(
                listOf(
                    CellData.text(timeStr),
                    CellData.text(log.className),
                    CellData.text(log.studentName),
                    CellData.text(log.previous.labelVi),
                    CellData.text(log.updated.labelVi),
                    CellData.text(log.reason),
                    CellData.text(log.editorName)
                )
            )
        }
        return rows
    }

    private data class CellData(
        val isNumber: Boolean,
        val value: String,
        val bold: Boolean = false
    ) {
        companion object {
            fun text(v: String, bold: Boolean = false) = CellData(false, v, bold)
            fun number(v: Double) = CellData(true, v.toString(), false)
        }
    }

    private fun writeZipEntry(zip: ZipOutputStream, path: String, content: String) {
        zip.putNextEntry(ZipEntry(path))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun escapeXml(input: String): String = buildString(input.length) {
        for (c in input) {
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> if (c.code >= 0x20 || c == '\n' || c == '\r' || c == '\t') append(c)
            }
        }
    }

    private fun colLetter(index: Int): String {
        var n = index
        val sb = StringBuilder()
        while (n >= 0) {
            sb.append(('A' + (n % 26)))
            n = n / 26 - 1
        }
        return sb.reverse().toString()
    }

    private fun worksheetXml(rows: List<List<CellData>>): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
        rows.forEachIndexed { rowIdx, row ->
            val rNum = rowIdx + 1
            sb.append("""<row r="$rNum">""")
            row.forEachIndexed { colIdx, cell ->
                val ref = "${colLetter(colIdx)}$rNum"
                val styleAttr = if (cell.bold) """ s="1"""" else ""
                if (cell.isNumber) {
                    sb.append("""<c r="$ref"$styleAttr><v>${escapeXml(cell.value)}</v></c>""")
                } else {
                    sb.append("""<c r="$ref" t="inlineStr"$styleAttr><is><t>${escapeXml(cell.value)}</t></is></c>""")
                }
            }
            sb.append("</row>")
        }
        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    private fun contentTypesXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
          <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
          <Default Extension="xml" ContentType="application/xml"/>
          <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
          <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
          <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
          <Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
          <Override PartName="/xl/worksheets/sheet3.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
        </Types>
    """.trimIndent()

    private fun rootRelsXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
        </Relationships>
    """.trimIndent()

    private fun workbookXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
                  xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
          <sheets>
            <sheet name="Tổng hợp chuyên cần" sheetId="1" r:id="rId1"/>
            <sheet name="Chi tiết điểm danh" sheetId="2" r:id="rId2"/>
            <sheet name="Nhật ký chỉnh sửa" sheetId="3" r:id="rId3"/>
          </sheets>
        </workbook>
    """.trimIndent()

    private fun workbookRelsXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
          <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
          <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet3.xml"/>
          <Relationship Id="rId4" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
        </Relationships>
    """.trimIndent()

    private fun stylesXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
          <fonts count="2">
            <font><sz val="11"/><name val="Calibri"/></font>
            <font><b/><sz val="11"/><name val="Calibri"/></font>
          </fonts>
          <fills count="2">
            <fill><patternFill patternType="none"/></fill>
            <fill><patternFill patternType="gray125"/></fill>
          </fills>
          <borders count="1">
            <border><left/><right/><top/><bottom/><diagonal/></border>
          </borders>
          <cellStyleXfs count="1">
            <xf numFmtId="0" fontId="0" fillId="0" borderId="0"/>
          </cellStyleXfs>
          <cellXfs count="2">
            <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
            <xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/>
          </cellXfs>
        </styleSheet>
    """.trimIndent()
}
