package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.AiInsightsRepository
import com.example.data.repository.AttendanceRepository
import com.example.data.repository.NetworkMonitor
import com.example.domain.model.AttendanceAuditLog
import com.example.domain.model.AttendanceRecord
import com.example.domain.model.AttendanceSession
import com.example.domain.model.AttendanceStatus
import com.example.domain.model.ClassAttendanceSummary
import com.example.domain.model.Classroom
import com.example.domain.model.QrSecurity
import com.example.domain.model.SchoolYear
import com.example.domain.model.SessionStatus
import com.example.domain.model.StatsCalculator
import com.example.domain.model.StatsPeriod
import com.example.domain.model.Student
import com.example.domain.model.TeacherInvite
import com.example.domain.model.TeacherProfile
import com.example.domain.model.UserRole
import com.example.firebase.toVietnameseFirestoreError
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val VM_TAG = "MainViewModel"

sealed interface ScanFeedback {
    data class Success(
        val studentName: String,
        val studentCode: String,
        val status: AttendanceStatus,
        val timeText: String
    ) : ScanFeedback

    data class Duplicate(
        val studentName: String,
        val existingStatus: AttendanceStatus,
        val existingTimeText: String
    ) : ScanFeedback

    data class SecurityRejected(val reason: String) : ScanFeedback
    data class Error(val message: String) : ScanFeedback
}

sealed interface AiInsightUiState {
    data object Idle : AiInsightUiState
    data object Loading : AiInsightUiState
    data class Streaming(val text: String) : AiInsightUiState
    data class Success(val text: String) : AiInsightUiState
    data class Error(val message: String) : AiInsightUiState
}

data class MainUiState(
    val isProfileLoading: Boolean = true,
    val profile: TeacherProfile? = null,
    val profileError: String? = null,
    val isOnline: Boolean = true,
    val schoolYears: List<SchoolYear> = emptyList(),
    val classrooms: List<Classroom> = emptyList(),
    val students: List<Student> = emptyList(),
    val sessions: List<AttendanceSession> = emptyList(),
    val records: List<AttendanceRecord> = emptyList(),
    val auditLogs: List<AttendanceAuditLog> = emptyList(),
    val teachers: List<TeacherProfile> = emptyList(),
    val teacherInvites: List<TeacherInvite> = emptyList(),
    val selectedSchoolYearId: String? = null,
    val selectedClassId: String? = null,
    val scanFeedback: ScanFeedback? = null,
    val aiInsightState: AiInsightUiState = AiInsightUiState.Idle,
    val isBusy: Boolean = false,
    val snackbarMessage: String? = null
) {
    val isAdmin: Boolean get() = profile?.userRole == UserRole.ADMIN
    val isActiveAccount: Boolean get() = profile?.active != false
}

class MainViewModel(
    private val repository: AttendanceRepository,
    private val aiRepository: AiInsightsRepository,
    private val networkMonitor: NetworkMonitor,
    private val currentUserId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var streamJobs: MutableList<Job> = mutableListOf()
    private val recentScanTimestamps = mutableMapOf<String, Long>()

    init {
        observeConnectivity()
        bootstrapUserSession()
    }

    private fun observeConnectivity() {
        viewModelScope.launch {
            networkMonitor.isOnlineFlow.collect { online ->
                _uiState.update { it.copy(isOnline = online) }
            }
        }
    }

    fun bootstrapUserSession() {
        viewModelScope.launch {
            _uiState.update { it.copy(isProfileLoading = true, profileError = null) }
            val result = repository.ensureTeacherProfile()
            result.onSuccess { profile ->
                _uiState.update { it.copy(isProfileLoading = false, profile = profile) }
                startDomainStreams(isAdmin = profile.userRole == UserRole.ADMIN)
            }.onFailure { err ->
                if (err is CancellationException) throw err
                Log.e(VM_TAG, "Failed to bootstrap teacher profile", err)
                _uiState.update {
                    it.copy(
                        isProfileLoading = false,
                        profileError = toVietnameseFirestoreError(err)
                    )
                }
            }
        }
    }

    private fun startDomainStreams(isAdmin: Boolean) {
        streamJobs.forEach { it.cancel() }
        streamJobs.clear()

        streamJobs += viewModelScope.launch {
            repository.observeOwnTeacherProfile(currentUserId)
                .catch { handleStreamError("hồ sơ giáo viên", it) }
                .collect { freshProfile ->
                    if (freshProfile != null) {
                        val roleChanged = freshProfile.userRole != _uiState.value.profile?.userRole
                        _uiState.update { it.copy(profile = freshProfile) }
                        if (roleChanged) {
                            startDomainStreams(isAdmin = freshProfile.userRole == UserRole.ADMIN)
                        }
                    }
                }
        }

        streamJobs += viewModelScope.launch {
            repository.observeSchoolYears(isAdmin, currentUserId)
                .catch { handleStreamError("năm học", it) }
                .collect { years ->
                    _uiState.update { state ->
                        val activeId = state.selectedSchoolYearId
                            ?.takeIf { id -> years.any { it.id == id } }
                            ?: years.firstOrNull { it.isCurrent }?.id
                            ?: years.firstOrNull()?.id
                        state.copy(schoolYears = years, selectedSchoolYearId = activeId)
                    }
                }
        }

        streamJobs += viewModelScope.launch {
            repository.observeClassrooms(isAdmin, currentUserId)
                .catch { handleStreamError("lớp học", it) }
                .collect { classes ->
                    _uiState.update { state ->
                        val activeClassId = state.selectedClassId
                            ?.takeIf { id -> classes.any { it.id == id } }
                            ?: classes.firstOrNull()?.id
                        state.copy(classrooms = classes, selectedClassId = activeClassId)
                    }
                }
        }

        streamJobs += viewModelScope.launch {
            repository.observeStudents(isAdmin, currentUserId)
                .catch { handleStreamError("học sinh", it) }
                .collect { students -> _uiState.update { it.copy(students = students) } }
        }

        streamJobs += viewModelScope.launch {
            repository.observeSessions(isAdmin, currentUserId)
                .catch { handleStreamError("buổi học", it) }
                .collect { sessions -> _uiState.update { it.copy(sessions = sessions) } }
        }

        streamJobs += viewModelScope.launch {
            repository.observeAttendanceRecords(isAdmin, currentUserId)
                .catch { handleStreamError("điểm danh", it) }
                .collect { records -> _uiState.update { it.copy(records = records) } }
        }

        streamJobs += viewModelScope.launch {
            repository.observeAuditLogs(isAdmin, currentUserId)
                .catch { handleStreamError("nhật ký chỉnh sửa", it) }
                .collect { logs -> _uiState.update { it.copy(auditLogs = logs) } }
        }

        if (isAdmin) {
            streamJobs += viewModelScope.launch {
                repository.observeAllTeachers(isAdmin = true)
                    .catch { handleStreamError("danh sách giáo viên", it) }
                    .collect { teachers -> _uiState.update { it.copy(teachers = teachers) } }
            }
            streamJobs += viewModelScope.launch {
                repository.observeTeacherInvites(isAdmin = true)
                    .catch { handleStreamError("lời mời giáo viên", it) }
                    .collect { invites -> _uiState.update { it.copy(teacherInvites = invites) } }
            }
        }
    }

    private fun handleStreamError(domainName: String, error: Throwable) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(VM_TAG, "Error observing $domainName", rootError)
        _uiState.update {
            it.copy(snackbarMessage = "Lỗi tải $domainName: ${toVietnameseFirestoreError(rootError)}")
        }
    }

    fun selectSchoolYear(yearId: String?) {
        _uiState.update { it.copy(selectedSchoolYearId = yearId) }
    }

    fun selectClass(classId: String?) {
        _uiState.update { it.copy(selectedClassId = classId) }
    }

    fun consumeSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun clearScanFeedback() {
        _uiState.update { it.copy(scanFeedback = null) }
    }

    // --- SCHOOL YEAR ACTIONS ---

    fun createSchoolYear(
        name: String,
        isCurrent: Boolean,
        semester1StartMillis: Long,
        semester1EndMillis: Long,
        semester2StartMillis: Long,
        semester2EndMillis: Long
    ) {
        if (name.trim().length < 3) {
            _uiState.update { it.copy(snackbarMessage = "Tên năm học phải có ít nhất 3 ký tự.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            repository.createSchoolYear(
                name = name,
                isCurrent = isCurrent,
                isAdmin = _uiState.value.isAdmin,
                semester1StartMillis = semester1StartMillis,
                semester1EndMillis = semester1EndMillis,
                semester2StartMillis = semester2StartMillis,
                semester2EndMillis = semester2EndMillis
            ).onSuccess {
                _uiState.update { it.copy(isBusy = false, snackbarMessage = "Đã thêm năm học $name.") }
            }.onFailure { err ->
                if (err is CancellationException) throw err
                _uiState.update { it.copy(isBusy = false, snackbarMessage = toVietnameseFirestoreError(err)) }
            }
        }
    }

    fun deleteSchoolYear(yearId: String) {
        viewModelScope.launch {
            repository.deleteSchoolYear(yearId)
                .onSuccess { _uiState.update { it.copy(snackbarMessage = "Đã xóa năm học.") } }
                .onFailure { err ->
                    if (err is CancellationException) throw err
                    _uiState.update { it.copy(snackbarMessage = toVietnameseFirestoreError(err)) }
                }
        }
    }

    // --- CLASSROOM ACTIONS ---

    fun createClassroom(
        name: String,
        grade: Int,
        schoolYear: SchoolYear,
        homeroomTeacherUid: String,
        homeroomTeacherName: String,
        assignedTeacherUids: List<String>
    ) {
        if (name.isBlank()) {
            _uiState.update { it.copy(snackbarMessage = "Vui lòng nhập tên lớp học (ví dụ: 10A1).") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            repository.createClassroom(
                name = name,
                grade = grade,
                schoolYear = schoolYear,
                homeroomTeacherUid = homeroomTeacherUid.ifBlank { currentUserId },
                homeroomTeacherName = homeroomTeacherName.ifBlank { _uiState.value.profile?.displayName ?: "Giáo viên" },
                assignedTeacherUids = assignedTeacherUids
            ).onSuccess { newClassId ->
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        selectedClassId = newClassId,
                        snackbarMessage = "Đã tạo lớp $name và sinh khóa bảo mật QR."
                    )
                }
            }.onFailure { err ->
                if (err is CancellationException) throw err
                _uiState.update { it.copy(isBusy = false, snackbarMessage = toVietnameseFirestoreError(err)) }
            }
        }
    }

    fun updateClassroomTeachers(
        classroom: Classroom,
        newTeacherUids: List<String>,
        homeroomUid: String,
        homeroomName: String
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            repository.updateClassroomTeachers(classroom, newTeacherUids, homeroomUid, homeroomName)
                .onSuccess {
                    _uiState.update { it.copy(isBusy = false, snackbarMessage = "Đã cập nhật phân quyền giáo viên cho lớp ${classroom.name}.") }
                }
                .onFailure { err ->
                    if (err is CancellationException) throw err
                    _uiState.update { it.copy(isBusy = false, snackbarMessage = toVietnameseFirestoreError(err)) }
                }
        }
    }

    fun deleteClassroom(classroom: Classroom) {
        viewModelScope.launch {
            repository.deleteClassroom(classroom.id)
                .onSuccess { _uiState.update { it.copy(snackbarMessage = "Đã xóa lớp ${classroom.name}.") } }
                .onFailure { err ->
                    if (err is CancellationException) throw err
                    _uiState.update { it.copy(snackbarMessage = toVietnameseFirestoreError(err)) }
                }
        }
    }

    // --- STUDENT ACTIONS ---

    fun createStudent(
        studentCode: String,
        fullName: String,
        gender: String,
        dateOfBirth: String,
        parentPhone: String,
        classroom: Classroom
    ) {
        if (studentCode.isBlank() || fullName.isBlank()) {
            _uiState.update { it.copy(snackbarMessage = "Vui lòng nhập đầy đủ mã học sinh và họ tên.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            repository.createStudent(studentCode, fullName, gender, dateOfBirth, parentPhone, classroom)
                .onSuccess {
                    _uiState.update { it.copy(isBusy = false, snackbarMessage = "Đã thêm học sinh $fullName với mã QR bảo mật.") }
                }
                .onFailure { err ->
                    if (err is CancellationException) throw err
                    _uiState.update { it.copy(isBusy = false, snackbarMessage = toVietnameseFirestoreError(err)) }
                }
        }
    }

    fun updateStudent(
        student: Student,
        studentCode: String,
        fullName: String,
        gender: String,
        dateOfBirth: String,
        parentPhone: String
    ) {
        viewModelScope.launch {
            repository.updateStudent(student, studentCode, fullName, gender, dateOfBirth, parentPhone)
                .onSuccess { _uiState.update { it.copy(snackbarMessage = "Đã cập nhật thông tin học sinh $fullName.") } }
                .onFailure { err ->
                    if (err is CancellationException) throw err
                    _uiState.update { it.copy(snackbarMessage = toVietnameseFirestoreError(err)) }
                }
        }
    }

    fun regenerateStudentQr(student: Student) {
        viewModelScope.launch {
            repository.regenerateStudentQrToken(student)
                .onSuccess {
                    _uiState.update { it.copy(snackbarMessage = "Đã cấp lại mã QR mới (v${student.qrVersion + 1}) và vô hiệu hóa thẻ cũ của ${student.fullName}.") }
                }
                .onFailure { err ->
                    if (err is CancellationException) throw err
                    _uiState.update { it.copy(snackbarMessage = toVietnameseFirestoreError(err)) }
                }
        }
    }

    fun deleteStudent(student: Student) {
        viewModelScope.launch {
            repository.deleteStudent(student)
                .onSuccess { _uiState.update { it.copy(snackbarMessage = "Đã xóa học sinh ${student.fullName}.") } }
                .onFailure { err ->
                    if (err is CancellationException) throw err
                    _uiState.update { it.copy(snackbarMessage = toVietnameseFirestoreError(err)) }
                }
        }
    }

    // --- SESSION & QR SCANNING ACTIONS ---

    fun createSession(
        classroom: Classroom,
        subject: String,
        periodLabel: String,
        sessionDate: String,
        scheduledStartMillis: Long,
        lateAfterMinutes: Int,
        onCreated: (String) -> Unit = {}
    ) {
        if (subject.isBlank() || periodLabel.isBlank()) {
            _uiState.update { it.copy(snackbarMessage = "Vui lòng nhập môn học và tiết học.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            val creatorName = _uiState.value.profile?.displayName ?: "Giáo viên"
            repository.createSession(
                classroom = classroom,
                subject = subject,
                periodLabel = periodLabel,
                sessionDate = sessionDate,
                scheduledStartMillis = scheduledStartMillis,
                lateAfterMinutes = lateAfterMinutes,
                creatorName = creatorName
            ).onSuccess { sessionId ->
                _uiState.update { it.copy(isBusy = false, snackbarMessage = "Đã tạo buổi học $subject - Lớp ${classroom.name}.") }
                onCreated(sessionId)
            }.onFailure { err ->
                if (err is CancellationException) throw err
                _uiState.update { it.copy(isBusy = false, snackbarMessage = toVietnameseFirestoreError(err)) }
            }
        }
    }

    fun closeSessionAndMarkAbsent(session: AttendanceSession) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            val classStudents = _uiState.value.students.filter { it.classId == session.classId }
            val sessionRecords = _uiState.value.records.filter { it.sessionId == session.id }
            val teacherName = _uiState.value.profile?.displayName ?: "Giáo viên"
            repository.closeSessionAndMarkAbsent(session, classStudents, sessionRecords, teacherName)
                .onSuccess { markedAbsentCount ->
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            snackbarMessage = "Đã chốt buổi học. Đánh dấu vắng không phép $markedAbsentCount học sinh chưa quét."
                        )
                    }
                }
                .onFailure { err ->
                    if (err is CancellationException) throw err
                    _uiState.update { it.copy(isBusy = false, snackbarMessage = toVietnameseFirestoreError(err)) }
                }
        }
    }

    fun reopenSession(session: AttendanceSession) {
        viewModelScope.launch {
            repository.reopenSession(session.id)
                .onSuccess { _uiState.update { it.copy(snackbarMessage = "Đã mở lại buổi học để tiếp tục điểm danh.") } }
                .onFailure { err ->
                    if (err is CancellationException) throw err
                    _uiState.update { it.copy(snackbarMessage = toVietnameseFirestoreError(err)) }
                }
        }
    }

    fun processScannedQr(sessionId: String, rawQrText: String) {
        val nowMillis = System.currentTimeMillis()
        // Short 2.5s cooldown per identical QR text to prevent camera frame spam
        val lastSeen = recentScanTimestamps[rawQrText]
        if (lastSeen != null && nowMillis - lastSeen < 2500L) {
            return
        }
        recentScanTimestamps[rawQrText] = nowMillis

        val state = _uiState.value
        val session = state.sessions.firstOrNull { it.id == sessionId }
        if (session == null) {
            _uiState.update { it.copy(scanFeedback = ScanFeedback.Error("Không tìm thấy buổi học.")) }
            return
        }
        if (session.sessionStatus != SessionStatus.OPEN) {
            _uiState.update {
                it.copy(scanFeedback = ScanFeedback.SecurityRejected("Buổi học đã chốt, không thể quét thêm QR."))
            }
            return
        }
        val classroom = state.classrooms.firstOrNull { it.id == session.classId }
        if (classroom == null) {
            _uiState.update {
                it.copy(scanFeedback = ScanFeedback.SecurityRejected("Không có quyền truy cập lớp học này."))
            }
            return
        }

        // 1. Format check
        val parsed = QrSecurity.parsePayload(rawQrText)
        if (parsed !is QrSecurity.ParseResult.ValidFormat) {
            _uiState.update {
                it.copy(
                    scanFeedback = ScanFeedback.SecurityRejected(
                        "Mã QR không hợp lệ hoặc không thuộc hệ thống Quản lý lớp QR - PP Hậu."
                    )
                )
            }
            return
        }

        // 2. Class roster token lookup
        val student = state.students.firstOrNull {
            it.classId == classroom.id && it.qrToken == parsed.qrToken
        }
        if (student == null) {
            _uiState.update {
                it.copy(
                    scanFeedback = ScanFeedback.SecurityRejected(
                        "Mã QR không thuộc lớp ${classroom.name} hoặc mã thẻ đã bị thu hồi!"
                    )
                )
            }
            return
        }

        // 3. Cryptographic HMAC signature verification
        if (!QrSecurity.verifySignature(parsed, classroom, student)) {
            _uiState.update {
                it.copy(
                    scanFeedback = ScanFeedback.SecurityRejected(
                        "Cảnh báo bảo mật: Chữ ký QR giả mạo hoặc thẻ đã hết hiệu lực (v${student.qrVersion})!"
                    )
                )
            }
            return
        }

        // 4. Anti-duplicate check
        val existingRecord = state.records.firstOrNull {
            it.sessionId == session.id && it.studentId == student.id
        }
        if (existingRecord != null) {
            _uiState.update {
                it.copy(
                    scanFeedback = ScanFeedback.Duplicate(
                        studentName = student.fullName,
                        existingStatus = existingRecord.attendanceStatus,
                        existingTimeText = StatsCalculator.formatTimeVi(existingRecord.checkedInAtMillis)
                    )
                )
            }
            return
        }

        // 5. Late threshold evaluation
        val deadlineMillis = session.scheduledStartMillis + (session.lateAfterMinutes * 60_000L)
        val status = if (nowMillis <= deadlineMillis) AttendanceStatus.PRESENT else AttendanceStatus.LATE
        val teacherName = state.profile?.displayName ?: "Giáo viên"

        viewModelScope.launch {
            repository.recordQrScanAttendance(
                session = session,
                student = student,
                status = status,
                checkedInAtMillis = nowMillis,
                teacherName = teacherName
            ).onSuccess {
                _uiState.update {
                    it.copy(
                        scanFeedback = ScanFeedback.Success(
                            studentName = student.fullName,
                            studentCode = student.studentCode,
                            status = status,
                            timeText = StatsCalculator.formatTimeVi(nowMillis)
                        )
                    )
                }
            }.onFailure { err ->
                if (err is CancellationException) throw err
                _uiState.update {
                    it.copy(scanFeedback = ScanFeedback.Error(toVietnameseFirestoreError(err)))
                }
            }
        }
    }

    fun editAttendanceWithAuditLog(
        session: AttendanceSession,
        student: Student,
        newStatus: AttendanceStatus,
        reason: String
    ) {
        val state = _uiState.value
        val sessionRecords = state.records.filter { it.sessionId == session.id }
        val existing = sessionRecords.firstOrNull { it.studentId == student.id }
        val editorName = state.profile?.displayName ?: "Giáo viên"

        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            repository.editAttendanceWithAuditLog(
                session = session,
                student = student,
                existingRecord = existing,
                allSessionRecords = sessionRecords,
                newStatus = newStatus,
                reason = reason,
                editorName = editorName
            ).onSuccess {
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        snackbarMessage = "Đã cập nhật trạng thái '${newStatus.labelVi}' cho ${student.fullName} và ghi nhật ký."
                    )
                }
            }.onFailure { err ->
                if (err is CancellationException) throw err
                _uiState.update {
                    it.copy(isBusy = false, snackbarMessage = toVietnameseFirestoreError(err))
                }
            }
        }
    }

    // --- ADMIN & TEACHER MANAGEMENT ---

    fun createTeacherInvite(
        email: String,
        fullName: String,
        subject: String,
        phone: String,
        role: UserRole
    ) {
        if (!email.contains("@") || fullName.isBlank()) {
            _uiState.update { it.copy(snackbarMessage = "Vui lòng nhập email hợp lệ và họ tên giáo viên.") }
            return
        }
        viewModelScope.launch {
            repository.createTeacherInvite(email, fullName, subject, phone, role)
                .onSuccess {
                    _uiState.update { it.copy(snackbarMessage = "Đã thêm giáo viên $fullName ($email).") }
                }
                .onFailure { err ->
                    if (err is CancellationException) throw err
                    _uiState.update { it.copy(snackbarMessage = toVietnameseFirestoreError(err)) }
                }
        }
    }

    fun deleteTeacherInvite(inviteId: String) {
        viewModelScope.launch {
            repository.deleteTeacherInvite(inviteId)
                .onSuccess { _uiState.update { it.copy(snackbarMessage = "Đã xóa lời mời giáo viên.") } }
                .onFailure { err ->
                    if (err is CancellationException) throw err
                    _uiState.update { it.copy(snackbarMessage = toVietnameseFirestoreError(err)) }
                }
        }
    }

    fun setTeacherRole(teacher: TeacherProfile, role: UserRole) {
        viewModelScope.launch {
            repository.setTeacherRole(teacher, role)
                .onSuccess {
                    _uiState.update { it.copy(snackbarMessage = "Đã chuyển vai trò ${teacher.displayName} thành ${role.labelVi}.") }
                }
                .onFailure { err ->
                    if (err is CancellationException) throw err
                    _uiState.update { it.copy(snackbarMessage = toVietnameseFirestoreError(err)) }
                }
        }
    }

    fun setTeacherActive(teacher: TeacherProfile, active: Boolean) {
        viewModelScope.launch {
            repository.setTeacherActive(teacher.uid, active)
                .onSuccess {
                    val label = if (active) "kích hoạt" else "tạm khóa"
                    _uiState.update { it.copy(snackbarMessage = "Đã $label tài khoản ${teacher.displayName}.") }
                }
                .onFailure { err ->
                    if (err is CancellationException) throw err
                    _uiState.update { it.copy(snackbarMessage = toVietnameseFirestoreError(err)) }
                }
        }
    }

    fun updateOwnProfile(displayName: String, subject: String, phone: String) {
        if (displayName.isBlank()) {
            _uiState.update { it.copy(snackbarMessage = "Họ tên không được để trống.") }
            return
        }
        viewModelScope.launch {
            repository.updateOwnProfile(displayName, subject, phone)
                .onSuccess { _uiState.update { it.copy(snackbarMessage = "Đã cập nhật hồ sơ giáo viên.") } }
                .onFailure { err ->
                    if (err is CancellationException) throw err
                    _uiState.update { it.copy(snackbarMessage = toVietnameseFirestoreError(err)) }
                }
        }
    }

    // --- AI ATTENDANCE INSIGHTS ---

    fun generateAiAttendanceInsights(
        className: String,
        schoolYearName: String,
        period: StatsPeriod,
        summary: ClassAttendanceSummary
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(aiInsightState = AiInsightUiState.Loading) }
            try {
                var finalText = ""
                aiRepository.streamAttendanceAnalysis(className, schoolYearName, period, summary)
                    .collect { partial ->
                        finalText = partial
                        _uiState.update { it.copy(aiInsightState = AiInsightUiState.Streaming(partial)) }
                    }
                _uiState.update { it.copy(aiInsightState = AiInsightUiState.Success(finalText)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(VM_TAG, "AI insight error", e)
                _uiState.update {
                    it.copy(
                        aiInsightState = AiInsightUiState.Error(
                            e.localizedMessage ?: "Không thể kết nối AI phân tích chuyên cần lúc này."
                        )
                    )
                }
            }
        }
    }
}
