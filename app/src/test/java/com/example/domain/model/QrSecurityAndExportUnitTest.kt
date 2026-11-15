package com.example.domain.model

import com.example.export.ExcelExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

class QrSecurityAndExportUnitTest {

    @Test
    fun `qr payload contains no PII and verifies signature cleanly`() {
        val secret = QrSecurity.generateRandomSecretHex()
        val token = QrSecurity.generateRandomQrToken()
        val classroom = Classroom(id = "cls_10a1", name = "10A1", qrSecret = secret)
        val student = Student(
            id = "stu_01",
            studentCode = "HS1001",
            fullName = "Nguyễn Hoàng Bảo Ngọc",
            classId = "cls_10a1",
            qrToken = token,
            qrVersion = 1L,
            active = true
        )

        val payload = QrSecurity.buildQrPayload(
            classId = classroom.id,
            studentId = student.id,
            qrToken = student.qrToken,
            qrVersion = student.qrVersion,
            classQrSecret = classroom.qrSecret
        )

        assertTrue(payload.startsWith("QRA1:"))
        assertFalse(payload.contains("Ngọc"))
        assertFalse(payload.contains("HS1001"))
        assertFalse(payload.contains("10A1"))

        val parsed = QrSecurity.parsePayload(payload) as QrSecurity.ParseResult.ValidFormat
        assertTrue(QrSecurity.verifySignature(parsed, classroom, student))

        // Revoking QR version invalidates old QR card immediately
        val rotatedStudent = student.copy(qrVersion = 2L)
        assertFalse(QrSecurity.verifySignature(parsed, classroom, rotatedStudent))

        // Different class secret rejects forged/wrong-class QR
        val otherClass = classroom.copy(id = "cls_10a2", qrSecret = QrSecurity.generateRandomSecretHex())
        assertFalse(QrSecurity.verifySignature(parsed, otherClass, student))
    }

    @Test
    fun `stats calculator computes accurate rates and at-risk flags`() {
        val s1 = Student(id = "s1", studentCode = "HS01", fullName = "An")
        val s2 = Student(id = "s2", studentCode = "HS02", fullName = "Bình")
        val records = listOf(
            AttendanceRecord(studentId = "s1", status = AttendanceStatus.PRESENT.key),
            AttendanceRecord(studentId = "s1", status = AttendanceStatus.LATE.key),
            AttendanceRecord(studentId = "s2", status = AttendanceStatus.ABSENT_UNEXCUSED.key),
            AttendanceRecord(studentId = "s2", status = AttendanceStatus.ABSENT_UNEXCUSED.key),
            AttendanceRecord(studentId = "s2", status = AttendanceStatus.ABSENT_UNEXCUSED.key)
        )
        val summary = StatsCalculator.computeClassSummary(
            students = listOf(s1, s2),
            filteredRecords = records,
            filteredSessions = emptyList()
        )
        assertEquals(5, summary.totalRecords)
        assertEquals(1, summary.presentCount)
        assertEquals(1, summary.lateCount)
        assertEquals(3, summary.unexcusedCount)
        val binSummary = summary.studentSummaries.first { it.student.id == "s2" }
        assertTrue(binSummary.isAtRisk)
    }

    @Test
    fun `excel exporter produces valid multi-sheet xlsx zip archive`() {
        val s1 = Student(id = "s1", studentCode = "HS01", fullName = "Nguyễn Văn A")
        val summary = StatsCalculator.computeClassSummary(
            students = listOf(s1),
            filteredRecords = listOf(
                AttendanceRecord(
                    sessionDate = "2026-11-14",
                    className = "10A1",
                    subject = "Toán học",
                    studentId = "s1",
                    studentCode = "HS01",
                    studentName = "Nguyễn Văn A",
                    status = AttendanceStatus.PRESENT.key,
                    scanMethod = ScanMethod.QR_SCAN.key,
                    checkedInAtMillis = 1794643200000L
                )
            ),
            filteredSessions = emptyList()
        )
        val bytes = ExcelExporter.generateAttendanceWorkbookBytes(
            className = "10A1",
            schoolYearName = "2025 - 2026",
            period = StatsPeriod.MONTH,
            summary = summary,
            records = emptyList(),
            auditLogs = emptyList()
        )
        val entries = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                entries.add(entry.name)
                entry = zis.nextEntry
            }
        }
        assertTrue(entries.contains("xl/workbook.xml"))
        assertTrue(entries.contains("xl/worksheets/sheet1.xml"))
        assertTrue(entries.contains("xl/worksheets/sheet2.xml"))
        assertTrue(entries.contains("xl/worksheets/sheet3.xml"))
    }
}
