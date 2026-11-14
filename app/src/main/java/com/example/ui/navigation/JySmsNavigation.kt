package com.example.ui.navigation

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CardMembership
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.core.InstitutionConfig
import com.example.domain.model.UserRole
import com.example.ui.components.JyLogoBadge
import com.example.ui.components.StatusPill
import com.example.ui.screens.AdminDashboardScreen
import com.example.ui.screens.AnnouncementsScreen
import com.example.ui.screens.AttendanceScreen
import com.example.ui.screens.CertificatesScreen
import com.example.ui.screens.ChatHubScreen
import com.example.ui.screens.ContactUsScreen
import com.example.ui.screens.CoursesScreen
import com.example.ui.screens.EnrolmentsScreen
import com.example.ui.screens.InstructorDashboardScreen
import com.example.ui.screens.PaymentsScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.screens.ResultsScreen
import com.example.ui.screens.SettingsAndContactScreen
import com.example.ui.screens.StudentHomeScreen
import com.example.ui.screens.StudentManagementScreen
import com.example.ui.viewmodel.MainSessionViewModel
import kotlinx.coroutines.launch

object AppRoute {
    const val DASHBOARD = "dashboard"
    const val STUDENTS = "students"
    const val COURSES = "courses"
    const val ENROLMENTS = "enrolments"
    const val ATTENDANCE = "attendance"
    const val RESULTS = "results"
    const val PAYMENTS = "payments"
    const val CERTIFICATES = "certificates"
    const val CHAT = "chat"
    const val REPORTS = "reports"
    const val ANNOUNCEMENTS = "announcements"
    const val SETTINGS = "settings"
    const val PROFILE = "profile"
    const val CONTACT = "contact"
}

private data class NavDestinationItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainSessionShell(
    viewModel: MainSessionViewModel,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val account by viewModel.userAccount.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val students by viewModel.students.collectAsStateWithLifecycle()
    val courses by viewModel.courses.collectAsStateWithLifecycle()
    val enrolments by viewModel.enrolments.collectAsStateWithLifecycle()
    val payments by viewModel.payments.collectAsStateWithLifecycle()
    val submissions by viewModel.paymentSubmissions.collectAsStateWithLifecycle()
    val attendance by viewModel.attendance.collectAsStateWithLifecycle()
    val attendanceSessions by viewModel.attendanceSessions.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val certificates by viewModel.certificates.collectAsStateWithLifecycle()
    val directory by viewModel.directory.collectAsStateWithLifecycle()
    val instructors by viewModel.instructors.collectAsStateWithLifecycle()
    val allUsers by viewModel.allUsers.collectAsStateWithLifecycle()
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val groupChats by viewModel.groupChats.collectAsStateWithLifecycle()
    val blockedUsers by viewModel.blockedUsers.collectAsStateWithLifecycle()
    val chatReports by viewModel.chatReports.collectAsStateWithLifecycle()
    val announcements by viewModel.announcements.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val activityLogs by viewModel.activityLogs.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
    val gatewayInit by viewModel.gatewayInit.collectAsStateWithLifecycle()
    val verifiedCert by viewModel.verifiedCert.collectAsStateWithLifecycle()
    val certLookupDone by viewModel.certLookupDone.collectAsStateWithLifecycle()

    LaunchedEffect(statusBanner) {
        val msg = statusBanner ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearBanner()
    }

    val role = account?.userRole ?: UserRole.STUDENT
    val unreadNotifCount = notifications.count { !it.isRead }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: AppRoute.DASHBOARD

    val navigateTo: (String) -> Unit = { route ->
        if (currentRoute != route) {
            navController.navigate(route) {
                popUpTo(AppRoute.DASHBOARD) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    val primaryTabs = remember(role) {
        when (role) {
            UserRole.ADMINISTRATOR -> listOf(
                NavDestinationItem(AppRoute.DASHBOARD, "Dashboard", Icons.Default.Dashboard),
                NavDestinationItem(AppRoute.STUDENTS, "Students", Icons.Default.People),
                NavDestinationItem(AppRoute.COURSES, "Courses", Icons.Default.MenuBook),
                NavDestinationItem(AppRoute.PAYMENTS, "Payments", Icons.Default.Payments),
                NavDestinationItem(AppRoute.CHAT, "Chat", Icons.AutoMirrored.Filled.Chat)
            )
            UserRole.INSTRUCTOR -> listOf(
                NavDestinationItem(AppRoute.DASHBOARD, "Dashboard", Icons.Default.Dashboard),
                NavDestinationItem(AppRoute.COURSES, "Courses", Icons.Default.MenuBook),
                NavDestinationItem(AppRoute.ATTENDANCE, "Attendance", Icons.Default.FactCheck),
                NavDestinationItem(AppRoute.RESULTS, "Results", Icons.Default.Assessment),
                NavDestinationItem(AppRoute.CHAT, "Chat", Icons.AutoMirrored.Filled.Chat)
            )
            UserRole.STUDENT -> listOf(
                NavDestinationItem(AppRoute.DASHBOARD, "Home", Icons.Default.Dashboard),
                NavDestinationItem(AppRoute.COURSES, "Courses", Icons.Default.MenuBook),
                NavDestinationItem(AppRoute.PAYMENTS, "Payments", Icons.Default.Payments),
                NavDestinationItem(AppRoute.CHAT, "Chat", Icons.AutoMirrored.Filled.Chat),
                NavDestinationItem(AppRoute.PROFILE, "Profile", Icons.Default.Person)
            )
        }
    }

    val drawerItems = remember(role) {
        when (role) {
            UserRole.ADMINISTRATOR -> listOf(
                NavDestinationItem(AppRoute.DASHBOARD, "Admin Dashboard", Icons.Default.Dashboard),
                NavDestinationItem(AppRoute.STUDENTS, "Student Records", Icons.Default.People),
                NavDestinationItem(AppRoute.COURSES, "Course Catalogue", Icons.Default.MenuBook),
                NavDestinationItem(AppRoute.ENROLMENTS, "Enrolments", Icons.Default.Badge),
                NavDestinationItem(AppRoute.PAYMENTS, "Payments & OPay", Icons.Default.Payments),
                NavDestinationItem(AppRoute.ATTENDANCE, "Attendance", Icons.Default.FactCheck),
                NavDestinationItem(AppRoute.RESULTS, "Academic Results", Icons.Default.Assessment),
                NavDestinationItem(AppRoute.CERTIFICATES, "Certificates", Icons.Default.CardMembership),
                NavDestinationItem(AppRoute.CHAT, "Messages & Moderation", Icons.AutoMirrored.Filled.Chat),
                NavDestinationItem(AppRoute.ANNOUNCEMENTS, "Announcements", Icons.Default.Campaign),
                NavDestinationItem(AppRoute.REPORTS, "Export Reports", Icons.Default.Summarize),
                NavDestinationItem(AppRoute.SETTINGS, "Settings & Contact", Icons.Default.Settings)
            )
            UserRole.INSTRUCTOR -> listOf(
                NavDestinationItem(AppRoute.DASHBOARD, "Instructor Home", Icons.Default.Dashboard),
                NavDestinationItem(AppRoute.COURSES, "Assigned Courses", Icons.Default.MenuBook),
                NavDestinationItem(AppRoute.ENROLMENTS, "Enrolled Students", Icons.Default.Badge),
                NavDestinationItem(AppRoute.ATTENDANCE, "Record Attendance", Icons.Default.FactCheck),
                NavDestinationItem(AppRoute.RESULTS, "Enter Results", Icons.Default.Assessment),
                NavDestinationItem(AppRoute.CHAT, "Course Chat", Icons.AutoMirrored.Filled.Chat),
                NavDestinationItem(AppRoute.ANNOUNCEMENTS, "Announcements", Icons.Default.Campaign),
                NavDestinationItem(AppRoute.SETTINGS, "Theme & Contact", Icons.Default.Settings)
            )
            UserRole.STUDENT -> listOf(
                NavDestinationItem(AppRoute.DASHBOARD, "Student Home", Icons.Default.Dashboard),
                NavDestinationItem(AppRoute.PROFILE, "My Registration", Icons.Default.Person),
                NavDestinationItem(AppRoute.COURSES, "Browse Courses", Icons.Default.MenuBook),
                NavDestinationItem(AppRoute.ENROLMENTS, "My Enrolments", Icons.Default.Badge),
                NavDestinationItem(AppRoute.PAYMENTS, "Pay Fees & Receipts", Icons.Default.Payments),
                NavDestinationItem(AppRoute.ATTENDANCE, "My Attendance", Icons.Default.FactCheck),
                NavDestinationItem(AppRoute.RESULTS, "My Results", Icons.Default.Assessment),
                NavDestinationItem(AppRoute.CERTIFICATES, "Certificates & Verify", Icons.Default.CardMembership),
                NavDestinationItem(AppRoute.CHAT, "Student Chat & Groups", Icons.AutoMirrored.Filled.Chat),
                NavDestinationItem(AppRoute.ANNOUNCEMENTS, "Notices & Alerts", Icons.Default.Campaign),
                NavDestinationItem(AppRoute.CONTACT, "Contact JY-COMPUTERS", Icons.Default.LocationOn),
                NavDestinationItem(AppRoute.SETTINGS, "Theme & Settings", Icons.Default.Settings)
            )
        }
    }

    val screenTitle = drawerItems.firstOrNull { it.route == currentRoute }?.label ?: InstitutionConfig.APP_SHORT_NAME

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        JyLogoBadge(sizeDp = 52)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(InstitutionConfig.APP_SHORT_NAME, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(account?.displayName ?: "Member", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(4.dp))
                            StatusPill(
                                text = role.name,
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    drawerItems.forEach { item ->
                        NavigationDrawerItem(
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(item.label) },
                            selected = currentRoute == item.route,
                            onClick = {
                                scope.launch { drawerState.close() }
                                navigateTo(item.route)
                            }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()
                    NavigationDrawerItem(
                        icon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        label = { Text("Sign Out", color = MaterialTheme.colorScheme.error) },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            onSignOut()
                        },
                        modifier = Modifier.testTag("sign_out_drawer_item")
                    )
                }
            }
        }
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useRail = maxWidth >= 840.dp

            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    TopAppBar(
                        title = { Text(screenTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        navigationIcon = {
                            IconButton(
                                onClick = { scope.launch { drawerState.open() } },
                                modifier = Modifier.testTag("drawer_menu_button")
                            ) {
                                Icon(Icons.Default.Menu, contentDescription = "Open navigation menu")
                            }
                        },
                        actions = {
                            IconButton(onClick = { navigateTo(AppRoute.ANNOUNCEMENTS) }) {
                                BadgedBox(
                                    badge = {
                                        if (unreadNotifCount > 0) {
                                            Badge { Text(unreadNotifCount.toString()) }
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.Notifications, contentDescription = "Notifications")
                                }
                            }
                            JyLogoBadge(sizeDp = 30)
                            Spacer(Modifier.width(12.dp))
                        }
                    )
                },
                bottomBar = {
                    if (!useRail) {
                        NavigationBar {
                            primaryTabs.forEach { tab ->
                                NavigationBarItem(
                                    selected = currentRoute == tab.route,
                                    onClick = { navigateTo(tab.route) },
                                    icon = { Icon(tab.icon, contentDescription = tab.label) },
                                    label = { Text(tab.label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
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
                    if (useRail) {
                        NavigationRail {
                            primaryTabs.forEach { tab ->
                                NavigationRailItem(
                                    selected = currentRoute == tab.route,
                                    onClick = { navigateTo(tab.route) },
                                    icon = { Icon(tab.icon, contentDescription = tab.label) },
                                    label = { Text(tab.label) }
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                        NavHost(
                            navController = navController,
                            startDestination = AppRoute.DASHBOARD
                        ) {
                            composable(AppRoute.DASHBOARD) {
                                when (role) {
                                    UserRole.ADMINISTRATOR -> AdminDashboardScreen(
                                        account = account,
                                        students = students,
                                        courses = courses,
                                        enrolments = enrolments,
                                        payments = payments,
                                        submissions = submissions,
                                        attendance = attendance,
                                        certificates = certificates,
                                        activityLogs = activityLogs,
                                        onNavigate = navigateTo
                                    )
                                    UserRole.INSTRUCTOR -> InstructorDashboardScreen(
                                        account = account,
                                        courses = courses,
                                        enrolments = enrolments,
                                        announcements = announcements,
                                        onNavigate = navigateTo
                                    )
                                    UserRole.STUDENT -> StudentHomeScreen(
                                        account = account,
                                        studentProfile = students.firstOrNull { it.userId == viewModel.currentUserId },
                                        enrolments = enrolments,
                                        certificates = certificates,
                                        announcements = announcements,
                                        onNavigate = navigateTo
                                    )
                                }
                            }

                            composable(AppRoute.STUDENTS) {
                                StudentManagementScreen(
                                    currentUid = viewModel.currentUserId,
                                    userAccount = account,
                                    students = students,
                                    courses = courses,
                                    enrolments = enrolments,
                                    onSaveStudent = viewModel::saveStudent,
                                    onUpdateStatus = viewModel::updateStudentStatus,
                                    onDeleteStudent = viewModel::deleteStudent,
                                    onExportPdf = { viewModel.exportStudents(context, asExcel = false) },
                                    onExportExcel = { viewModel.exportStudents(context, asExcel = true) }
                                )
                            }

                            composable(AppRoute.PROFILE) {
                                StudentManagementScreen(
                                    currentUid = viewModel.currentUserId,
                                    userAccount = account,
                                    students = students,
                                    courses = courses,
                                    enrolments = enrolments,
                                    onSaveStudent = viewModel::saveStudent,
                                    onUpdateStatus = viewModel::updateStudentStatus,
                                    onDeleteStudent = viewModel::deleteStudent,
                                    onExportPdf = { viewModel.exportStudents(context, asExcel = false) },
                                    onExportExcel = { viewModel.exportStudents(context, asExcel = true) }
                                )
                            }

                            composable(AppRoute.COURSES) {
                                CoursesScreen(
                                    currentUid = viewModel.currentUserId,
                                    userAccount = account,
                                    courses = courses,
                                    students = students,
                                    instructors = instructors,
                                    onSaveCourse = viewModel::saveCourse,
                                    onArchiveOrDeleteCourse = viewModel::archiveOrDeleteCourse,
                                    onEnrolStudent = viewModel::enrolStudentInCourse
                                )
                            }

                            composable(AppRoute.ENROLMENTS) {
                                EnrolmentsScreen(
                                    userAccount = account,
                                    enrolments = enrolments,
                                    onConfirmEnrolment = viewModel::confirmEnrolment,
                                    onUpdateCompletion = viewModel::updateCompletionStatus,
                                    onGoToPay = { navigateTo(AppRoute.PAYMENTS) }
                                )
                            }

                            composable(AppRoute.PAYMENTS) {
                                PaymentsScreen(
                                    userAccount = account,
                                    enrolments = enrolments,
                                    payments = payments,
                                    submissions = submissions,
                                    gatewayInit = gatewayInit,
                                    onClearGatewayInit = viewModel::clearGatewayInit,
                                    onInitiateOnlinePayment = viewModel::initiateOnlinePayment,
                                    onVerifyOnlinePayment = viewModel::verifyGatewayPaymentOnServer,
                                    onSubmitOpayTransfer = viewModel::submitOpayTransfer,
                                    onAdminVerifyOpay = viewModel::verifyOpaySubmission,
                                    onAdminRecordPayment = viewModel::recordVerifiedPayment,
                                    onExportFinancialPdf = { viewModel.exportFinancial(context, asExcel = false) },
                                    onExportFinancialExcel = { viewModel.exportFinancial(context, asExcel = true) },
                                    onShowMessage = viewModel::showBanner
                                )
                            }

                            composable(AppRoute.ATTENDANCE) {
                                AttendanceScreen(
                                    userAccount = account,
                                    courses = courses,
                                    enrolments = enrolments,
                                    attendance = attendance,
                                    attendanceSessions = attendanceSessions,
                                    onMarkAttendance = viewModel::recordAttendance,
                                    onGenerateQrSession = viewModel::generateQrAttendanceSession,
                                    onToggleQrSession = viewModel::toggleQrAttendanceSession,
                                    onSubmitQrAttendance = viewModel::markAttendanceFromQr,
                                    onExportPdf = { viewModel.exportAttendance(context, asExcel = false) },
                                    onExportExcel = { viewModel.exportAttendance(context, asExcel = true) }
                                )
                            }

                            composable(AppRoute.RESULTS) {
                                ResultsScreen(
                                    userAccount = account,
                                    courses = courses,
                                    enrolments = enrolments,
                                    results = results,
                                    onSaveResult = viewModel::saveAcademicResult,
                                    onExportPdf = { viewModel.exportResults(context, asExcel = false) },
                                    onExportExcel = { viewModel.exportResults(context, asExcel = true) }
                                )
                            }

                            composable(AppRoute.CERTIFICATES) {
                                CertificatesScreen(
                                    userAccount = account,
                                    enrolments = enrolments,
                                    certificates = certificates,
                                    verifiedCert = verifiedCert,
                                    certLookupDone = certLookupDone,
                                    onIssueCertificate = viewModel::issueCertificate,
                                    onVerifyCertificate = viewModel::verifyCertificate
                                )
                            }

                            composable(AppRoute.CHAT) {
                                ChatHubScreen(
                                    currentUid = viewModel.currentUserId,
                                    userAccount = account,
                                    directory = directory,
                                    conversations = conversations,
                                    groupChats = groupChats,
                                    blockedUsers = blockedUsers,
                                    chatReports = chatReports,
                                    observeDirectMessages = viewModel::observeDirectMessages,
                                    observeGroupMessages = viewModel::observeGroupMessages,
                                    onStartConversation = viewModel::startOrOpenConversation,
                                    onSendDirectMessage = viewModel::sendDirectMessage,
                                    onSendGroupMessage = viewModel::sendGroupMessage,
                                    onMarkRead = viewModel::markConversationRead,
                                    onUpdateTyping = viewModel::updateTyping,
                                    onRemoveGroupMessage = viewModel::removeGroupMessage,
                                    onBlockUser = viewModel::blockUser,
                                    onReportMessage = viewModel::reportMessage,
                                    onResolveReport = viewModel::resolveReport,
                                    onToggleSuspendUser = viewModel::toggleUserSuspension
                                )
                            }

                            composable(AppRoute.ANNOUNCEMENTS) {
                                AnnouncementsScreen(
                                    userAccount = account,
                                    courses = courses,
                                    announcements = announcements,
                                    notifications = notifications,
                                    preferences = preferences,
                                    onPublishAnnouncement = viewModel::publishAnnouncement,
                                    onMarkNotificationRead = viewModel::markNotificationRead,
                                    onUpdateNotificationPrefs = viewModel::updateNotificationPrefs
                                )
                            }

                            composable(AppRoute.REPORTS) {
                                ReportsScreen(
                                    onExportStudents = { viewModel.exportStudents(context, it) },
                                    onExportEnrolments = { viewModel.exportEnrolments(context, it) },
                                    onExportAttendance = { viewModel.exportAttendance(context, it) },
                                    onExportResults = { viewModel.exportResults(context, it) },
                                    onExportFinancial = { excel, outstanding -> viewModel.exportFinancial(context, excel, outstanding) },
                                    onExportGraduation = { viewModel.exportGraduation(context, it) }
                                )
                            }

                            composable(AppRoute.SETTINGS) {
                                SettingsAndContactScreen(
                                    userAccount = account,
                                    preferences = preferences,
                                    allUsers = allUsers,
                                    instructors = instructors,
                                    onSetDarkMode = viewModel::setDarkMode,
                                    onSetPaymentBackendUrl = viewModel::setPaymentBackendUrl,
                                    onAssignRole = viewModel::assignUserRole,
                                    onToggleSuspend = viewModel::toggleUserSuspension,
                                    onAddInstructor = viewModel::addInstructor
                                )
                            }

                            composable(AppRoute.CONTACT) {
                                ContactUsScreen()
                            }
                        }
                    }
                }
            }
        }
    }
}
