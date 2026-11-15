package com.example.data.repository

import android.content.Context
import com.example.domain.model.AttendanceAuditLog
import com.example.domain.model.AttendanceRecord
import com.example.domain.model.AttendanceSession
import com.example.domain.model.AttendanceStatus
import com.example.domain.model.Classroom
import com.example.domain.model.QrSecurity
import com.example.domain.model.ScanMethod
import com.example.domain.model.SchoolYear
import com.example.domain.model.SessionStatus
import com.example.domain.model.StatsCalculator
import com.example.domain.model.Student
import com.example.domain.model.TeacherInvite
import com.example.domain.model.TeacherProfile
import com.example.domain.model.UserRole
import com.example.firebase.FirestoreClientProvider
import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

private const val BOOTSTRAPPED_ADMIN_EMAIL = "haupham101981@gmail.com"

class AttendanceRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        db = FirestoreClientProvider.getDatabase(context),
        auth = Firebase.auth
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("Giáo viên cần đăng nhập Google trước khi truy cập dữ liệu.")

    private fun QuerySnapshot.toObjectsEstimated(clazz: Class<*>): List<Any?> =
        documents.map { it.toObject(clazz, ServerTimestampBehavior.ESTIMATE) }

    private inline fun <reified T> safeSnapshotFlow(
        path: String,
        crossinline queryProvider: () -> com.google.firebase.firestore.Query
    ): Flow<List<T>> = flow {
        val uid = requireUserId()
        queryProvider()
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    doc.toObject(T::class.java, ServerTimestampBehavior.ESTIMATE)
                }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val exceptionToLog = firestoreError ?: (error as? Exception)
                if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                    handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                }
                throw error
            }
            .collect { emit(it) }
    }

    // --- AUTH & TEACHER PROFILE BOOTSTRAP ---

    suspend fun ensureTeacherProfile(): Result<TeacherProfile> = runCatching {
        val user = auth.currentUser
            ?: throw IllegalStateException("Chưa đăng nhập tài khoản Google.")
        val uid = user.uid
        val email = (user.email ?: "").trim().lowercase()
        val displayName = (user.displayName ?: email.substringBefore("@").ifBlank { "Giáo viên" }).take(80)

        val adminDocRef = db.collection("admins").document(uid)
        val teacherDocRef = db.collection("teachers").document(uid)

        val isBootstrapped = email == BOOTSTRAPPED_ADMIN_EMAIL && user.isEmailVerified
        val adminSnap = try {
            adminDocRef.get().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, adminDocRef.path)
            null
        }
        val isExplicitAdmin = adminSnap?.exists() == true || isBootstrapped

        if (isBootstrapped && adminSnap?.exists() != true) {
            val adminPayload = mapOf(
                "uid" to uid,
                "email" to email,
                "displayName" to displayName,
                "grantedByUid" to uid,
                "createdAt" to FieldValue.serverTimestamp()
            )
            try {
                adminDocRef.set(adminPayload).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.CREATE, adminDocRef.path)
            }
        }

        val existingTeacher = try {
            teacherDocRef.get().await().toObject(TeacherProfile::class.java, ServerTimestampBehavior.ESTIMATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, teacherDocRef.path)
            null
        }

        if (existingTeacher != null) {
            if (isExplicitAdmin && existingTeacher.role != UserRole.ADMIN.key) {
                teacherDocRef.update(
                    mapOf(
                        "role" to UserRole.ADMIN.key,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                return@runCatching existingTeacher.copy(role = UserRole.ADMIN.key)
            }
            return@runCatching existingTeacher
        }

        // Check pre-registered invite if available
        val invite = if (email.isNotBlank()) {
            try {
                db.collection("teacher_invites")
                    .whereEqualTo("email", email)
                    .limit(1)
                    .get()
                    .await()
                    .documents
                    .firstOrNull()
                    ?.toObject(TeacherInvite::class.java, ServerTimestampBehavior.ESTIMATE)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.LIST, "teacher_invites")
                null
            }
        } else null

        val assignedRole = if (isExplicitAdmin) UserRole.ADMIN.key else UserRole.TEACHER.key
        val resolvedName = invite?.fullName?.takeIf { it.isNotBlank() } ?: displayName
        val resolvedSubject = invite?.subject?.takeIf { it.isNotBlank() }
            ?: if (isExplicitAdmin) "Quản trị hệ thống" else "Chưa phân môn"
        val resolvedPhone = invite?.phone ?: ""

        val payload = mapOf(
            "uid" to uid,
            "displayName" to resolvedName,
            "email" to (email.ifBlank { "$uid@google.user" }),
            "subject" to resolvedSubject,
            "phone" to resolvedPhone,
            "role" to assignedRole,
            "active" to true,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            teacherDocRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, teacherDocRef.path)
            throw e
        }

        TeacherProfile(
            id = uid,
            uid = uid,
            displayName = resolvedName,
            email = email,
            subject = resolvedSubject,
            phone = resolvedPhone,
            role = assignedRole,
            active = true
        )
    }

    fun observeOwnTeacherProfile(uid: String): Flow<TeacherProfile?> = flow {
        requireUserId()
        val docRef = db.collection("teachers").document(uid)
        docRef.snapshots()
            .map { it.toObject(TeacherProfile::class.java, ServerTimestampBehavior.ESTIMATE) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                if (firestoreError != null && auth.currentUser?.uid == uid) {
                    handleFirestoreError(firestoreError, OperationType.GET, docRef.path)
                }
                throw error
            }
            .collect { emit(it) }
    }

    fun observeAllTeachers(isAdmin: Boolean): Flow<List<TeacherProfile>> =
        if (!isAdmin) {
            flow { emit(emptyList()) }
        } else {
            safeSnapshotFlow("teachers") { db.collection("teachers") }
        }

    fun observeTeacherInvites(isAdmin: Boolean): Flow<List<TeacherInvite>> =
        if (!isAdmin) {
            flow { emit(emptyList()) }
        } else {
            safeSnapshotFlow("teacher_invites") { db.collection("teacher_invites") }
        }

    suspend fun updateOwnProfile(displayName: String, subject: String, phone: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("teachers").document(uid)
        try {
            docRef.update(
                mapOf(
                    "displayName" to displayName.trim().take(80),
                    "subject" to subject.trim().take(80),
                    "phone" to phone.trim().take(25),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun createTeacherInvite(
        email: String,
        fullName: String,
        subject: String,
        phone: String,
        role: UserRole
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("teacher_invites").document()
        val payload = mapOf(
            "email" to email.trim().lowercase().take(120),
            "fullName" to fullName.trim().take(80),
            "subject" to subject.trim().take(80),
            "phone" to phone.trim().take(25),
            "role" to role.key,
            "active" to true,
            "invitedByUid" to uid,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteTeacherInvite(inviteId: String): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("teacher_invites").document(inviteId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    suspend fun setTeacherRole(targetTeacher: TeacherProfile, newRole: UserRole): Result<Unit> = runCatching {
        val adminUid = requireUserId()
        val batch = db.batch()
        val teacherRef = db.collection("teachers").document(targetTeacher.uid)
        val adminRef = db.collection("admins").document(targetTeacher.uid)

        batch.update(
            teacherRef,
            mapOf(
                "role" to newRole.key,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        if (newRole == UserRole.ADMIN) {
            batch.set(
                adminRef,
                mapOf(
                    "uid" to targetTeacher.uid,
                    "email" to targetTeacher.email,
                    "displayName" to targetTeacher.displayName,
                    "grantedByUid" to adminUid,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
        } else if (targetTeacher.uid != adminUid) {
            batch.delete(adminRef)
        }
        try {
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, teacherRef.path)
            throw e
        }
    }

    suspend fun setTeacherActive(targetUid: String, active: Boolean): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("teachers").document(targetUid)
        try {
            docRef.update(
                mapOf(
                    "active" to active,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    // --- SCHOOL YEARS ---

    fun observeSchoolYears(isAdmin: Boolean, currentUid: String): Flow<List<SchoolYear>> {
        return if (isAdmin) {
            safeSnapshotFlow<SchoolYear>("school_years") {
                db.collection("school_years")
            }.map { list -> list.sortedByDescending { it.isCurrent } }
        } else {
            val schoolWideFlow = safeSnapshotFlow<SchoolYear>("school_years") {
                db.collection("school_years").whereEqualTo("isSchoolWide", true)
            }
            val ownFlow = safeSnapshotFlow<SchoolYear>("school_years") {
                db.collection("school_years").whereEqualTo("ownerUid", currentUid)
            }
            combine(schoolWideFlow, ownFlow) { wide, own ->
                (wide + own)
                    .distinctBy { it.id }
                    .sortedByDescending { it.isCurrent }
            }
        }
    }

    suspend fun createSchoolYear(
        name: String,
        isCurrent: Boolean,
        isAdmin: Boolean,
        semester1StartMillis: Long,
        semester1EndMillis: Long,
        semester2StartMillis: Long,
        semester2EndMillis: Long
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("school_years").document()
        val payload = mapOf(
            "name" to name.trim().take(40),
            "isCurrent" to isCurrent,
            "isSchoolWide" to isAdmin,
            "ownerUid" to uid,
            "semester1StartMillis" to semester1StartMillis,
            "semester1EndMillis" to semester1EndMillis,
            "semester2StartMillis" to semester2StartMillis,
            "semester2EndMillis" to semester2EndMillis,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteSchoolYear(yearId: String): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("school_years").document(yearId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- CLASSROOMS ---

    fun observeClassrooms(isAdmin: Boolean, currentUid: String): Flow<List<Classroom>> =
        safeSnapshotFlow<Classroom>("classes") {
            if (isAdmin) {
                db.collection("classes")
            } else {
                db.collection("classes").whereArrayContains("teacherUids", currentUid)
            }
        }.map { list -> list.sortedWith(compareBy({ it.grade }, { it.name })) }

    suspend fun createClassroom(
        name: String,
        grade: Int,
        schoolYear: SchoolYear,
        homeroomTeacherUid: String,
        homeroomTeacherName: String,
        assignedTeacherUids: List<String>
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("classes").document()
        val finalTeacherUids = (assignedTeacherUids + uid).distinct().take(20)
        val payload = mapOf(
            "name" to name.trim().take(40),
            "grade" to grade.coerceIn(10, 12),
            "schoolYearId" to schoolYear.id,
            "schoolYearName" to schoolYear.name,
            "homeroomTeacherUid" to homeroomTeacherUid.ifBlank { uid },
            "homeroomTeacherName" to homeroomTeacherName.trim().take(80),
            "ownerUid" to uid,
            "teacherUids" to finalTeacherUids,
            "qrSecret" to QrSecurity.generateRandomSecretHex(),
            "studentCount" to 0L,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun updateClassroomTeachers(
        classroom: Classroom,
        newTeacherUids: List<String>,
        homeroomUid: String,
        homeroomName: String
    ): Result<Unit> = runCatching {
        requireUserId()
        val cleanUids = (newTeacherUids + classroom.ownerUid).distinct().take(20)
        val batch = db.batch()
        val classRef = db.collection("classes").document(classroom.id)
        batch.update(
            classRef,
            mapOf(
                "teacherUids" to cleanUids,
                "homeroomTeacherUid" to homeroomUid,
                "homeroomTeacherName" to homeroomName.take(80),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )

        // Sync denormalized teacherUids onto students and sessions of this class
        val students = db.collection("students").whereEqualTo("classId", classroom.id).get().await()
        students.documents.forEach { doc ->
            batch.update(doc.reference, mapOf("teacherUids" to cleanUids, "updatedAt" to FieldValue.serverTimestamp()))
        }
        val sessions = db.collection("sessions").whereEqualTo("classId", classroom.id).get().await()
        sessions.documents.forEach { doc ->
            batch.update(doc.reference, mapOf("teacherUids" to cleanUids, "updatedAt" to FieldValue.serverTimestamp()))
        }

        try {
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, classRef.path)
            throw e
        }
    }

    suspend fun deleteClassroom(classId: String): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("classes").document(classId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- STUDENTS ---

    fun observeStudents(isAdmin: Boolean, currentUid: String): Flow<List<Student>> =
        safeSnapshotFlow<Student>("students") {
            if (isAdmin) {
                db.collection("students")
            } else {
                db.collection("students").whereArrayContains("teacherUids", currentUid)
            }
        }.map { list -> list.sortedBy { it.fullName } }

    suspend fun createStudent(
        studentCode: String,
        fullName: String,
        gender: String,
        dateOfBirth: String,
        parentPhone: String,
        classroom: Classroom
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("students").document()
        val classRef = db.collection("classes").document(classroom.id)
        val batch = db.batch()
        val payload = mapOf(
            "studentCode" to studentCode.trim().take(30),
            "fullName" to fullName.trim().take(80),
            "gender" to gender,
            "dateOfBirth" to dateOfBirth.trim().take(20),
            "parentPhone" to parentPhone.trim().take(25),
            "classId" to classroom.id,
            "className" to classroom.name,
            "schoolYearId" to classroom.schoolYearId,
            "qrToken" to QrSecurity.generateRandomQrToken(),
            "qrVersion" to 1L,
            "active" to true,
            "ownerUid" to uid,
            "teacherUids" to classroom.teacherUids.ifEmpty { listOf(uid) },
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        batch.set(docRef, payload)
        batch.update(
            classRef,
            mapOf(
                "studentCount" to FieldValue.increment(1L),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        try {
            batch.commit().await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun updateStudent(
        student: Student,
        studentCode: String,
        fullName: String,
        gender: String,
        dateOfBirth: String,
        parentPhone: String
    ): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("students").document(student.id)
        try {
            docRef.update(
                mapOf(
                    "studentCode" to studentCode.trim().take(30),
                    "fullName" to fullName.trim().take(80),
                    "gender" to gender,
                    "dateOfBirth" to dateOfBirth.trim().take(20),
                    "parentPhone" to parentPhone.trim().take(25),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun regenerateStudentQrToken(student: Student): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("students").document(student.id)
        try {
            docRef.update(
                mapOf(
                    "qrToken" to QrSecurity.generateRandomQrToken(),
                    "qrVersion" to (student.qrVersion + 1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteStudent(student: Student): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("students").document(student.id)
        val classRef = db.collection("classes").document(student.classId)
        val batch = db.batch()
        batch.delete(docRef)
        batch.update(
            classRef,
            mapOf(
                "studentCount" to FieldValue.increment(-1L),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        try {
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- SESSIONS ---

    fun observeSessions(isAdmin: Boolean, currentUid: String): Flow<List<AttendanceSession>> =
        safeSnapshotFlow<AttendanceSession>("sessions") {
            if (isAdmin) {
                db.collection("sessions")
            } else {
                db.collection("sessions").whereArrayContains("teacherUids", currentUid)
            }
        }.map { list -> list.sortedByDescending { it.scheduledStartMillis } }

    suspend fun createSession(
        classroom: Classroom,
        subject: String,
        periodLabel: String,
        sessionDate: String,
        scheduledStartMillis: Long,
        lateAfterMinutes: Int,
        creatorName: String
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("sessions").document()
        val payload = mapOf(
            "classId" to classroom.id,
            "className" to classroom.name,
            "schoolYearId" to classroom.schoolYearId,
            "subject" to subject.trim().take(60),
            "periodLabel" to periodLabel.trim().take(30),
            "sessionDate" to sessionDate,
            "scheduledStartMillis" to scheduledStartMillis,
            "lateAfterMinutes" to lateAfterMinutes.coerceIn(0, 120),
            "status" to SessionStatus.OPEN.key,
            "createdByUid" to uid,
            "createdByName" to creatorName.trim().ifBlank { "Giáo viên" }.take(80),
            "teacherUids" to classroom.teacherUids.ifEmpty { listOf(uid) },
            "presentCount" to 0L,
            "lateCount" to 0L,
            "excusedCount" to 0L,
            "unexcusedCount" to 0L,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun closeSessionAndMarkAbsent(
        session: AttendanceSession,
        classStudents: List<Student>,
        existingRecords: List<AttendanceRecord>,
        teacherName: String
    ): Result<Int> = runCatching {
        val uid = requireUserId()
        val recordedStudentIds = existingRecords
            .filter { it.sessionId == session.id }
            .map { it.studentId }
            .toSet()

        val unscanned = classStudents.filter { it.active && it.id !in recordedStudentIds }
        val nowMillis = System.currentTimeMillis()
        val batch = db.batch()
        val safeTeacherName = teacherName.ifBlank { "Giáo viên" }.take(80)

        unscanned.forEach { student ->
            val recordId = "${session.id}_${student.id}"
            val recordRef = db.collection("attendance_records").document(recordId)
            val payload = mapOf(
                "sessionId" to session.id,
                "classId" to session.classId,
                "className" to session.className,
                "schoolYearId" to session.schoolYearId,
                "sessionDate" to session.sessionDate,
                "subject" to session.subject,
                "studentId" to student.id,
                "studentCode" to student.studentCode,
                "studentName" to student.fullName,
                "status" to AttendanceStatus.ABSENT_UNEXCUSED.key,
                "scanMethod" to ScanMethod.MANUAL.key,
                "checkedInAtMillis" to nowMillis,
                "note" to "Tự động đánh dấu vắng khi chốt buổi học",
                "recordedByUid" to uid,
                "recordedByName" to safeTeacherName,
                "updatedByUid" to uid,
                "updatedByName" to safeTeacherName,
                "teacherUids" to session.teacherUids.ifEmpty { listOf(uid) },
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            batch.set(recordRef, payload)
        }

        val allSessionRecords = existingRecords.filter { it.sessionId == session.id }
        val present = allSessionRecords.count { it.attendanceStatus == AttendanceStatus.PRESENT }
        val late = allSessionRecords.count { it.attendanceStatus == AttendanceStatus.LATE }
        val excused = allSessionRecords.count { it.attendanceStatus == AttendanceStatus.ABSENT_EXCUSED }
        val unexcused = allSessionRecords.count { it.attendanceStatus == AttendanceStatus.ABSENT_UNEXCUSED } + unscanned.size

        val sessionRef = db.collection("sessions").document(session.id)
        batch.update(
            sessionRef,
            mapOf(
                "status" to SessionStatus.CLOSED.key,
                "presentCount" to present.toLong(),
                "lateCount" to late.toLong(),
                "excusedCount" to excused.toLong(),
                "unexcusedCount" to unexcused.toLong(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )

        try {
            batch.commit().await()
            unscanned.size
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, sessionRef.path)
            throw e
        }
    }

    suspend fun reopenSession(sessionId: String): Result<Unit> = runCatching {
        requireUserId()
        val sessionRef = db.collection("sessions").document(sessionId)
        try {
            sessionRef.update(
                mapOf(
                    "status" to SessionStatus.OPEN.key,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, sessionRef.path)
            throw e
        }
    }

    // --- ATTENDANCE RECORDS & AUDIT LOGS ---

    fun observeAttendanceRecords(isAdmin: Boolean, currentUid: String): Flow<List<AttendanceRecord>> =
        safeSnapshotFlow<AttendanceRecord>("attendance_records") {
            if (isAdmin) {
                db.collection("attendance_records")
            } else {
                db.collection("attendance_records").whereArrayContains("teacherUids", currentUid)
            }
        }.map { list -> list.sortedByDescending { it.checkedInAtMillis } }

    fun observeAuditLogs(isAdmin: Boolean, currentUid: String): Flow<List<AttendanceAuditLog>> =
        safeSnapshotFlow<AttendanceAuditLog>("audit_logs") {
            if (isAdmin) {
                db.collection("audit_logs")
            } else {
                db.collection("audit_logs").whereArrayContains("teacherUids", currentUid)
            }
        }.map { list ->
            list.sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
        }

    suspend fun recordQrScanAttendance(
        session: AttendanceSession,
        student: Student,
        status: AttendanceStatus,
        checkedInAtMillis: Long,
        teacherName: String
    ): Result<AttendanceRecord> = runCatching {
        val uid = requireUserId()
        val recordId = "${session.id}_${student.id}"
        val recordRef = db.collection("attendance_records").document(recordId)
        val sessionRef = db.collection("sessions").document(session.id)
        val safeTeacherName = teacherName.ifBlank { "Giáo viên" }.take(80)

        val payload = mapOf(
            "sessionId" to session.id,
            "classId" to session.classId,
            "className" to session.className,
            "schoolYearId" to session.schoolYearId,
            "sessionDate" to session.sessionDate,
            "subject" to session.subject,
            "studentId" to student.id,
            "studentCode" to student.studentCode,
            "studentName" to student.fullName,
            "status" to status.key,
            "scanMethod" to ScanMethod.QR_SCAN.key,
            "checkedInAtMillis" to checkedInAtMillis,
            "note" to "",
            "recordedByUid" to uid,
            "recordedByName" to safeTeacherName,
            "updatedByUid" to uid,
            "updatedByName" to safeTeacherName,
            "teacherUids" to session.teacherUids.ifEmpty { listOf(uid) },
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        val batch = db.batch()
        batch.set(recordRef, payload)
        val counterField = when (status) {
            AttendanceStatus.PRESENT -> "presentCount"
            AttendanceStatus.LATE -> "lateCount"
            AttendanceStatus.ABSENT_EXCUSED -> "excusedCount"
            AttendanceStatus.ABSENT_UNEXCUSED -> "unexcusedCount"
        }
        batch.update(
            sessionRef,
            mapOf(
                counterField to FieldValue.increment(1L),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        try {
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, recordRef.path)
            throw e
        }

        AttendanceRecord(
            id = recordId,
            sessionId = session.id,
            classId = session.classId,
            className = session.className,
            schoolYearId = session.schoolYearId,
            sessionDate = session.sessionDate,
            subject = session.subject,
            studentId = student.id,
            studentCode = student.studentCode,
            studentName = student.fullName,
            status = status.key,
            scanMethod = ScanMethod.QR_SCAN.key,
            checkedInAtMillis = checkedInAtMillis,
            note = "",
            recordedByUid = uid,
            recordedByName = safeTeacherName,
            updatedByUid = uid,
            updatedByName = safeTeacherName,
            teacherUids = session.teacherUids
        )
    }

    suspend fun editAttendanceWithAuditLog(
        session: AttendanceSession,
        student: Student,
        existingRecord: AttendanceRecord?,
        allSessionRecords: List<AttendanceRecord>,
        newStatus: AttendanceStatus,
        reason: String,
        editorName: String
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val cleanReason = reason.trim().take(300)
        require(cleanReason.length >= 3) { "Vui lòng nhập lý do chỉnh sửa ít nhất 3 ký tự." }

        val recordId = "${session.id}_${student.id}"
        val recordRef = db.collection("attendance_records").document(recordId)
        val logRef = db.collection("audit_logs").document()
        val sessionRef = db.collection("sessions").document(session.id)
        val safeEditor = editorName.ifBlank { "Giáo viên" }.take(80)
        val nowMillis = System.currentTimeMillis()
        val previousStatus = existingRecord?.attendanceStatus ?: AttendanceStatus.ABSENT_UNEXCUSED

        val batch = db.batch()
        if (existingRecord == null) {
            val createPayload = mapOf(
                "sessionId" to session.id,
                "classId" to session.classId,
                "className" to session.className,
                "schoolYearId" to session.schoolYearId,
                "sessionDate" to session.sessionDate,
                "subject" to session.subject,
                "studentId" to student.id,
                "studentCode" to student.studentCode,
                "studentName" to student.fullName,
                "status" to newStatus.key,
                "scanMethod" to ScanMethod.MANUAL.key,
                "checkedInAtMillis" to nowMillis,
                "note" to cleanReason,
                "recordedByUid" to uid,
                "recordedByName" to safeEditor,
                "updatedByUid" to uid,
                "updatedByName" to safeEditor,
                "teacherUids" to session.teacherUids.ifEmpty { listOf(uid) },
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            batch.set(recordRef, createPayload)
        } else {
            batch.update(
                recordRef,
                mapOf(
                    "status" to newStatus.key,
                    "scanMethod" to ScanMethod.MANUAL.key,
                    "checkedInAtMillis" to (existingRecord.checkedInAtMillis.takeIf { it > 0L } ?: nowMillis),
                    "note" to cleanReason,
                    "updatedByUid" to uid,
                    "updatedByName" to safeEditor,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }

        val auditPayload = mapOf(
            "recordId" to recordId,
            "sessionId" to session.id,
            "classId" to session.classId,
            "className" to session.className,
            "studentId" to student.id,
            "studentName" to student.fullName,
            "previousStatus" to previousStatus.key,
            "newStatus" to newStatus.key,
            "reason" to cleanReason,
            "editorUid" to uid,
            "editorName" to safeEditor,
            "teacherUids" to session.teacherUids.ifEmpty { listOf(uid) },
            "createdAt" to FieldValue.serverTimestamp()
        )
        batch.set(logRef, auditPayload)

        // Recompute session counts accurately
        val updatedList = allSessionRecords
            .filter { it.studentId != student.id }
            .map { it.attendanceStatus } + newStatus

        batch.update(
            sessionRef,
            mapOf(
                "presentCount" to updatedList.count { it == AttendanceStatus.PRESENT }.toLong(),
                "lateCount" to updatedList.count { it == AttendanceStatus.LATE }.toLong(),
                "excusedCount" to updatedList.count { it == AttendanceStatus.ABSENT_EXCUSED }.toLong(),
                "unexcusedCount" to updatedList.count { it == AttendanceStatus.ABSENT_UNEXCUSED }.toLong(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )

        try {
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, recordRef.path)
            throw e
        }
    }
}
