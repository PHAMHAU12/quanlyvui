package com.example.domain.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class StudentAttendanceSummary(
    val student: Student,
    val presentCount: Int,
    val lateCount: Int,
    val excusedCount: Int,
    val unexcusedCount: Int
) {
    val totalRecords: Int get() = presentCount + lateCount + excusedCount + unexcusedCount
    val attendedCount: Int get() = presentCount + lateCount
    val attendanceRatePercent: Double
        get() = if (totalRecords == 0) 100.0 else (attendedCount * 100.0) / totalRecords
    val isAtRisk: Boolean
        get() = unexcusedCount >= 3 || (totalRecords >= 4 && attendanceRatePercent < 80.0)
}

data class ClassAttendanceSummary(
    val totalSessions: Int,
    val totalRecords: Int,
    val presentCount: Int,
    val lateCount: Int,
    val excusedCount: Int,
    val unexcusedCount: Int,
    val studentSummaries: List<StudentAttendanceSummary>
) {
    val attendanceRatePercent: Double
        get() = if (totalRecords == 0) 100.0 else ((presentCount + lateCount) * 100.0) / totalRecords
}

object StatsCalculator {
    fun filterRecordsByPeriod(
        records: List<AttendanceRecord>,
        period: StatsPeriod,
        schoolYear: SchoolYear?,
        selectedYearMonth: String // Format: "YYYY-MM"
    ): List<AttendanceRecord> {
        return when (period) {
            StatsPeriod.MONTH -> records.filter { it.sessionDate.startsWith(selectedYearMonth) }
            StatsPeriod.SEMESTER_1 -> {
                if (schoolYear == null) records
                else records.filter {
                    val ms = it.checkedInAtMillis
                    ms in schoolYear.semester1StartMillis..schoolYear.semester1EndMillis
                }
            }
            StatsPeriod.SEMESTER_2 -> {
                if (schoolYear == null) records
                else records.filter {
                    val ms = it.checkedInAtMillis
                    ms in schoolYear.semester2StartMillis..schoolYear.semester2EndMillis
                }
            }
            StatsPeriod.SCHOOL_YEAR -> {
                if (schoolYear == null) records
                else records.filter { it.schoolYearId == schoolYear.id }
            }
        }
    }

    fun filterSessionsByPeriod(
        sessions: List<AttendanceSession>,
        period: StatsPeriod,
        schoolYear: SchoolYear?,
        selectedYearMonth: String
    ): List<AttendanceSession> {
        return when (period) {
            StatsPeriod.MONTH -> sessions.filter { it.sessionDate.startsWith(selectedYearMonth) }
            StatsPeriod.SEMESTER_1 -> {
                if (schoolYear == null) sessions
                else sessions.filter {
                    it.scheduledStartMillis in schoolYear.semester1StartMillis..schoolYear.semester1EndMillis
                }
            }
            StatsPeriod.SEMESTER_2 -> {
                if (schoolYear == null) sessions
                else sessions.filter {
                    it.scheduledStartMillis in schoolYear.semester2StartMillis..schoolYear.semester2EndMillis
                }
            }
            StatsPeriod.SCHOOL_YEAR -> {
                if (schoolYear == null) sessions
                else sessions.filter { it.schoolYearId == schoolYear.id }
            }
        }
    }

    fun computeClassSummary(
        students: List<Student>,
        filteredRecords: List<AttendanceRecord>,
        filteredSessions: List<AttendanceSession>,
        selectedStudentId: String? = null
    ): ClassAttendanceSummary {
        val targetStudents = if (selectedStudentId.isNullOrBlank()) {
            students
        } else {
            students.filter { it.id == selectedStudentId }
        }
        val targetRecords = if (selectedStudentId.isNullOrBlank()) {
            filteredRecords
        } else {
            filteredRecords.filter { it.studentId == selectedStudentId }
        }

        val recordsByStudent = targetRecords.groupBy { it.studentId }
        val studentSummaries = targetStudents.map { student ->
            val list = recordsByStudent[student.id].orEmpty()
            StudentAttendanceSummary(
                student = student,
                presentCount = list.count { it.attendanceStatus == AttendanceStatus.PRESENT },
                lateCount = list.count { it.attendanceStatus == AttendanceStatus.LATE },
                excusedCount = list.count { it.attendanceStatus == AttendanceStatus.ABSENT_EXCUSED },
                unexcusedCount = list.count { it.attendanceStatus == AttendanceStatus.ABSENT_UNEXCUSED }
            )
        }.sortedWith(
            compareByDescending<StudentAttendanceSummary> { it.unexcusedCount }
                .thenBy { it.attendanceRatePercent }
                .thenBy { it.student.fullName }
        )

        return ClassAttendanceSummary(
            totalSessions = filteredSessions.size,
            totalRecords = targetRecords.size,
            presentCount = targetRecords.count { it.attendanceStatus == AttendanceStatus.PRESENT },
            lateCount = targetRecords.count { it.attendanceStatus == AttendanceStatus.LATE },
            excusedCount = targetRecords.count { it.attendanceStatus == AttendanceStatus.ABSENT_EXCUSED },
            unexcusedCount = targetRecords.count { it.attendanceStatus == AttendanceStatus.ABSENT_UNEXCUSED },
            studentSummaries = studentSummaries
        )
    }

    fun currentDateIso(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    fun currentYearMonth(): String =
        SimpleDateFormat("yyyy-MM", Locale.US).format(Date())

    fun formatDateTimeVi(millis: Long): String {
        if (millis <= 0L) return "--:--"
        return SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("vi", "VN")).format(Date(millis))
    }

    fun formatTimeVi(millis: Long): String {
        if (millis <= 0L) return "--:--"
        return SimpleDateFormat("HH:mm:ss", Locale("vi", "VN")).format(Date(millis))
    }

    fun defaultSchoolYearRange(): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH)
        val startYear = if (month >= Calendar.AUGUST) year else year - 1
        val sem1Start = Calendar.getInstance().apply {
            set(startYear, Calendar.SEPTEMBER, 5, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val sem2End = Calendar.getInstance().apply {
            set(startYear + 1, Calendar.MAY, 31, 23, 59, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis
        return sem1Start to sem2End
    }
}
