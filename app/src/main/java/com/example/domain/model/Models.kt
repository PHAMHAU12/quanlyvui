package com.example.domain.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class UserRole(val key: String, val labelVi: String) {
    ADMIN("admin", "Quản trị viên"),
    TEACHER("teacher", "Giáo viên");

    companion object {
        fun fromKey(key: String?): UserRole =
            entries.firstOrNull { it.key == key } ?: TEACHER
    }
}

enum class AttendanceStatus(val key: String, val labelVi: String, val shortVi: String) {
    PRESENT("PRESENT", "Có mặt", "CM"),
    LATE("LATE", "Đi trễ", "ĐT"),
    ABSENT_EXCUSED("ABSENT_EXCUSED", "Vắng có phép", "CP"),
    ABSENT_UNEXCUSED("ABSENT_UNEXCUSED", "Vắng không phép", "KP");

    companion object {
        fun fromKey(key: String?): AttendanceStatus =
            entries.firstOrNull { it.key == key } ?: ABSENT_UNEXCUSED
    }
}

enum class SessionStatus(val key: String, val labelVi: String) {
    OPEN("OPEN", "Đang mở"),
    CLOSED("CLOSED", "Đã chốt");

    companion object {
        fun fromKey(key: String?): SessionStatus =
            entries.firstOrNull { it.key == key } ?: OPEN
    }
}

enum class ScanMethod(val key: String, val labelVi: String) {
    QR_SCAN("QR_SCAN", "Quét QR"),
    MANUAL("MANUAL", "Thủ công");

    companion object {
        fun fromKey(key: String?): ScanMethod =
            entries.firstOrNull { it.key == key } ?: MANUAL
    }
}

enum class StatsPeriod(val labelVi: String) {
    MONTH("Theo tháng"),
    SEMESTER_1("Học kỳ I"),
    SEMESTER_2("Học kỳ II"),
    SCHOOL_YEAR("Cả năm học")
}

@IgnoreExtraProperties
data class AdminEntry(
    @DocumentId val id: String = "",
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val grantedByUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class TeacherProfile(
    @DocumentId val id: String = "",
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val subject: String = "",
    val phone: String = "",
    val role: String = UserRole.TEACHER.key,
    val active: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val userRole: UserRole get() = UserRole.fromKey(role)
}

@IgnoreExtraProperties
data class TeacherInvite(
    @DocumentId val id: String = "",
    val email: String = "",
    val fullName: String = "",
    val subject: String = "",
    val phone: String = "",
    val role: String = UserRole.TEACHER.key,
    val active: Boolean = true,
    val invitedByUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class SchoolYear(
    @DocumentId val id: String = "",
    val name: String = "",
    val isCurrent: Boolean = false,
    val isSchoolWide: Boolean = false,
    val ownerUid: String = "",
    val semester1StartMillis: Long = 0L,
    val semester1EndMillis: Long = 0L,
    val semester2StartMillis: Long = 0L,
    val semester2EndMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Classroom(
    @DocumentId val id: String = "",
    val name: String = "",
    val grade: Long = 10L,
    val schoolYearId: String = "",
    val schoolYearName: String = "",
    val homeroomTeacherUid: String = "",
    val homeroomTeacherName: String = "",
    val ownerUid: String = "",
    val teacherUids: List<String> = emptyList(),
    val qrSecret: String = "",
    val studentCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Student(
    @DocumentId val id: String = "",
    val studentCode: String = "",
    val fullName: String = "",
    val gender: String = "Nam",
    val dateOfBirth: String = "",
    val parentPhone: String = "",
    val classId: String = "",
    val className: String = "",
    val schoolYearId: String = "",
    val qrToken: String = "",
    val qrVersion: Long = 1L,
    val active: Boolean = true,
    val ownerUid: String = "",
    val teacherUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AttendanceSession(
    @DocumentId val id: String = "",
    val classId: String = "",
    val className: String = "",
    val schoolYearId: String = "",
    val subject: String = "",
    val periodLabel: String = "",
    val sessionDate: String = "",
    val scheduledStartMillis: Long = 0L,
    val lateAfterMinutes: Long = 10L,
    val status: String = SessionStatus.OPEN.key,
    val createdByUid: String = "",
    val createdByName: String = "",
    val teacherUids: List<String> = emptyList(),
    val presentCount: Long = 0L,
    val lateCount: Long = 0L,
    val excusedCount: Long = 0L,
    val unexcusedCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val sessionStatus: SessionStatus get() = SessionStatus.fromKey(status)
}

@IgnoreExtraProperties
data class AttendanceRecord(
    @DocumentId val id: String = "",
    val sessionId: String = "",
    val classId: String = "",
    val className: String = "",
    val schoolYearId: String = "",
    val sessionDate: String = "",
    val subject: String = "",
    val studentId: String = "",
    val studentCode: String = "",
    val studentName: String = "",
    val status: String = AttendanceStatus.PRESENT.key,
    val scanMethod: String = ScanMethod.QR_SCAN.key,
    val checkedInAtMillis: Long = 0L,
    val note: String = "",
    val recordedByUid: String = "",
    val recordedByName: String = "",
    val updatedByUid: String = "",
    val updatedByName: String = "",
    val teacherUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val attendanceStatus: AttendanceStatus get() = AttendanceStatus.fromKey(status)
    val method: ScanMethod get() = ScanMethod.fromKey(scanMethod)
}

@IgnoreExtraProperties
data class AttendanceAuditLog(
    @DocumentId val id: String = "",
    val recordId: String = "",
    val sessionId: String = "",
    val classId: String = "",
    val className: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val previousStatus: String = AttendanceStatus.ABSENT_UNEXCUSED.key,
    val newStatus: String = AttendanceStatus.ABSENT_EXCUSED.key,
    val reason: String = "",
    val editorUid: String = "",
    val editorName: String = "",
    val teacherUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    val previous: AttendanceStatus get() = AttendanceStatus.fromKey(previousStatus)
    val updated: AttendanceStatus get() = AttendanceStatus.fromKey(newStatus)
}
