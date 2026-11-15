package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Class
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.repository.AiInsightsRepository
import com.example.data.repository.AttendanceRepository
import com.example.data.repository.NetworkMonitor
import com.example.data.repository.authStateFlow
import com.example.data.repository.signOut
import com.example.firebase.configureAppCheck
import com.example.ui.components.SyncStatusBanner
import com.example.ui.screens.AdminAndSettingsScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ClassesAndStudentsScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.SessionsAndScannerScreen
import com.example.ui.screens.StatisticsAndExportScreen
import com.example.ui.theme.QrAttendanceTheme
import com.example.ui.viewmodel.MainViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

private enum class AppDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val testTag: String
) {
    DASHBOARD("tong_quan", "Tổng quan", Icons.Rounded.Dashboard, "nav_dashboard"),
    CLASSES("lop_hoc", "Lớp học", Icons.Rounded.Class, "nav_classes"),
    SESSIONS("diem_danh", "Điểm danh", Icons.Rounded.QrCodeScanner, "nav_sessions"),
    STATS("thong_ke", "Thống kê", Icons.Rounded.BarChart, "nav_stats"),
    SETTINGS("cai_dat", "Cài đặt", Icons.Rounded.Settings, "nav_settings")
}

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialize App Check synchronously before any UI or Firebase SDK calls
        configureAppCheck(this, intent)
        enableEdgeToEdge()

        setContent {
            val widthSizeClass = calculateWindowSizeClass(this).widthSizeClass
            QrAttendanceTheme {
                val currentUser by Firebase.auth.authStateFlow()
                    .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

                if (currentUser == null) {
                    AuthScreen(onAuthSuccess = {})
                } else {
                    val uid = currentUser!!.uid
                    key(uid) {
                        MainAppShell(
                            currentUserId = uid,
                            widthSizeClass = widthSizeClass
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainAppShell(
    currentUserId: String,
    widthSizeClass: WindowWidthSizeClass
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: MainViewModel = viewModel(
        key = "main_vm_$currentUserId",
        factory = remember(currentUserId, appContext) {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MainViewModel(
                        repository = AttendanceRepository(appContext),
                        aiRepository = AiInsightsRepository(),
                        networkMonitor = NetworkMonitor(appContext),
                        currentUserId = currentUserId
                    ) as T
                }
            }
        }
    )

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: AppDestination.DASHBOARD.route

    var activeScannerSessionId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(uiState.snackbarMessage) {
        val msg = uiState.snackbarMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeSnackbar()
        }
    }

    fun navigateTo(destination: AppDestination) {
        activeScannerSessionId = null
        navController.navigate(destination.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    if (uiState.isProfileLoading) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator()
                Text(
                    text = "Đang đồng bộ hồ sơ giáo viên...",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        return
    }

    if (!uiState.profileError.isNullOrBlank()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Không thể khởi tạo phiên làm việc",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = uiState.profileError.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { viewModel.bootstrapUserSession() }) {
                        Text("Thử lại")
                    }
                    OutlinedButton(onClick = { signOut(credentialManager, scope) }) {
                        Text("Đăng xuất")
                    }
                }
            }
        }
        return
    }

    if (!uiState.isActiveAccount) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Tài khoản đã bị tạm khóa",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = "Vui lòng liên hệ Quản trị viên nhà trường để kích hoạt lại tài khoản giáo viên của bạn.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { signOut(credentialManager, scope) }) {
                    Text("Đăng xuất")
                }
            }
        }
        return
    }

    val useNavigationRail = widthSizeClass != WindowWidthSizeClass.Compact && activeScannerSessionId == null

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            if (activeScannerSessionId == null) {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.QrCodeScanner,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = "QUẢN LÝ LỚP BẰNG MÃ QR",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Text(
                                    text = "PP HẬU • ĐIỂM DANH THÔNG MINH",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    },
                    actions = {
                        SyncStatusBanner(
                            isOnline = uiState.isOnline,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        },
        bottomBar = {
            if (!useNavigationRail && activeScannerSessionId == null) {
                NavigationBar {
                    AppDestination.entries.forEach { dest ->
                        NavigationBarItem(
                            selected = currentRoute == dest.route,
                            onClick = { navigateTo(dest) },
                            icon = { Icon(dest.icon, contentDescription = dest.label) },
                            label = { Text(dest.label, maxLines = 1) },
                            modifier = Modifier.testTag(dest.testTag)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (useNavigationRail) {
                NavigationRail {
                    AppDestination.entries.forEach { dest ->
                        NavigationRailItem(
                            selected = currentRoute == dest.route,
                            onClick = { navigateTo(dest) },
                            icon = { Icon(dest.icon, contentDescription = dest.label) },
                            label = { Text(dest.label) },
                            modifier = Modifier.testTag(dest.testTag)
                        )
                    }
                }
            }

            NavHost(
                navController = navController,
                startDestination = AppDestination.DASHBOARD.route,
                modifier = Modifier.weight(1f)
            ) {
                composable(AppDestination.DASHBOARD.route) {
                    DashboardScreen(
                        uiState = uiState,
                        onOpenScannerForSession = { sessionId ->
                            activeScannerSessionId = sessionId
                            navController.navigate(AppDestination.SESSIONS.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onGoToClasses = { navigateTo(AppDestination.CLASSES) },
                        onGoToSessions = { navigateTo(AppDestination.SESSIONS) },
                        onGoToStats = { navigateTo(AppDestination.STATS) }
                    )
                }

                composable(AppDestination.CLASSES.route) {
                    ClassesAndStudentsScreen(
                        uiState = uiState,
                        onSelectSchoolYear = viewModel::selectSchoolYear,
                        onSelectClass = viewModel::selectClass,
                        onCreateSchoolYear = viewModel::createSchoolYear,
                        onCreateClassroom = viewModel::createClassroom,
                        onUpdateClassTeachers = viewModel::updateClassroomTeachers,
                        onDeleteClassroom = viewModel::deleteClassroom,
                        onCreateStudent = viewModel::createStudent,
                        onUpdateStudent = viewModel::updateStudent,
                        onRegenerateStudentQr = viewModel::regenerateStudentQr,
                        onDeleteStudent = viewModel::deleteStudent
                    )
                }

                composable(AppDestination.SESSIONS.route) {
                    SessionsAndScannerScreen(
                        uiState = uiState,
                        activeScannerSessionId = activeScannerSessionId,
                        onOpenScanner = { sessionId ->
                            viewModel.clearScanFeedback()
                            activeScannerSessionId = sessionId
                        },
                        onCloseScanner = {
                            viewModel.clearScanFeedback()
                            activeScannerSessionId = null
                        },
                        onCreateSession = viewModel::createSession,
                        onCloseSession = viewModel::closeSessionAndMarkAbsent,
                        onReopenSession = viewModel::reopenSession,
                        onQrScanned = viewModel::processScannedQr,
                        onClearScanFeedback = viewModel::clearScanFeedback,
                        onEditAttendance = viewModel::editAttendanceWithAuditLog
                    )
                }

                composable(AppDestination.STATS.route) {
                    StatisticsAndExportScreen(
                        uiState = uiState,
                        onSelectClass = viewModel::selectClass,
                        onGenerateAiInsights = viewModel::generateAiAttendanceInsights
                    )
                }

                composable(AppDestination.SETTINGS.route) {
                    AdminAndSettingsScreen(
                        uiState = uiState,
                        onUpdateOwnProfile = viewModel::updateOwnProfile,
                        onCreateTeacherInvite = viewModel::createTeacherInvite,
                        onDeleteTeacherInvite = viewModel::deleteTeacherInvite,
                        onSetTeacherRole = viewModel::setTeacherRole,
                        onSetTeacherActive = viewModel::setTeacherActive,
                        onCreateSchoolYear = viewModel::createSchoolYear,
                        onDeleteSchoolYear = viewModel::deleteSchoolYear,
                        onSignOut = { signOut(credentialManager, scope) }
                    )
                }
            }
        }
    }
}
