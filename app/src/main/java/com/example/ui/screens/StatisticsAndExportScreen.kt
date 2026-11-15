package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.ClassAttendanceSummary
import com.example.domain.model.StatsCalculator
import com.example.domain.model.StatsPeriod
import com.example.export.ExcelExporter
import com.example.export.ExportFileHelper
import com.example.export.PdfExporter
import com.example.ui.components.EmptyStateCard
import com.example.ui.theme.StatusExcusedColor
import com.example.ui.theme.StatusLateColor
import com.example.ui.theme.StatusPresentColor
import com.example.ui.theme.StatusUnexcusedColor
import com.example.ui.viewmodel.AiInsightUiState
import com.example.ui.viewmodel.MainUiState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatisticsAndExportScreen(
    uiState: MainUiState,
    onSelectClass: (String?) -> Unit,
    onGenerateAiInsights: (String, String, StatsPeriod, ClassAttendanceSummary) -> Unit
) {
    val context = LocalContext.current
    var selectedPeriod by remember { mutableStateOf(StatsPeriod.MONTH) }
    var selectedYearMonth by remember { mutableStateOf(StatsCalculator.currentYearMonth()) }
    var selectedStudentId by remember { mutableStateOf<String?>(null) }

    val selectedClass = uiState.classrooms.firstOrNull { it.id == uiState.selectedClassId }
        ?: uiState.classrooms.firstOrNull()
    val schoolYear = uiState.schoolYears.firstOrNull { it.id == selectedClass?.schoolYearId }
        ?: uiState.schoolYears.firstOrNull { it.id == uiState.selectedSchoolYearId }

    val classStudents = remember(uiState.students, selectedClass?.id) {
        if (selectedClass == null) emptyList()
        else uiState.students.filter { it.classId == selectedClass.id }
    }

    val classRecords = remember(uiState.records, selectedClass?.id) {
        if (selectedClass == null) emptyList()
        else uiState.records.filter { it.classId == selectedClass.id }
    }

    val classSessions = remember(uiState.sessions, selectedClass?.id) {
        if (selectedClass == null) emptyList()
        else uiState.sessions.filter { it.classId == selectedClass.id }
    }

    val filteredRecords = remember(classRecords, selectedPeriod, schoolYear?.id, selectedYearMonth) {
        StatsCalculator.filterRecordsByPeriod(
            records = classRecords,
            period = selectedPeriod,
            schoolYear = schoolYear,
            selectedYearMonth = selectedYearMonth
        )
    }

    val filteredSessions = remember(classSessions, selectedPeriod, schoolYear?.id, selectedYearMonth) {
        StatsCalculator.filterSessionsByPeriod(
            sessions = classSessions,
            period = selectedPeriod,
            schoolYear = schoolYear,
            selectedYearMonth = selectedYearMonth
        )
    }

    val effectiveStudentId = selectedStudentId?.takeIf { id -> classStudents.any { it.id == id } }

    val summary = remember(classStudents, filteredRecords, filteredSessions, effectiveStudentId) {
        StatsCalculator.computeClassSummary(
            students = classStudents,
            filteredRecords = filteredRecords,
            filteredSessions = filteredSessions,
            selectedStudentId = effectiveStudentId
        )
    }

    val classLogs = remember(uiState.auditLogs, selectedClass?.id, effectiveStudentId) {
        uiState.auditLogs.filter {
            (selectedClass == null || it.classId == selectedClass.id) &&
                (effectiveStudentId == null || it.studentId == effectiveStudentId)
        }
    }

    var pendingBytes by remember { mutableStateOf<ByteArray?>(null) }

    val saveExcelLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        )
    ) { uri ->
        val bytes = pendingBytes
        pendingBytes = null
        if (uri != null && bytes != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                Toast.makeText(context, "Đã lưu file Excel thành công.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Lỗi lưu file Excel: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val bytes = pendingBytes
        pendingBytes = null
        if (uri != null && bytes != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                Toast.makeText(context, "Đã lưu file PDF thành công.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Lỗi lưu file PDF: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Thống kê & Xuất báo cáo",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                if (uiState.classrooms.isEmpty()) {
                    EmptyStateCard(
                        icon = Icons.Rounded.BarChart,
                        title = "Chưa có dữ liệu lớp học",
                        description = "Tạo lớp học, thêm học sinh và thực hiện quét QR điểm danh để xem thống kê và xuất Excel/PDF."
                    )
                } else {
                    // Class selector
                    Text("Chọn lớp học:", style = MaterialTheme.typography.labelMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(uiState.classrooms, key = { it.id }) { cls ->
                            FilterChip(
                                selected = cls.id == selectedClass?.id,
                                onClick = {
                                    selectedStudentId = null
                                    onSelectClass(cls.id)
                                },
                                label = { Text("Lớp ${cls.name}") }
                            )
                        }
                    }

                    // Period selector
                    Text("Kỳ thống kê:", style = MaterialTheme.typography.labelMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        StatsPeriod.entries.forEach { period ->
                            FilterChip(
                                selected = selectedPeriod == period,
                                onClick = { selectedPeriod = period },
                                label = { Text(period.labelVi) }
                            )
                        }
                    }

                    if (selectedPeriod == StatsPeriod.MONTH) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = { selectedYearMonth = shiftYearMonth(selectedYearMonth, -1) }
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                                    contentDescription = "Tháng trước"
                                )
                            }
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = "Tháng $selectedYearMonth",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                            }
                            IconButton(
                                onClick = { selectedYearMonth = shiftYearMonth(selectedYearMonth, 1) }
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                    contentDescription = "Tháng sau"
                                )
                            }
                        }
                    }

                    // Student filter
                    if (classStudents.isNotEmpty()) {
                        Text("Phạm vi học sinh:", style = MaterialTheme.typography.labelMedium)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                FilterChip(
                                    selected = effectiveStudentId == null,
                                    onClick = { selectedStudentId = null },
                                    label = { Text("Tất cả (${classStudents.size})") }
                                )
                            }
                            items(classStudents, key = { it.id }) { student ->
                                FilterChip(
                                    selected = effectiveStudentId == student.id,
                                    onClick = { selectedStudentId = student.id },
                                    label = { Text(student.fullName) }
                                )
                            }
                        }
                    }

                    // Donut chart & Breakdown Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = "Tổng quan chuyên cần (${summary.totalSessions} buổi học)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(18.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                AttendanceDonutChart(
                                    present = summary.presentCount,
                                    late = summary.lateCount,
                                    excused = summary.excusedCount,
                                    unexcused = summary.unexcusedCount,
                                    ratePercent = summary.attendanceRatePercent,
                                    modifier = Modifier.size(138.dp)
                                )
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    ChartLegendRow("Có mặt", summary.presentCount, StatusPresentColor)
                                    ChartLegendRow("Đi trễ", summary.lateCount, StatusLateColor)
                                    ChartLegendRow("Vắng có phép", summary.excusedCount, StatusExcusedColor)
                                    ChartLegendRow("Vắng không phép", summary.unexcusedCount, StatusUnexcusedColor)
                                }
                            }
                        }
                    }

                    // Export Actions Card
                    if (selectedClass != null) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Xuất báo cáo Excel & PDF",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Button(
                                        onClick = {
                                            val bytes = ExcelExporter.generateAttendanceWorkbookBytes(
                                                className = selectedClass.name,
                                                schoolYearName = schoolYear?.name ?: selectedClass.schoolYearName,
                                                period = selectedPeriod,
                                                summary = summary,
                                                records = filteredRecords,
                                                auditLogs = classLogs
                                            )
                                            pendingBytes = bytes
                                            saveExcelLauncher.launch(
                                                "ChuyenCan_Lop_${selectedClass.name}_${StatsCalculator.currentDateIso()}.xlsx"
                                            )
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("export_excel_save_button")
                                    ) {
                                        Icon(Icons.Rounded.TableChart, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Lưu Excel")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            val bytes = ExcelExporter.generateAttendanceWorkbookBytes(
                                                className = selectedClass.name,
                                                schoolYearName = schoolYear?.name ?: selectedClass.schoolYearName,
                                                period = selectedPeriod,
                                                summary = summary,
                                                records = filteredRecords,
                                                auditLogs = classLogs
                                            )
                                            ExportFileHelper.shareBytes(
                                                context = context,
                                                fileName = "ChuyenCan_Lop_${selectedClass.name}.xlsx",
                                                mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                                bytes = bytes
                                            )
                                        },
                                        modifier = Modifier.testTag("export_excel_share_button")
                                    ) {
                                        Icon(Icons.Rounded.Share, contentDescription = "Chia sẻ Excel", modifier = Modifier.size(18.dp))
                                    }
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Button(
                                        onClick = {
                                            val bytes = PdfExporter.generateAttendanceReportPdf(
                                                className = selectedClass.name,
                                                schoolYearName = schoolYear?.name ?: selectedClass.schoolYearName,
                                                period = selectedPeriod,
                                                teacherName = uiState.profile?.displayName ?: "Giáo viên",
                                                summary = summary
                                            )
                                            pendingBytes = bytes
                                            savePdfLauncher.launch(
                                                "BaoCao_ChuyenCan_${selectedClass.name}_${StatsCalculator.currentDateIso()}.pdf"
                                            )
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("export_pdf_save_button")
                                    ) {
                                        Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Lưu PDF")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            val bytes = PdfExporter.generateAttendanceReportPdf(
                                                className = selectedClass.name,
                                                schoolYearName = schoolYear?.name ?: selectedClass.schoolYearName,
                                                period = selectedPeriod,
                                                teacherName = uiState.profile?.displayName ?: "Giáo viên",
                                                summary = summary
                                            )
                                            ExportFileHelper.shareBytes(
                                                context = context,
                                                fileName = "BaoCao_ChuyenCan_${selectedClass.name}.pdf",
                                                mimeType = "application/pdf",
                                                bytes = bytes
                                            )
                                        },
                                        modifier = Modifier.testTag("export_pdf_share_button")
                                    ) {
                                        Icon(Icons.Rounded.Share, contentDescription = "Chia sẻ PDF", modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }

                        // Firebase AI Logic Insights Card
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
                            ),
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Rounded.AutoAwesome,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Trợ lý AI phân tích chuyên cần",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Text(
                                    text = "Tự động phát hiện học sinh có nguy cơ vi phạm chuyên cần và gợi ý tin nhắn nhắc phụ huynh dựa trên dữ liệu thực tế.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Button(
                                    onClick = {
                                        onGenerateAiInsights(
                                            selectedClass.name,
                                            schoolYear?.name ?: selectedClass.schoolYearName,
                                            selectedPeriod,
                                            summary
                                        )
                                    },
                                    enabled = uiState.aiInsightState !is AiInsightUiState.Loading &&
                                        uiState.aiInsightState !is AiInsightUiState.Streaming,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("run_ai_insights_button")
                                ) {
                                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Phân tích chuyên cần bằng AI")
                                }

                                when (val aiState = uiState.aiInsightState) {
                                    AiInsightUiState.Idle -> {}
                                    AiInsightUiState.Loading -> {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text("Đang tổng hợp và phân tích dữ liệu lớp ${selectedClass.name}...")
                                        }
                                    }
                                    is AiInsightUiState.Streaming -> {
                                        Surface(
                                            color = MaterialTheme.colorScheme.surface,
                                            shape = MaterialTheme.shapes.medium,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = aiState.text,
                                                style = MaterialTheme.typography.bodyMedium,
                                                modifier = Modifier.padding(12.dp)
                                            )
                                        }
                                    }
                                    is AiInsightUiState.Success -> {
                                        Surface(
                                            color = MaterialTheme.colorScheme.surface,
                                            shape = MaterialTheme.shapes.medium,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("ai_insights_result")
                                        ) {
                                            Text(
                                                text = aiState.text,
                                                style = MaterialTheme.typography.bodyMedium,
                                                modifier = Modifier.padding(12.dp)
                                            )
                                        }
                                    }
                                    is AiInsightUiState.Error -> {
                                        Surface(
                                            color = MaterialTheme.colorScheme.errorContainer,
                                            shape = MaterialTheme.shapes.medium,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = aiState.message,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onErrorContainer,
                                                modifier = Modifier.padding(12.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Text(
                        text = "Chi tiết chuyên cần từng học sinh",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        if (uiState.classrooms.isNotEmpty()) {
            items(summary.studentSummaries, key = { it.student.id }) { stSummary ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .widthIn(max = 840.dp)
                        .fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stSummary.student.fullName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Mã HS: ${stSummary.student.studentCode}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                color = if (stSummary.isAtRisk) {
                                    StatusUnexcusedColor.copy(alpha = 0.14f)
                                } else {
                                    StatusPresentColor.copy(alpha = 0.14f)
                                },
                                shape = RoundedCornerShape(50)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    if (stSummary.isAtRisk) {
                                        Icon(
                                            imageVector = Icons.Rounded.WarningAmber,
                                            contentDescription = "Cảnh báo chuyên cần",
                                            tint = StatusUnexcusedColor,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    Text(
                                        text = "${"%.1f".format(stSummary.attendanceRatePercent)}%",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (stSummary.isAtRisk) StatusUnexcusedColor else StatusPresentColor
                                    )
                                }
                            }
                        }
                        Text(
                            text = "Có mặt: ${stSummary.presentCount}  •  Đi trễ: ${stSummary.lateCount}  •  Có phép: ${stSummary.excusedCount}  •  Không phép: ${stSummary.unexcusedCount}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AttendanceDonutChart(
    present: Int,
    late: Int,
    excused: Int,
    unexcused: Int,
    ratePercent: Double,
    modifier: Modifier = Modifier
) {
    val total = present + late + excused + unexcused
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 22.dp.toPx()
            if (total == 0) {
                drawArc(
                    color = trackColor,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            } else {
                val slices = listOf(
                    present to StatusPresentColor,
                    late to StatusLateColor,
                    excused to StatusExcusedColor,
                    unexcused to StatusUnexcusedColor
                )
                var startAngle = -90f
                slices.forEach { (count, color) ->
                    if (count > 0) {
                        val sweep = (count.toFloat() / total.toFloat()) * 360f
                        drawArc(
                            color = color,
                            startAngle = startAngle,
                            sweepAngle = sweep,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                        )
                        startAngle += sweep
                    }
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${"%.0f".format(ratePercent)}%",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Chuyên cần",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ChartLegendRow(label: String, count: Int, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
        Text(
            text = "$count",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun shiftYearMonth(current: String, deltaMonths: Int): String {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM", Locale.US)
        val date = sdf.parse(current) ?: return current
        val cal = Calendar.getInstance().apply {
            time = date
            add(Calendar.MONTH, deltaMonths)
        }
        sdf.format(cal.time)
    } catch (_: Exception) {
        current
    }
}
