package com.example.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Class
import androidx.compose.material.icons.rounded.EventNote
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.AttendanceStatus
import com.example.domain.model.SessionStatus
import com.example.domain.model.StatsCalculator
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.KpiMetricCard
import com.example.ui.components.SyncStatusBanner
import com.example.ui.theme.StatusLateColor
import com.example.ui.theme.StatusPresentColor
import com.example.ui.viewmodel.MainUiState

@Composable
fun DashboardScreen(
    uiState: MainUiState,
    onOpenScannerForSession: (String) -> Unit,
    onGoToClasses: () -> Unit,
    onGoToSessions: () -> Unit,
    onGoToStats: () -> Unit
) {
    val openSessions = uiState.sessions.filter { it.sessionStatus == SessionStatus.OPEN }
    val totalRecords = uiState.records.size
    val attendedRecords = uiState.records.count {
        it.attendanceStatus == AttendanceStatus.PRESENT || it.attendanceStatus == AttendanceStatus.LATE
    }
    val overallRate = if (totalRecords == 0) 100.0 else (attendedRecords * 100.0) / totalRecords

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Column(
                modifier = Modifier.widthIn(max = 840.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Welcome Hero Card
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    ),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Xin chào, ${uiState.profile?.displayName ?: "Giáo viên"}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(50)
                            ) {
                                Text(
                                    text = uiState.profile?.userRole?.labelVi ?: "Giáo viên",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Text(
                            text = "QUẢN LÝ LỚP BẰNG MÃ QR - PP HẬU",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        val activeYear = uiState.schoolYears.firstOrNull { it.id == uiState.selectedSchoolYearId }
                        Text(
                            text = if (activeYear != null) "Năm học: ${activeYear.name} • Điểm danh chuẩn hóa & Phân tích AI"
                            else "Hệ thống điểm danh học sinh THPT thông minh",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 2x2 KPI Grid
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    KpiMetricCard(
                        title = "Lớp phụ trách",
                        value = "${uiState.classrooms.size}",
                        subtitle = uiState.schoolYears.firstOrNull { it.id == uiState.selectedSchoolYearId }?.name
                            ?: "Chưa có năm học",
                        icon = Icons.Rounded.Class,
                        accentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    KpiMetricCard(
                        title = "Học sinh",
                        value = "${uiState.students.size}",
                        subtitle = "Đã cấp mã QR riêng",
                        icon = Icons.Rounded.Groups,
                        accentColor = StatusPresentColor,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    KpiMetricCard(
                        title = "Buổi học",
                        value = "${uiState.sessions.size}",
                        subtitle = "${openSessions.size} buổi đang mở quét QR",
                        icon = Icons.Rounded.EventNote,
                        accentColor = StatusLateColor,
                        modifier = Modifier.weight(1f)
                    )
                    KpiMetricCard(
                        title = "Chuyên cần",
                        value = "${"%.1f".format(overallRate)}%",
                        subtitle = "$totalRecords lượt ghi nhận",
                        icon = Icons.Rounded.BarChart,
                        accentColor = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Quick action row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = onGoToSessions,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dashboard_create_session_button")
                    ) {
                        Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Mở buổi điểm danh")
                    }
                    OutlinedButton(
                        onClick = onGoToStats,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dashboard_stats_button")
                    ) {
                        Icon(Icons.Rounded.BarChart, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Thống kê & AI")
                    }
                }
            }
        }

        item {
            Column(modifier = Modifier.widthIn(max = 840.dp)) {
                Text(
                    text = "Buổi học đang mở điểm danh",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        if (openSessions.isEmpty()) {
            item {
                Column(modifier = Modifier.widthIn(max = 840.dp)) {
                    EmptyStateCard(
                        icon = Icons.Rounded.QrCodeScanner,
                        title = "Chưa có buổi học đang mở",
                        description = if (uiState.classrooms.isEmpty()) {
                            "Hãy tạo năm học, lớp học và thêm học sinh trước khi bắt đầu quét QR điểm danh."
                        } else {
                            "Tạo một buổi học mới để bật camera quét mã QR điểm danh học sinh."
                        },
                        actionLabel = if (uiState.classrooms.isEmpty()) "Quản lý lớp & học sinh" else "Tạo buổi học ngay",
                        onAction = if (uiState.classrooms.isEmpty()) onGoToClasses else onGoToSessions
                    )
                }
            }
        } else {
            items(openSessions.take(4), key = { it.id }) { session ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Lớp ${session.className} • ${session.subject}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${session.periodLabel} • ${session.sessionDate} • Có mặt: ${session.presentCount} | Trễ: ${session.lateCount}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Button(
                            onClick = { onOpenScannerForSession(session.id) },
                            modifier = Modifier.testTag("quick_scan_${session.id}")
                        ) {
                            Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Quét QR")
                        }
                    }
                }
            }
        }

        item {
            Column(modifier = Modifier.widthIn(max = 840.dp)) {
                Text(
                    text = "Nhật ký chỉnh sửa gần đây",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        if (uiState.auditLogs.isEmpty()) {
            item {
                Column(modifier = Modifier.widthIn(max = 840.dp)) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Chưa có chỉnh sửa điểm danh nào. Mọi thay đổi thủ công đều được lưu vết đầy đủ.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        } else {
            items(uiState.auditLogs.take(5), key = { it.id }) { log ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "${log.studentName} (Lớp ${log.className}): ${log.previous.labelVi} → ${log.updated.labelVi}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Lý do: ${log.reason} • Bởi: ${log.editorName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
