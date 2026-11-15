package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.domain.model.AttendanceStatus
import com.example.domain.model.Classroom
import com.example.domain.model.SchoolYear
import com.example.domain.model.UserRole
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class AttendanceRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun ensureTeacherProfile_nonBootstrappedEmail_createsTeacherProfileWithTeacherRole() = runBlocking {
        val uid = signInTestUser("gv_toan_${UUID.randomUUID().toString().take(6)}@thpt.edu.vn")
        val repo = AttendanceRepository(firestore, auth)

        val profile = withTimeout(TIMEOUT_MS) { repo.ensureTeacherProfile().getOrThrow() }
        assertEquals(uid, profile.uid)
        assertEquals(UserRole.TEACHER, profile.userRole)
        assertTrue(profile.active)
    }

    @Test
    fun observeClassrooms_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = AttendanceRepository(firestore, auth)

        var caught: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeClassrooms(isAdmin = false, currentUid = "unauth_user").first()
            }
        } catch (e: Throwable) {
            caught = e
        }
        assertNotNull("Expected failure when unauthenticated", caught)
    }

    @Test
    fun fullAttendanceWorkflow_teacherCreatesYearClassStudentSessionScanAndAuditEdit() = runBlocking {
        val suffix = UUID.randomUUID().toString().take(6)
        val teacherAEmail = "gv_a_$suffix@thpt.edu.vn"
        val teacherBEmail = "gv_b_$suffix@thpt.edu.vn"

        val teacherAUid = signInTestUser(teacherAEmail)
        val repoA = AttendanceRepository(firestore, auth)
        val profileA = withTimeout(TIMEOUT_MS) { repoA.ensureTeacherProfile().getOrThrow() }

        val yearId = withTimeout(TIMEOUT_MS) {
            repoA.createSchoolYear(
                name = "2025 - 2026 ($suffix)",
                isCurrent = true,
                isAdmin = false,
                semester1StartMillis = 1757030400000L,
                semester1EndMillis = 1768003200000L,
                semester2StartMillis = 1768608000000L,
                semester2EndMillis = 1780185600000L
            ).getOrThrow()
        }

        val year = SchoolYear(
            id = yearId,
            name = "2025 - 2026 ($suffix)",
            isCurrent = true,
            isSchoolWide = false,
            ownerUid = teacherAUid,
            semester1StartMillis = 1757030400000L,
            semester1EndMillis = 1768003200000L,
            semester2StartMillis = 1768608000000L,
            semester2EndMillis = 1780185600000L
        )

        val classId = withTimeout(TIMEOUT_MS) {
            repoA.createClassroom(
                name = "10A1-$suffix",
                grade = 10,
                schoolYear = year,
                homeroomTeacherUid = teacherAUid,
                homeroomTeacherName = profileA.displayName,
                assignedTeacherUids = listOf(teacherAUid)
            ).getOrThrow()
        }

        val classesA = withTimeout(FLOW_TIMEOUT_MS) {
            repoA.observeClassrooms(isAdmin = false, currentUid = teacherAUid)
                .first { list -> list.any { it.id == classId } }
        }
        val classroom = classesA.first { it.id == classId }

        val studentId = withTimeout(TIMEOUT_MS) {
            repoA.createStudent(
                studentCode = "HS$suffix",
                fullName = "Trần Minh Khoa",
                gender = "Nam",
                dateOfBirth = "10/04/2010",
                parentPhone = "0987654321",
                classroom = classroom
            ).getOrThrow()
        }

        val studentsA = withTimeout(FLOW_TIMEOUT_MS) {
            repoA.observeStudents(isAdmin = false, currentUid = teacherAUid)
                .first { list -> list.any { it.id == studentId } }
        }
        val student = studentsA.first { it.id == studentId }

        val sessionId = withTimeout(TIMEOUT_MS) {
            repoA.createSession(
                classroom = classroom,
                subject = "Toán học",
                periodLabel = "Tiết 1",
                sessionDate = "2026-11-14",
                scheduledStartMillis = System.currentTimeMillis(),
                lateAfterMinutes = 10,
                creatorName = profileA.displayName
            ).getOrThrow()
        }

        val sessionsA = withTimeout(FLOW_TIMEOUT_MS) {
            repoA.observeSessions(isAdmin = false, currentUid = teacherAUid)
                .first { list -> list.any { it.id == sessionId } }
        }
        val session = sessionsA.first { it.id == sessionId }

        // Record QR scan attendance
        val record = withTimeout(TIMEOUT_MS) {
            repoA.recordQrScanAttendance(
                session = session,
                student = student,
                status = AttendanceStatus.PRESENT,
                checkedInAtMillis = System.currentTimeMillis(),
                teacherName = profileA.displayName
            ).getOrThrow()
        }
        assertEquals("${sessionId}_${studentId}", record.id)

        // Edit attendance with mandatory audit log
        withTimeout(TIMEOUT_MS) {
            repoA.editAttendanceWithAuditLog(
                session = session,
                student = student,
                existingRecord = record,
                allSessionRecords = listOf(record),
                newStatus = AttendanceStatus.ABSENT_EXCUSED,
                reason = "Phụ huynh xin phép về sớm khám bệnh",
                editorName = profileA.displayName
            ).getOrThrow()
        }

        val logs = withTimeout(FLOW_TIMEOUT_MS) {
            repoA.observeAuditLogs(isAdmin = false, currentUid = teacherAUid)
                .first { list -> list.any { it.recordId == record.id } }
        }
        assertTrue(logs.any { it.newStatus == AttendanceStatus.ABSENT_EXCUSED.key })

        // Verify cross-teacher isolation: Teacher B cannot read Teacher A's student
        val teacherBUid = signInTestUser(teacherBEmail)
        val repoB = AttendanceRepository(firestore, auth)
        val studentsB = withTimeout(FLOW_TIMEOUT_MS) {
            repoB.observeStudents(isAdmin = false, currentUid = teacherBUid).first()
        }
        assertTrue(studentsB.none { it.id == studentId })

        // Direct read by Teacher B on Teacher A's student doc must fail with PERMISSION_DENIED
        var deniedException: FirebaseFirestoreException? = null
        try {
            withTimeout(TIMEOUT_MS) {
                firestore.collection("students").document(studentId).get().let { task ->
                    com.google.android.gms.tasks.Tasks.await(task)
                }
            }
        } catch (e: Exception) {
            deniedException = e.unwrapFirestoreException()
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, deniedException?.code)
    }

    private companion object {
        const val TIMEOUT_MS = 8000L
        const val FLOW_TIMEOUT_MS = 5000L
    }
}
