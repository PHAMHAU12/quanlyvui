package com.example.data.repository

import android.util.Log
import com.example.domain.model.ClassAttendanceSummary
import com.example.domain.model.StatsPeriod
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout

private const val AI_TAG = "AiInsightsRepo"

class AiInsightsRepository {
    private val model by lazy {
        Firebase.ai.generativeModel("gemini-3.8-flash")
    }

    fun streamAttendanceAnalysis(
        className: String,
        schoolYearName: String,
        period: StatsPeriod,
        summary: ClassAttendanceSummary
    ): Flow<String> = flow {
        val atRiskStudents = summary.studentSummaries
            .filter { it.isAtRisk || it.lateCount >= 2 || it.unexcusedCount >= 1 }
            .take(12)
            .joinToString("\n") { s ->
                "- ${s.student.fullName} (${s.student.studentCode}): Có mặt ${s.presentCount}, Đi trễ ${s.lateCount}, Có phép ${s.excusedCount}, Không phép ${s.unexcusedCount}, Tỉ lệ ${"%.1f".format(s.attendanceRatePercent)}%"
            }
            .ifBlank { "Không có học sinh vi phạm chuyên cần đáng chú ý." }

        val prompt = """
            Bạn là trợ lý phân tích chuyên cần giáo dục dành cho giáo viên THPT tại Việt Nam.
            Hãy phân tích dữ liệu điểm danh thực tế sau đây bằng tiếng Việt ngắn gọn, súc tích, thực tế (tối đa 250 từ):

            - Lớp: $className | Năm học: $schoolYearName | Kỳ thống kê: ${period.labelVi}
            - Tổng số buổi học: ${summary.totalSessions}
            - Tổng lượt ghi nhận: ${summary.totalRecords}
            - Có mặt: ${summary.presentCount} | Đi trễ: ${summary.lateCount} | Vắng có phép: ${summary.excusedCount} | Vắng không phép: ${summary.unexcusedCount}
            - Tỉ lệ chuyên cần chung: ${"%.1f".format(summary.attendanceRatePercent)}%

            Học sinh cần lưu ý:
            $atRiskStudents

            Hãy trình bày rõ 3 mục:
            1. Đánh giá tổng quan tình hình chuyên cần của lớp.
            2. Cảnh báo học sinh có nguy cơ vi phạm hạnh kiểm/chuyên cần (nếu có).
            3. Đề xuất hành động cụ thể cho giáo viên chủ nhiệm (kèm mẫu tin nhắn ngắn gửi phụ huynh nếu cần).
        """.trimIndent()

        val buffer = StringBuilder()
        try {
            withTimeout(60_000L) {
                model.generateContentStream(prompt).collect { chunk ->
                    val chunkText = chunk.text.orEmpty()
                    if (chunkText.isNotEmpty()) {
                        buffer.append(chunkText)
                        emit(buffer.toString())
                    }
                }
            }
            if (buffer.isBlank()) {
                throw IllegalStateException("Mô hình AI không trả về nội dung phân tích.")
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(AI_TAG, "AI analysis timed out", e)
            throw IllegalStateException("Phân tích AI quá thời gian chờ (60 giây). Vui lòng thử lại.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(AI_TAG, "AI analysis failed", e)
            throw e
        }
    }
}
