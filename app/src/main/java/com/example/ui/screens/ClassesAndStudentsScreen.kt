package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Class
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.Classroom
import com.example.domain.model.SchoolYear
import com.example.domain.model.StatsCalculator
import com.example.domain.model.Student
import com.example.domain.model.TeacherProfile
import com.example.export.ExportFileHelper
import com.example.export.PdfExporter
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.StudentQrDialog
import com.example.ui.viewmodel.MainUiState

@Composable
fun ClassesAndStudentsScreen(
    uiState: MainUiState,
    onSelectSchoolYear: (String?) -> Unit,
    onSelectClass: (String?) -> Unit,
    onCreateSchoolYear: (String, Boolean, Long, Long, Long, Long) -> Unit,
    onCreateClassroom: (String, Int, SchoolYear, String, String, List<String>) -> Unit,
    onUpdateClassTeachers: (Classroom, List<String>, String, String) -> Unit,
    onDeleteClassroom: (Classroom) -> Unit,
    onCreateStudent: (String, String, String, String, String, Classroom) -> Unit,
    onUpdateStudent: (Student, String, String, String, String, String) -> Unit,
    onRegenerateStudentQr: (Student) -> Unit,
    onDeleteStudent: (Student) -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var showAddYearDialog by remember { mutableStateOf(false) }
    var showAddClassDialog by remember { mutableStateOf(false) }
    var showAddStudentDialog by remember { mutableStateOf(false) }
    var editingStudent by remember { mutableStateOf<Student?>(null) }
    var qrStudent by remember { mutableStateOf<Student?>(null) }
    var assignTeachersClass by remember { mutableStateOf<Classroom?>(null) }

    val selectedYear = uiState.schoolYears.firstOrNull { it.id == uiState.selectedSchoolYearId }
    val filteredClasses = uiState.classrooms.filter {
        selectedYear == null || it.schoolYearId == selectedYear.id
    }
    val selectedClass = filteredClasses.firstOrNull { it.id == uiState.selectedClassId }
        ?: filteredClasses.firstOrNull()

    val classStudents = remember(uiState.students, selectedClass?.id, searchQuery) {
        val list = if (selectedClass == null) emptyList()
        else uiState.students.filter { it.classId == selectedClass.id }
        if (searchQuery.isBlank()) list
        else list.filter {
            it.fullName.contains(searchQuery, ignoreCase = true) ||
                it.studentCode.contains(searchQuery, ignoreCase = true)
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Column(
                modifier = Modifier.widthIn(max = 840.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Lớp học & Học sinh",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { showAddYearDialog = true },
                            modifier = Modifier.testTag("add_school_year_button")
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Năm học")
                        }
                        Button(
                            onClick = {
                                if (selectedYear == null) showAddYearDialog = true
                                else showAddClassDialog = true
                            },
                            modifier = Modifier.testTag("add_class_button")
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Tạo lớp")
                        }
                    }
                }

                if (uiState.schoolYears.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(uiState.schoolYears, key = { it.id }) { year ->
                            FilterChip(
                                selected = year.id == selectedYear?.id,
                                onClick = { onSelectSchoolYear(year.id) },
                                label = {
                                    Text(if (year.isCurrent) "${year.name} (Hiện tại)" else year.name)
                                }
                            )
                        }
                    }
                }

                if (filteredClasses.isEmpty()) {
                    EmptyStateCard(
                        icon = Icons.Rounded.Class,
                        title = "Chưa có lớp học nào",
                        description = if (selectedYear == null) {
                            "Hãy tạo năm học trước (ví dụ: 2025 - 2026) rồi thêm lớp học THPT."
                        } else {
                            "Nhấn 'Tạo lớp' để thêm lớp 10A1, 11A2... và sinh khóa bảo mật QR riêng."
                        },
                        actionLabel = if (selectedYear == null) "Tạo năm học" else "Tạo lớp mới",
                        onAction = {
                            if (selectedYear == null) showAddYearDialog = true else showAddClassDialog = true
                        }
                    )
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(filteredClasses, key = { it.id }) { classroom ->
                            val isSelected = classroom.id == selectedClass?.id
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    }
                                ),
                                modifier = Modifier
                                    .width(210.dp)
                                    .clickable { onSelectClass(classroom.id) }
                                    .testTag("class_card_${classroom.id}")
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "Lớp ${classroom.name}",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Surface(
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                            shape = MaterialTheme.shapes.small
                                        ) {
                                            Text(
                                                text = "Khối ${classroom.grade}",
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "GVCN: ${classroom.homeroomTeacherName.ifBlank { "Chưa gán" }}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${uiState.students.count { it.classId == classroom.id }} học sinh",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (selectedClass != null) {
            item {
                Column(
                    modifier = Modifier.widthIn(max = 840.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = { showAddStudentDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("add_student_button")
                        ) {
                            Icon(Icons.Rounded.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Thêm học sinh")
                        }
                        if (classStudents.isNotEmpty()) {
                            OutlinedButton(
                                onClick = {
                                    val bytes = PdfExporter.generateQrCardsPdf(selectedClass, classStudents)
                                    ExportFileHelper.shareBytes(
                                        context = context,
                                        fileName = "The_QR_Lop_${selectedClass.name}.pdf",
                                        mimeType = "application/pdf",
                                        bytes = bytes
                                    )
                                },
                                modifier = Modifier.testTag("export_qr_cards_pdf_button")
                            ) {
                                Icon(Icons.Rounded.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("In thẻ QR PDF")
                            }
                        }
                    }

                    if (uiState.isAdmin) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = { assignTeachersClass = selectedClass },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.GroupAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Phân công GV (${selectedClass.teacherUids.size})")
                            }
                            if (classStudents.isEmpty()) {
                                OutlinedButton(
                                    onClick = { onDeleteClassroom(selectedClass) }
                                ) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "Xóa lớp", modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Xóa lớp")
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Tìm học sinh theo tên hoặc mã HS...") },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (classStudents.isEmpty()) {
                item {
                    Column(modifier = Modifier.widthIn(max = 840.dp)) {
                        EmptyStateCard(
                            icon = Icons.Rounded.Badge,
                            title = "Lớp ${selectedClass.name} chưa có học sinh",
                            description = "Thêm học sinh để hệ thống tự sinh mã QR ngẫu nhiên không chứa thông tin cá nhân.",
                            actionLabel = "Thêm học sinh đầu tiên",
                            onAction = { showAddStudentDialog = true }
                        )
                    }
                }
            } else {
                items(classStudents, key = { it.id }) { student ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .widthIn(max = 840.dp)
                            .fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = student.fullName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Mã: ${student.studentCode} • ${student.gender} • QR v${student.qrVersion}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (student.parentPhone.isNotBlank()) {
                                    Text(
                                        text = "SĐT PH: ${student.parentPhone}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton(
                                onClick = { qrStudent = student },
                                modifier = Modifier.testTag("view_qr_${student.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.QrCode2,
                                    contentDescription = "Xem mã QR của ${student.fullName}",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                onClick = { editingStudent = student },
                                modifier = Modifier.testTag("edit_student_${student.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Edit,
                                    contentDescription = "Sửa thông tin ${student.fullName}"
                                )
                            }
                            IconButton(
                                onClick = { onDeleteStudent(student) },
                                modifier = Modifier.testTag("delete_student_${student.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Delete,
                                    contentDescription = "Xóa ${student.fullName}",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddYearDialog) {
        CreateSchoolYearDialog(
            onDismiss = { showAddYearDialog = false },
            onConfirm = { name, isCurrent, s1Start, s1End, s2Start, s2End ->
                onCreateSchoolYear(name, isCurrent, s1Start, s1End, s2Start, s2End)
                showAddYearDialog = false
            }
        )
    }

    if (showAddClassDialog && selectedYear != null) {
        CreateClassroomDialog(
            schoolYear = selectedYear,
            currentProfile = uiState.profile,
            teachers = uiState.teachers,
            onDismiss = { showAddClassDialog = false },
            onConfirm = { name, grade, homeroomUid, homeroomName, teacherUids ->
                onCreateClassroom(name, grade, selectedYear, homeroomUid, homeroomName, teacherUids)
                showAddClassDialog = false
            }
        )
    }

    if (showAddStudentDialog && selectedClass != null) {
        StudentFormDialog(
            title = "Thêm học sinh lớp ${selectedClass.name}",
            initial = null,
            onDismiss = { showAddStudentDialog = false },
            onConfirm = { code, name, gender, dob, phone ->
                onCreateStudent(code, name, gender, dob, phone, selectedClass)
                showAddStudentDialog = false
            }
        )
    }

    editingStudent?.let { student ->
        StudentFormDialog(
            title = "Cập nhật học sinh",
            initial = student,
            onDismiss = { editingStudent = null },
            onConfirm = { code, name, gender, dob, phone ->
                onUpdateStudent(student, code, name, gender, dob, phone)
                editingStudent = null
            }
        )
    }

    qrStudent?.let { student ->
        val freshStudent = uiState.students.firstOrNull { it.id == student.id } ?: student
        val cls = uiState.classrooms.firstOrNull { it.id == freshStudent.classId }
        if (cls != null) {
            StudentQrDialog(
                student = freshStudent,
                classroom = cls,
                onRegenerateQr = { onRegenerateStudentQr(freshStudent) },
                onDismiss = { qrStudent = null }
            )
        }
    }

    assignTeachersClass?.let { cls ->
        AssignTeachersDialog(
            classroom = cls,
            teachers = uiState.teachers,
            onDismiss = { assignTeachersClass = null },
            onConfirm = { uids, homeroomUid, homeroomName ->
                onUpdateClassTeachers(cls, uids, homeroomUid, homeroomName)
                assignTeachersClass = null
            }
        )
    }
}

@Composable
fun CreateSchoolYearDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Boolean, Long, Long, Long, Long) -> Unit
) {
    var name by remember { mutableStateOf("2025 - 2026") }
    var isCurrent by remember { mutableStateOf(true) }
    val (defaultStart, defaultEnd) = remember { StatsCalculator.defaultSchoolYearRange() }
    val midPoint = remember(defaultStart, defaultEnd) { (defaultStart + defaultEnd) / 2L }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Thêm năm học mới") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Tên năm học (VD: 2025 - 2026)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { isCurrent = !isCurrent }
                ) {
                    Checkbox(checked = isCurrent, onCheckedChange = { isCurrent = it })
                    Text("Đặt làm năm học hiện tại", style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    text = "Học kỳ I và Học kỳ II được tự động thiết lập theo khung năm học THPT (tháng 9 → tháng 5).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(name, isCurrent, defaultStart, midPoint, midPoint + 1L, defaultEnd)
                }
            ) {
                Text("Lưu năm học")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Hủy") }
        }
    )
}

@Composable
private fun CreateClassroomDialog(
    schoolYear: SchoolYear,
    currentProfile: TeacherProfile?,
    teachers: List<TeacherProfile>,
    onDismiss: () -> Unit,
    onConfirm: (String, Int, String, String, List<String>) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var grade by remember { mutableIntStateOf(10) }
    var homeroomName by remember { mutableStateOf(currentProfile?.displayName ?: "") }
    val selectedTeacherUids = remember {
        mutableStateListOf<String>().apply {
            currentProfile?.uid?.let { add(it) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tạo lớp học (${schoolYear.name})") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Tên lớp (VD: 10A1, 11B2)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(10, 11, 12).forEach { g ->
                        FilterChip(
                            selected = grade == g,
                            onClick = { grade = g },
                            label = { Text("Khối $g") }
                        )
                    }
                }
                OutlinedTextField(
                    value = homeroomName,
                    onValueChange = { homeroomName = it },
                    label = { Text("Giáo viên chủ nhiệm") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (teachers.isNotEmpty()) {
                    Text("Giáo viên được phân quyền lớp:", style = MaterialTheme.typography.labelMedium)
                    teachers.take(5).forEach { t ->
                        val checked = t.uid in selectedTeacherUids
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (checked) selectedTeacherUids.remove(t.uid)
                                    else selectedTeacherUids.add(t.uid)
                                }
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = {
                                    if (it) {
                                        if (t.uid !in selectedTeacherUids) selectedTeacherUids.add(t.uid)
                                    } else {
                                        selectedTeacherUids.remove(t.uid)
                                    }
                                }
                            )
                            Text("${t.displayName} (${t.subject})", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        name,
                        grade,
                        currentProfile?.uid ?: "",
                        homeroomName,
                        selectedTeacherUids.toList()
                    )
                }
            ) {
                Text("Tạo lớp")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Hủy") }
        }
    )
}

@Composable
private fun StudentFormDialog(
    title: String,
    initial: Student?,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String, String) -> Unit
) {
    var code by remember { mutableStateOf(initial?.studentCode ?: "") }
    var fullName by remember { mutableStateOf(initial?.fullName ?: "") }
    var gender by remember { mutableStateOf(initial?.gender ?: "Nam") }
    var dob by remember { mutableStateOf(initial?.dateOfBirth ?: "") }
    var parentPhone by remember { mutableStateOf(initial?.parentPhone ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("Mã học sinh (VD: HS10A101)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text("Họ và tên học sinh") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Nam", "Nữ", "Khác").forEach { g ->
                        FilterChip(
                            selected = gender == g,
                            onClick = { gender = g },
                            label = { Text(g) }
                        )
                    }
                }
                OutlinedTextField(
                    value = dob,
                    onValueChange = { dob = it },
                    label = { Text("Ngày sinh (DD/MM/YYYY)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = parentPhone,
                    onValueChange = { parentPhone = it },
                    label = { Text("SĐT phụ huynh") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(code, fullName, gender, dob, parentPhone) }) {
                Text("Lưu")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Hủy") }
        }
    )
}

@Composable
private fun AssignTeachersDialog(
    classroom: Classroom,
    teachers: List<TeacherProfile>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>, String, String) -> Unit
) {
    val selected = remember {
        mutableStateListOf<String>().apply { addAll(classroom.teacherUids) }
    }
    var homeroomUid by remember { mutableStateOf(classroom.homeroomTeacherUid) }
    var homeroomName by remember { mutableStateOf(classroom.homeroomTeacherName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Phân quyền giáo viên - Lớp ${classroom.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Chỉ các giáo viên được chọn mới có quyền xem học sinh và điểm danh lớp này.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                teachers.forEach { teacher ->
                    val isChecked = teacher.uid in selected
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isChecked) selected.remove(teacher.uid)
                                else selected.add(teacher.uid)
                            }
                    ) {
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    if (teacher.uid !in selected) selected.add(teacher.uid)
                                } else {
                                    selected.remove(teacher.uid)
                                }
                            }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(teacher.displayName, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${teacher.subject} • ${teacher.email}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(
                            onClick = {
                                homeroomUid = teacher.uid
                                homeroomName = teacher.displayName
                                if (teacher.uid !in selected) selected.add(teacher.uid)
                            }
                        ) {
                            Text(if (homeroomUid == teacher.uid) "GVCN ✓" else "Đặt GVCN")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selected.toList(), homeroomUid, homeroomName) }) {
                Text("Lưu phân quyền")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Hủy") }
        }
    )
}
