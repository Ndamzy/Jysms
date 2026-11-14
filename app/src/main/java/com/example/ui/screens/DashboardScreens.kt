package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CardMembership
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.Formatters
import com.example.core.InstitutionConfig
import com.example.domain.model.ActivityLog
import com.example.domain.model.Announcement
import com.example.domain.model.AttendanceRecord
import com.example.domain.model.AttendanceStatus
import com.example.domain.model.Certificate
import com.example.domain.model.CompletionStatus
import com.example.domain.model.Course
import com.example.domain.model.Enrolment
import com.example.domain.model.EnrolmentStatus
import com.example.domain.model.Payment
import com.example.domain.model.PaymentSubmission
import com.example.domain.model.PaymentTxStatus
import com.example.domain.model.Student
import com.example.domain.model.StudentStatus
import com.example.domain.model.SubmissionStatus
import com.example.domain.model.UserAccount
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.JyLogoBadge
import com.example.ui.components.KpiStatCard
import com.example.ui.components.SimpleBarChartCard
import com.example.ui.components.StatusPill
import com.example.ui.navigation.AppRoute
import com.example.ui.theme.JyEmerald
import com.example.ui.theme.JyGold
import com.example.ui.theme.JyNavy
import com.example.ui.theme.JyNavyLight
import com.example.ui.theme.StatusInfo
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning

@Composable
fun AdminDashboardScreen(
    account: UserAccount?,
    students: List<Student>,
    courses: List<Course>,
    enrolments: List<Enrolment>,
    payments: List<Payment>,
    submissions: List<PaymentSubmission>,
    attendance: List<AttendanceRecord>,
    certificates: List<Certificate>,
    activityLogs: List<ActivityLog>,
    onNavigate: (String) -> Unit
) {
    val activeStudents = students.count { it.status == StudentStatus.ACTIVE.name }
    val inactiveStudents = students.count { it.status == StudentStatus.INACTIVE.name }
    val graduatedStudents = students.count { it.status == StudentStatus.GRADUATED.name } +
        certificates.map { it.studentId }.distinct().size
    val activeEnrolments = enrolments.count { it.enrolmentStatus == EnrolmentStatus.CONFIRMED.name }
    val completedCourses = enrolments.count { it.completionStatus == CompletionStatus.COMPLETED.name }
    val totalRevenue = payments.filter { it.status == PaymentTxStatus.SUCCESSFUL.name }.sumOf { it.amountNgn }
    val outstandingBalance = enrolments.sumOf { it.balanceNgn }
    val pendingOpay = submissions.count { it.status == SubmissionStatus.PENDING_VERIFICATION.name }

    val coursePopularity = courses.take(5).map { course ->
        val count = enrolments.count { it.courseId == course.id }.toFloat()
        course.code to count
    }

    val attendanceSummary = listOf(
        "Present" to attendance.count { it.status == AttendanceStatus.PRESENT.name }.toFloat(),
        "Late" to attendance.count { it.status == AttendanceStatus.LATE.name }.toFloat(),
        "Excused" to attendance.count { it.status == AttendanceStatus.EXCUSED.name }.toFloat(),
        "Absent" to attendance.count { it.status == AttendanceStatus.ABSENT.name }.toFloat()
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = JyNavy)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Image(
                        painter = painterResource(id = R.drawable.img_campus_hero),
                        contentDescription = null,
                        modifier = Modifier
                            .matchParentSize()
                            .alpha(0.18f),
                        contentScale = ContentScale.Crop
                    )
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        JyLogoBadge(sizeDp = 56)
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Administrator Command",
                                style = MaterialTheme.typography.labelMedium,
                                color = JyEmerald
                            )
                            Text(
                                text = account?.displayName ?: "JY-COMPUTERS Admin",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${students.size} students • ${courses.size} courses • Abuja Campus",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        }

        if (pendingOpay > 0) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigate(AppRoute.PAYMENTS) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.PendingActions, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "$pendingOpay OPay Transfer(s) Pending Verification",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                "Tap to verify bank transfer references & update balances.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                KpiStatCard(
                    title = "Total Revenue",
                    value = Formatters.formatNgn(totalRevenue),
                    subtitle = "${payments.count { it.status == PaymentTxStatus.SUCCESSFUL.name }} tx",
                    icon = Icons.Default.AccountBalance,
                    accentColor = StatusSuccess,
                    modifier = Modifier.weight(1f)
                )
                KpiStatCard(
                    title = "Outstanding Fees",
                    value = Formatters.formatNgn(outstandingBalance),
                    subtitle = "$pendingOpay pending",
                    icon = Icons.Default.Warning,
                    accentColor = StatusWarning,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                KpiStatCard(
                    title = "Total Students",
                    value = students.size.toString(),
                    subtitle = "$activeStudents active / $inactiveStudents inactive",
                    icon = Icons.Default.People,
                    accentColor = StatusInfo,
                    modifier = Modifier.weight(1f)
                )
                KpiStatCard(
                    title = "Active Enrolments",
                    value = activeEnrolments.toString(),
                    subtitle = "$completedCourses completed",
                    icon = Icons.Default.School,
                    accentColor = JyEmerald,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                KpiStatCard(
                    title = "Total Courses",
                    value = courses.size.toString(),
                    subtitle = "Abuja NGN",
                    icon = Icons.Default.MenuBook,
                    accentColor = JyNavy,
                    modifier = Modifier.weight(1f)
                )
                KpiStatCard(
                    title = "Graduated",
                    value = graduatedStudents.toString(),
                    subtitle = "${certificates.size} certs",
                    icon = Icons.Default.CardMembership,
                    accentColor = JyGold,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Text("Quick Actions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val actions = listOf(
                    Triple("Students", Icons.Default.People, AppRoute.STUDENTS),
                    Triple("Courses", Icons.Default.MenuBook, AppRoute.COURSES),
                    Triple("Enrolments", Icons.Default.Badge, AppRoute.ENROLMENTS),
                    Triple("Payments", Icons.Default.Payments, AppRoute.PAYMENTS),
                    Triple("Attendance", Icons.Default.FactCheck, AppRoute.ATTENDANCE),
                    Triple("Results", Icons.Default.Assessment, AppRoute.RESULTS),
                    Triple("Certificates", Icons.Default.CardMembership, AppRoute.CERTIFICATES),
                    Triple("Reports", Icons.Default.CheckCircle, AppRoute.REPORTS)
                )
                items(actions) { (label, icon, route) ->
                    FilledTonalButton(onClick = { onNavigate(route) }) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(label)
                    }
                }
            }
        }

        item {
            SimpleBarChartCard(
                title = "Course Popularity",
                subtitle = "Live enrolment count across training programmes",
                entries = coursePopularity,
                barColor = JyEmerald
            )
        }

        item {
            SimpleBarChartCard(
                title = "Attendance Breakdown",
                subtitle = "Recorded daily attendance across all courses",
                entries = attendanceSummary,
                barColor = JyNavyLight
            )
        }

        item {
            Text("Recent Activity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        if (activityLogs.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Default.Campaign,
                    title = "No Activity Logged Yet",
                    subtitle = "Administrative actions, registrations, and payment verifications appear here."
                )
            }
        } else {
            items(activityLogs.take(8), key = { it.id }) { log ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusPill(
                            text = log.targetType,
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(log.action, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${log.actorName} • ${log.details}",
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

@Composable
fun InstructorDashboardScreen(
    account: UserAccount?,
    courses: List<Course>,
    enrolments: List<Enrolment>,
    announcements: List<Announcement>,
    onNavigate: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = JyNavy)
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    JyLogoBadge(sizeDp = 52)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("Instructor Workspace", style = MaterialTheme.typography.labelMedium, color = JyEmerald)
                        Text(
                            account?.displayName ?: "Instructor",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${courses.size} courses • ${enrolments.size} active student enrolments",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                QuickNavTile("Record Attendance", Icons.Default.FactCheck, Modifier.weight(1f)) {
                    onNavigate(AppRoute.ATTENDANCE)
                }
                QuickNavTile("Enter Results", Icons.Default.Assessment, Modifier.weight(1f)) {
                    onNavigate(AppRoute.RESULTS)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                QuickNavTile("Course Chat", Icons.AutoMirrored.Filled.Chat, Modifier.weight(1f)) {
                    onNavigate(AppRoute.CHAT)
                }
                QuickNavTile("Announcements", Icons.Default.Campaign, Modifier.weight(1f)) {
                    onNavigate(AppRoute.ANNOUNCEMENTS)
                }
            }
        }

        item {
            Text("Latest Announcements", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        if (announcements.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Default.Campaign,
                    title = "No Announcements Yet",
                    subtitle = "Post course updates or schedules for your students."
                )
            }
        } else {
            items(announcements.take(4), key = { it.id }) { ann ->
                AnnouncementCard(ann)
            }
        }
    }
}

@Composable
fun StudentHomeScreen(
    account: UserAccount?,
    studentProfile: Student?,
    enrolments: List<Enrolment>,
    certificates: List<Certificate>,
    announcements: List<Announcement>,
    onNavigate: (String) -> Unit
) {
    val totalFees = enrolments.sumOf { it.courseFeeNgn }
    val totalPaid = enrolments.sumOf { it.amountPaidNgn }
    val outstanding = (totalFees - totalPaid).coerceAtLeast(0L)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = JyNavy)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.horizontalGradient(listOf(JyNavy, JyNavyLight)))
                        .padding(18.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        JyLogoBadge(sizeDp = 56)
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = InstitutionConfig.INSTITUTION_NAME,
                                style = MaterialTheme.typography.labelMedium,
                                color = JyEmerald
                            )
                            Text(
                                text = studentProfile?.fullName ?: account?.displayName ?: "Welcome Student",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = studentProfile?.studentCode?.let { "Student ID: $it" }
                                    ?: "Complete registration to get your JY Student ID",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }
        }

        if (studentProfile == null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Complete Your Student Registration",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Enter your biodata, passport photo, and next-of-kin details before enrolling in courses.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = { onNavigate(AppRoute.PROFILE) }) {
                            Text("Register Now")
                        }
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                KpiStatCard(
                    title = "My Courses",
                    value = enrolments.size.toString(),
                    subtitle = "${certificates.size} certs",
                    icon = Icons.Default.School,
                    accentColor = JyEmerald,
                    modifier = Modifier.weight(1f)
                )
                KpiStatCard(
                    title = "Fee Balance",
                    value = Formatters.formatNgn(outstanding),
                    subtitle = "Paid ${Formatters.formatNgn(totalPaid)}",
                    icon = Icons.Default.Payments,
                    accentColor = if (outstanding == 0L) StatusSuccess else StatusWarning,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                QuickNavTile("Scan QR Attendance", Icons.Default.QrCodeScanner, Modifier.weight(1f)) {
                    onNavigate(AppRoute.ATTENDANCE)
                }
                QuickNavTile("My Results", Icons.Default.Assessment, Modifier.weight(1f)) {
                    onNavigate(AppRoute.RESULTS)
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                QuickNavTile("Browse Courses", Icons.Default.MenuBook, Modifier.weight(1f)) {
                    onNavigate(AppRoute.COURSES)
                }
                QuickNavTile("Certificates", Icons.Default.CardMembership, Modifier.weight(1f)) {
                    onNavigate(AppRoute.CERTIFICATES)
                }
            }
        }

        item {
            Text("Campus Announcements", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        if (announcements.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Default.Campaign,
                    title = "No Announcements",
                    subtitle = "Updates on Abuja training cohorts, exams, and certificates will appear here."
                )
            }
        } else {
            items(announcements.take(5), key = { it.id }) { ann ->
                AnnouncementCard(ann)
            }
        }
    }
}

@Composable
private fun QuickNavTile(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun AnnouncementCard(ann: Announcement) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(ann.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                StatusPill(
                    text = ann.targetAudience,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(ann.body, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "By ${ann.authorName.ifBlank { "JY-COMPUTERS" }} • ${Formatters.formatTimestamp(ann.createdAt?.toDate())}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
