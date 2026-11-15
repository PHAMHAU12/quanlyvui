package com.example.export

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.example.domain.model.ClassAttendanceSummary
import com.example.domain.model.Classroom
import com.example.domain.model.QrCodeBitmapGenerator
import com.example.domain.model.QrSecurity
import com.example.domain.model.StatsCalculator
import com.example.domain.model.StatsPeriod
import com.example.domain.model.Student
import java.io.ByteArrayOutputStream
import java.io.File

object PdfExporter {

    private const val PAGE_WIDTH = 595 // A4 @ 72dpi
    private const val PAGE_HEIGHT = 842

    fun generateAttendanceReportPdf(
        className: String,
        schoolYearName: String,
        period: StatsPeriod,
        teacherName: String,
        summary: ClassAttendanceSummary
    ): ByteArray {
        val doc = PdfDocument()
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(13, 76, 94)
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(55, 65, 81)
            textSize = 10.5f
        }
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 9.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val headerBgPaint = Paint().apply {
            color = Color.rgb(13, 76, 94)
        }
        val rowBgAltPaint = Paint().apply {
            color = Color.rgb(242, 247, 246)
        }
        val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(17, 24, 39)
            textSize = 9.5f
        }
        val linePaint = Paint().apply {
            color = Color.rgb(209, 213, 219)
            strokeWidth = 0.8f
        }

        val rowsPerPage = 26
        val students = summary.studentSummaries
        val totalPages = ((students.size.coerceAtLeast(1) - 1) / rowsPerPage) + 1

        for (pageIdx in 0 until totalPages) {
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageIdx + 1).create()
            val page = doc.startPage(pageInfo)
            val canvas = page.canvas

            var y = 44f
            if (pageIdx == 0) {
                canvas.drawText("BÁO CÁO CHUYÊN CẦN - QUẢN LÝ LỚP BẰNG MÃ QR - PP HẬU", 36f, y, titlePaint)
                y += 18f
                canvas.drawText(
                    "Lớp: $className   |   Năm học: $schoolYearName   |   Kỳ: ${period.labelVi}",
                    36f,
                    y,
                    subtitlePaint
                )
                y += 15f
                canvas.drawText(
                    "Giáo viên xuất báo cáo: $teacherName   |   Ngày xuất: ${StatsCalculator.formatDateTimeVi(System.currentTimeMillis())}",
                    36f,
                    y,
                    subtitlePaint
                )
                y += 16f
                canvas.drawText(
                    "Tổng buổi: ${summary.totalSessions}  •  Có mặt: ${summary.presentCount}  •  Đi trễ: ${summary.lateCount}  •  Có phép: ${summary.excusedCount}  •  Không phép: ${summary.unexcusedCount}  •  Chuyên cần: ${"%.1f".format(summary.attendanceRatePercent)}%",
                    36f,
                    y,
                    subtitlePaint
                )
                y += 18f
            } else {
                canvas.drawText("BÁO CÁO CHUYÊN CẦN LỚP $className (Trang ${pageIdx + 1})", 36f, y, titlePaint)
                y += 22f
            }

            // Table header
            canvas.drawRect(36f, y, PAGE_WIDTH - 36f, y + 22f, headerBgPaint)
            val cols = floatArrayOf(42f, 70f, 138f, 295f, 342f, 388f, 440f, 502f)
            val headers = arrayOf("STT", "Mã HS", "Họ và tên", "Có mặt", "Đi trễ", "Có phép", "K.Phép", "Tỉ lệ")
            headers.forEachIndexed { idx, label ->
                canvas.drawText(label, cols[idx], y + 14.5f, headerPaint)
            }
            y += 22f

            val startIdx = pageIdx * rowsPerPage
            val endIdx = minOf(startIdx + rowsPerPage, students.size)
            for (i in startIdx until endIdx) {
                val item = students[i]
                if ((i - startIdx) % 2 == 1) {
                    canvas.drawRect(36f, y, PAGE_WIDTH - 36f, y + 21f, rowBgAltPaint)
                }
                canvas.drawText("${i + 1}", cols[0], y + 14f, cellPaint)
                canvas.drawText(item.student.studentCode.take(11), cols[1], y + 14f, cellPaint)
                canvas.drawText(item.student.fullName.take(26), cols[2], y + 14f, cellPaint)
                canvas.drawText("${item.presentCount}", cols[3], y + 14f, cellPaint)
                canvas.drawText("${item.lateCount}", cols[4], y + 14f, cellPaint)
                canvas.drawText("${item.excusedCount}", cols[5], y + 14f, cellPaint)
                canvas.drawText("${item.unexcusedCount}", cols[6], y + 14f, cellPaint)
                canvas.drawText("${"%.1f".format(item.attendanceRatePercent)}%", cols[7], y + 14f, cellPaint)
                y += 21f
                canvas.drawLine(36f, y, PAGE_WIDTH - 36f, y, linePaint)
            }

            if (pageIdx == totalPages - 1) {
                y += 28f
                if (y < PAGE_HEIGHT - 80) {
                    canvas.drawText("Xác nhận của Giáo viên / Ban Giám hiệu", PAGE_WIDTH - 225f, y, subtitlePaint)
                    canvas.drawText("(Ký và ghi rõ họ tên)", PAGE_WIDTH - 190f, y + 16f, subtitlePaint)
                }
            }

            doc.finishPage(page)
        }

        val baos = ByteArrayOutputStream()
        doc.writeTo(baos)
        doc.close()
        return baos.toByteArray()
    }

    fun generateQrCardsPdf(
        classroom: Classroom,
        students: List<Student>
    ): ByteArray {
        val doc = PdfDocument()
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(13, 76, 94)
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(17, 24, 39)
            textSize = 10.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(75, 85, 99)
            textSize = 9f
        }
        val borderPaint = Paint().apply {
            color = Color.rgb(203, 213, 225)
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }

        val cardsPerPage = 9 // 3 columns x 3 rows
        val totalPages = ((students.size.coerceAtLeast(1) - 1) / cardsPerPage) + 1
        val cardWidth = 166f
        val cardHeight = 225f

        for (pageIdx in 0 until totalPages) {
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageIdx + 1).create()
            val page = doc.startPage(pageInfo)
            val canvas = page.canvas

            canvas.drawText(
                "THẺ ĐIỂM DANH QR - LỚP ${classroom.name} (${classroom.schoolYearName}) - PP HẬU",
                36f,
                38f,
                titlePaint
            )

            val startIdx = pageIdx * cardsPerPage
            val endIdx = minOf(startIdx + cardsPerPage, students.size)
            for (i in startIdx until endIdx) {
                val localIdx = i - startIdx
                val col = localIdx % 3
                val row = localIdx / 3
                val left = 36f + col * (cardWidth + 12f)
                val top = 58f + row * (cardHeight + 12f)

                canvas.drawRect(left, top, left + cardWidth, top + cardHeight, borderPaint)

                val student = students[i]
                val payload = QrSecurity.buildQrPayload(
                    classId = classroom.id,
                    studentId = student.id,
                    qrToken = student.qrToken,
                    qrVersion = student.qrVersion,
                    classQrSecret = classroom.qrSecret
                )
                val qrBitmap = QrCodeBitmapGenerator.generateBitmap(payload, 280)
                val qrSize = 132
                val qrLeft = (left + (cardWidth - qrSize) / 2f).toInt()
                val qrTop = (top + 14f).toInt()
                canvas.drawBitmap(
                    qrBitmap,
                    null,
                    Rect(qrLeft, qrTop, qrLeft + qrSize, qrTop + qrSize),
                    null
                )
                qrBitmap.recycle()

                canvas.drawText(student.fullName.take(22), left + 12f, top + 168f, namePaint)
                canvas.drawText("Mã HS: ${student.studentCode}", left + 12f, top + 184f, subPaint)
                canvas.drawText("Lớp: ${classroom.name}  •  Phiên bản QR #${student.qrVersion}", left + 12f, top + 199f, subPaint)
                canvas.drawText("QUẢN LÝ LỚP BẰNG MÃ QR - PP HẬU", left + 12f, top + 213f, subPaint)
            }
            doc.finishPage(page)
        }

        val baos = ByteArrayOutputStream()
        doc.writeTo(baos)
        doc.close()
        return baos.toByteArray()
    }
}

object ExportFileHelper {
    fun shareBytes(
        context: Context,
        fileName: String,
        mimeType: String,
        bytes: ByteArray
    ) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, fileName)
        file.writeBytes(bytes)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, fileName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Chia sẻ tệp báo cáo"))
    }
}
