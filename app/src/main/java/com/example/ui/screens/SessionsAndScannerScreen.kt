package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController.COORDINATE_SYSTEM_VIEW_REFERENCED
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.EventNote
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.domain.model.AttendanceSession
import com.example.domain.model.AttendanceStatus
import com.example.domain.model.Classroom
import com.example.domain.model.SessionStatus
import com.example.domain.model.StatsCalculator
import com.example.domain.model.Student
import com.example.ui.components.AttendanceStatusChip
import com.example.ui.components.EmptyStateCard
import com.example.ui.theme.StatusLateColor
import com.example.ui.theme.StatusPresentColor
import com.example.ui.theme.StatusUnexcusedColor
import com.example.ui.viewmodel.MainUiState
import com.example.ui.viewmodel.ScanFeedback
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode

@Composable
fun SessionsAndScannerScreen(
    uiState: MainUiState,
    activeScannerSessionId: String?,
    onOpenScanner: (String) -> Unit,
    onCloseScanner: () -> Unit,
    onCreateSession: (Classroom, String, String, String, Long, Int, (String) -> Unit) -> Unit,
    onCloseSession: (AttendanceSession) -> Unit,
    onReopenSession: (AttendanceSession) -> Unit,
    onQrScanned: (String, String) -> Unit,
    onClearScanFeedback: () -> Unit,
    onEditAttendance: (AttendanceSession, Student, AttendanceStatus, String) -> Unit
) {
    if (activeScannerSessionId != null) {
        val session = uiState.sessions.firstOrNull { it.id == activeScannerSessionId }
        if (session != null) {
            BackHandler { onCloseScanner() }
            val sessionStudents = uiState.students.filter { it.classId == session.classId }
            val sessionRecords = uiState.records.filter { it.sessionId == session.id }
            QrScannerOverlayScreen(
                session = session,
                totalStudents = sessionStudents.size,
                scannedCount = sessionRecords.size,
                students = sessionStudents,
                scanFeedback = uiState.scanFeedback,
                onQrDetected = { raw -> onQrScanned(session.id, raw) },
                onClearFeedback = onClearScanFeedback,
                onManualMark = { student, status, reason ->
                    onEditAttendance(session, student, status, reason)
                },
                onBack = onCloseScanner
            )
            return
        }
    }

    var selectedSessionId by remember(uiState.sessions) {
        mutableStateOf(uiState.sessions.firstOrNull()?.id)
    }
    var showCreateDialog by remember { mutableStateOf(false) }
    var activeSubTab by remember { mutableIntStateOf(0) }
    var editingTarget by remember { mutableStateOf<Pair<AttendanceSession, Student>?>(null) }

    val selectedSession = uiState.sessions.firstOrNull { it.id == selectedSessionId }
        ?: uiState.sessions.firstOrNull()

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
                        text = "Buổi học & Điểm danh",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Button(
                        onClick = { showCreateDialog = true },
                        enabled = uiState.classrooms.isNotEmpty(),
                        modifier = Modifier.testTag("create_session_button")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Tạo buổi")
                    }
                }

                if (uiState.sessions.isEmpty()) {
                    EmptyStateCard(
                        icon = Icons.Rounded.EventNote,
                        title = "Chưa có buổi học nào",
                        description = if (uiState.classrooms.isEmpty()) {
                            "Vui lòng tạo lớp học và thêm học sinh ở tab Lớp học trước."
                        } else {
                            "Tạo buổi học theo môn và tiết để bắt đầu quét mã QR điểm danh học sinh."
                        },
                        actionLabel = if (uiState.classrooms.isNotEmpty()) "Tạo buổi học mới" else null,
                        onAction = if (uiState.classrooms.isNotEmpty()) { { showCreateDialog = true } } else null
                    )
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(uiState.sessions, key = { it.id }) { session ->
                            val isSelected = session.id == selectedSession?.id
                            Card(
                                onClick = { selectedSessionId = session.id },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    }
                                ),
                                modifier = Modifier
                                    .width(235.dp)
                                    .testTag("session_card_${session.id}")
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "Lớp ${session.className}",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Surface(
                                            color = if (session.sessionStatus == SessionStatus.OPEN) {
                                                StatusPresentColor.copy(alpha = 0.16f)
                                            } else {
                                                MaterialTheme.colorScheme.surfaceVariant
                                            },
                                            shape = MaterialTheme.shapes.small
                                        ) {
                                            Text(
                                                text = session.sessionStatus.labelVi,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (session.sessionStatus == SessionStatus.OPEN) {
                                                    StatusPresentColor
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                },
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = "${session.subject} • ${session.periodLabel}",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = "${session.sessionDate} • Có mặt: ${session.presentCount} | Trễ: ${session.lateCount}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (selectedSession != null) {
            val sessionStudents = uiState.students.filter { it.classId == selectedSession.classId }
            val sessionRecords = uiState.records.filter { it.sessionId == selectedSession.id }
            val recordByStudent = sessionRecords.associateBy { it.studentId }
            val sessionLogs = uiState.auditLogs.filter { it.sessionId == selectedSession.id }

            item {
                Column(
                    modifier = Modifier.widthIn(max = 840.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Lớp ${selectedSession.className} - ${selectedSession.subject}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${selectedSession.periodLabel} • ${selectedSession.sessionDate} • Đi trễ sau ${selectedSession.lateAfterMinutes} phút",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (selectedSession.sessionStatus == SessionStatus.OPEN) {
                                    Button(
                                        onClick = { onOpenScanner(selectedSession.id) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("open_qr_scanner_button")
                                    ) {
                                        Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Quét QR điểm danh")
                                    }
                                    OutlinedButton(
                                        onClick = { onCloseSession(selectedSession) },
                                        modifier = Modifier.testTag("close_session_button")
                                    ) {
                                        Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Chốt buổi")
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = { onReopenSession(selectedSession) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("reopen_session_button")
                                    ) {
                                        Icon(Icons.Rounded.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Mở lại buổi học")
                                    }
                                }
                            }
                        }
                    }

                    TabRow(selectedTabIndex = activeSubTab) {
                        Tab(
                            selected = activeSubTab == 0,
                            onClick = { activeSubTab = 0 },
                            text = { Text("Điểm danh (${sessionStudents.size})") }
                        )
                        Tab(
                            selected = activeSubTab == 1,
                            onClick = { activeSubTab = 1 },
                            text = { Text("Nhật ký sửa (${sessionLogs.size})") }
                        )
                    }
                }
            }

            if (activeSubTab == 0) {
                items(sessionStudents, key = { it.id }) { student ->
                    val record = recordByStudent[student.id]
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
                                    text = if (record != null) {
                                        "${student.studentCode} • ${record.method.labelVi} lúc ${StatsCalculator.formatTimeVi(record.checkedInAtMillis)}"
                                    } else {
                                        "${student.studentCode} • Chưa điểm danh"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (record?.note?.isNotBlank() == true) {
                                    Text(
                                        text = "Ghi chú: ${record.note}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            if (record != null) {
                                AttendanceStatusChip(status = record.attendanceStatus)
                            } else {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(50)
                                ) {
                                    Text(
                                        text = "Chưa quét",
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            IconButton(
                                onClick = { editingTarget = selectedSession to student },
                                modifier = Modifier.testTag("edit_attendance_${student.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Edit,
                                    contentDescription = "Chỉnh sửa điểm danh ${student.fullName}"
                                )
                            }
                        }
                    }
                }
            } else {
                if (sessionLogs.isEmpty()) {
                    item {
                        Column(modifier = Modifier.widthIn(max = 840.dp)) {
                            EmptyStateCard(
                                icon = Icons.Rounded.EventNote,
                                title = "Chưa có nhật ký chỉnh sửa",
                                description = "Khi giáo viên chỉnh sửa trạng thái điểm danh của học sinh, hệ thống bắt buộc ghi nhận lý do và lưu nhật ký bất biến."
                            )
                        }
                    }
                } else {
                    items(sessionLogs, key = { it.id }) { log ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier
                                .widthIn(max = 840.dp)
                                .fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "${log.studentName}: ${log.previous.labelVi} → ${log.updated.labelVi}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Lý do: ${log.reason}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "Người sửa: ${log.editorName} • ${
                                        log.createdAt?.toDate()?.time?.let { StatsCalculator.formatDateTimeVi(it) } ?: "Vừa xong"
                                    }",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog && uiState.classrooms.isNotEmpty()) {
        CreateSessionDialog(
            classrooms = uiState.classrooms,
            defaultClassId = uiState.selectedClassId,
            onDismiss = { showCreateDialog = false },
            onConfirm = { classroom, subject, period, lateMinutes ->
                onCreateSession(
                    classroom,
                    subject,
                    period,
                    StatsCalculator.currentDateIso(),
                    System.currentTimeMillis(),
                    lateMinutes
                ) { newId ->
                    selectedSessionId = newId
                }
                showCreateDialog = false
            }
        )
    }

    editingTarget?.let { (session, student) ->
        val existingStatus = uiState.records
            .firstOrNull { it.sessionId == session.id && it.studentId == student.id }
            ?.attendanceStatus ?: AttendanceStatus.ABSENT_UNEXCUSED
        EditAttendanceWithAuditDialog(
            student = student,
            currentStatus = existingStatus,
            onDismiss = { editingTarget = null },
            onConfirm = { newStatus, reason ->
                onEditAttendance(session, student, newStatus, reason)
                editingTarget = null
            }
        )
    }
}

@Composable
private fun QrScannerOverlayScreen(
    session: AttendanceSession,
    totalStudents: Int,
    scannedCount: Int,
    students: List<Student>,
    scanFeedback: ScanFeedback?,
    onQrDetected: (String) -> Unit,
    onClearFeedback: () -> Unit,
    onManualMark: (Student, AttendanceStatus, String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var torchEnabled by remember { mutableStateOf(false) }
    var showManualDialog by remember { mutableStateOf(false) }

    val toneGen = remember {
        try {
            ToneGenerator(AudioManager.STREAM_MUSIC, 75)
        } catch (_: Exception) {
            null
        }
    }
    DisposableEffect(Unit) {
        onDispose { toneGen?.release() }
    }

    LaunchedEffect(scanFeedback) {
        when (scanFeedback) {
            is ScanFeedback.Success -> {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
            }
            is ScanFeedback.Duplicate -> {
                toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP2, 180)
            }
            is ScanFeedback.SecurityRejected, is ScanFeedback.Error -> {
                toneGen?.startTone(ToneGenerator.TONE_SUP_ERROR, 260)
            }
            null -> {}
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Top Header
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("scanner_back_button")
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Quay lại")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Quét QR - Lớp ${session.className}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${session.subject} (${session.periodLabel}) • Đã ghi nhận $scannedCount/$totalStudents",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (hasCameraPermission) {
                    IconButton(onClick = { torchEnabled = !torchEnabled }) {
                        Icon(
                            imageVector = if (torchEnabled) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
                            contentDescription = "Bật/tắt đèn flash"
                        )
                    }
                }
            }
        }

        if (!hasCameraPermission) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(24.dp)
            ) {
                EmptyStateCard(
                    icon = Icons.Rounded.CameraAlt,
                    title = "Cần quyền Camera để quét mã QR",
                    description = "Ứng dụng chỉ sử dụng camera để đọc mã QR định danh ngẫu nhiên của học sinh khi điểm danh.",
                    actionLabel = "Cấp quyền Camera",
                    onAction = { permissionLauncher.launch(Manifest.permission.CAMERA) }
                )
            }
        } else {
            val cameraController = remember(context) {
                LifecycleCameraController(context).apply {
                    cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                }
            }
            val barcodeScanner = remember {
                val options = BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                    .build()
                BarcodeScanning.getClient(options)
            }

            DisposableEffect(lifecycleOwner, cameraController) {
                val mainExecutor = ContextCompat.getMainExecutor(context)
                cameraController.setImageAnalysisAnalyzer(
                    mainExecutor,
                    MlKitAnalyzer(
                        listOf(barcodeScanner),
                        COORDINATE_SYSTEM_VIEW_REFERENCED,
                        mainExecutor
                    ) { result ->
                        val barcodes = result?.getValue(barcodeScanner)
                        val raw = barcodes?.firstOrNull()?.rawValue
                        if (!raw.isNullOrBlank()) {
                            onQrDetected(raw)
                        }
                    }
                )
                try {
                    cameraController.bindToLifecycle(lifecycleOwner)
                } catch (e: Exception) {
                    Log.e("QrScanner", "Failed to bind camera controller", e)
                }
                onDispose {
                    cameraController.clearImageAnalysisAnalyzer()
                    barcodeScanner.close()
                }
            }

            LaunchedEffect(torchEnabled) {
                cameraController.enableTorch(torchEnabled)
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            controller = cameraController
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Viewfinder Reticle
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(240.dp)
                        .border(
                            BorderStroke(3.dp, Color(0xFF38D9A9)),
                            RoundedCornerShape(24.dp)
                        )
                )

                // Live scan feedback card
                androidx.compose.animation.AnimatedVisibility(
                    visible = scanFeedback != null,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                ) {
                    scanFeedback?.let { feedback ->
                        val (bg, icon, title, detail) = when (feedback) {
                            is ScanFeedback.Success -> Quad(
                                StatusPresentColor,
                                Icons.Rounded.CheckCircle,
                                "${feedback.studentName} (${feedback.studentCode})",
                                "${feedback.status.labelVi} lúc ${feedback.timeText}"
                            )
                            is ScanFeedback.Duplicate -> Quad(
                                StatusLateColor,
                                Icons.Rounded.WarningAmber,
                                "Cảnh báo quét trùng: ${feedback.studentName}",
                                "Đã điểm danh (${feedback.existingStatus.labelVi}) lúc ${feedback.existingTimeText}"
                            )
                            is ScanFeedback.SecurityRejected -> Quad(
                                StatusUnexcusedColor,
                                Icons.Rounded.ErrorOutline,
                                "Từ chối mã QR",
                                feedback.reason
                            )
                            is ScanFeedback.Error -> Quad(
                                StatusUnexcusedColor,
                                Icons.Rounded.ErrorOutline,
                                "Lỗi ghi nhận",
                                feedback.message
                            )
                        }

                        Surface(
                            color = bg,
                            shape = MaterialTheme.shapes.medium,
                            shadowElevation = 6.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("scan_feedback_banner")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(14.dp)
                            ) {
                                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(title, color = Color.White, fontWeight = FontWeight.Bold)
                                    Text(detail, color = Color.White.copy(alpha = 0.92f), style = MaterialTheme.typography.bodySmall)
                                }
                                TextButton(onClick = onClearFeedback) {
                                    Text("Ẩn", color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                OutlinedButton(
                    onClick = { showManualDialog = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Điểm danh thủ công")
                }
                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Xong")
                }
            }
        }
    }

    if (showManualDialog) {
        var selectedStudent by remember { mutableStateOf(students.firstOrNull()) }
        var reason by remember { mutableStateOf("Học sinh quên mang thẻ QR") }
        var status by remember { mutableStateOf(AttendanceStatus.PRESENT) }
        AlertDialog(
            onDismissRequest = { showManualDialog = false },
            title = { Text("Điểm danh thủ công (Ghi nhật ký)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (students.isEmpty()) {
                        Text("Lớp chưa có học sinh nào.")
                    } else {
                        Text("Chọn học sinh:", style = MaterialTheme.typography.labelMedium)
                        LazyColumn(modifier = Modifier.height(140.dp)) {
                            items(students, key = { it.id }) { st ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    RadioButton(
                                        selected = selectedStudent?.id == st.id,
                                        onClick = { selectedStudent = st }
                                    )
                                    Text("${st.fullName} (${st.studentCode})", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(AttendanceStatus.PRESENT, AttendanceStatus.LATE).forEach { st ->
                                FilterChip(
                                    selected = status == st,
                                    onClick = { status = st },
                                    label = { Text(st.labelVi) }
                                )
                            }
                        }
                        OutlinedTextField(
                            value = reason,
                            onValueChange = { reason = it },
                            label = { Text("Lý do (bắt buộc)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = selectedStudent != null && reason.trim().length >= 3,
                    onClick = {
                        selectedStudent?.let { onManualMark(it, status, reason) }
                        showManualDialog = false
                    }
                ) {
                    Text("Ghi nhận")
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualDialog = false }) { Text("Hủy") }
            }
        )
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
private fun CreateSessionDialog(
    classrooms: List<Classroom>,
    defaultClassId: String?,
    onDismiss: () -> Unit,
    onConfirm: (Classroom, String, String, Int) -> Unit
) {
    var selectedClass by remember {
        mutableStateOf(classrooms.firstOrNull { it.id == defaultClassId } ?: classrooms.first())
    }
    var subject by remember { mutableStateOf("Toán học") }
    var periodLabel by remember { mutableStateOf("Tiết 1 - 2") }
    var lateMinutes by remember { mutableStateOf("10") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tạo buổi điểm danh mới") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Chọn lớp:", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(classrooms, key = { it.id }) { cls ->
                        FilterChip(
                            selected = cls.id == selectedClass.id,
                            onClick = { selectedClass = cls },
                            label = { Text(cls.name) }
                        )
                    }
                }
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("Môn học / Hoạt động") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = periodLabel,
                    onValueChange = { periodLabel = it },
                    label = { Text("Tiết học (VD: Tiết 1)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = lateMinutes,
                    onValueChange = { lateMinutes = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Số phút tính đi trễ") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val mins = lateMinutes.toIntOrNull()?.coerceIn(0, 120) ?: 10
                    onConfirm(selectedClass, subject, periodLabel, mins)
                }
            ) {
                Text("Tạo buổi")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Hủy") }
        }
    )
}

@Composable
private fun EditAttendanceWithAuditDialog(
    student: Student,
    currentStatus: AttendanceStatus,
    onDismiss: () -> Unit,
    onConfirm: (AttendanceStatus, String) -> Unit
) {
    var selectedStatus by remember { mutableStateOf(currentStatus) }
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chỉnh sửa điểm danh") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "${student.fullName} (${student.studentCode})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                AttendanceStatus.entries.forEach { status ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(
                            selected = selectedStatus == status,
                            onClick = { selectedStatus = status }
                        )
                        Text(status.labelVi, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Lý do chỉnh sửa (Bắt buộc ≥ 3 ký tự)") },
                    placeholder = { Text("VD: Phụ huynh nhắn tin xin phép nghỉ ốm") },
                    minLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("audit_reason_input")
                )
            }
        },
        confirmButton = {
            Button(
                enabled = reason.trim().length >= 3,
                onClick = { onConfirm(selectedStatus, reason) },
                modifier = Modifier.testTag("confirm_edit_attendance_button")
            ) {
                Text("Lưu & Ghi nhật ký")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Hủy") }
        }
    )
}
