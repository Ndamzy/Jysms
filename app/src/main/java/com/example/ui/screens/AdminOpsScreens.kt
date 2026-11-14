package com.example.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.Formatters
import com.example.core.InstitutionConfig
import com.example.data.local.UserPreferences
import com.example.domain.model.Announcement
import com.example.domain.model.AppNotification
import com.example.domain.model.Course
import com.example.domain.model.Instructor
import com.example.domain.model.UserAccount
import com.example.domain.model.UserRole
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.JyLogoBadge
import com.example.ui.components.StatusPill
import com.example.ui.theme.JyEmerald
import com.example.ui.theme.StatusInfo
import com.example.ui.theme.StatusSuccess

@Composable
fun AnnouncementsScreen(
    userAccount: UserAccount?,
    courses: List<Course>,
    announcements: List<Announcement>,
    notifications: List<AppNotification>,
    preferences: UserPreferences,
    onPublishAnnouncement: (String, String, String, String) -> Unit,
    onMarkNotificationRead: (String) -> Unit,
    onUpdateNotificationPrefs: (Boolean, Boolean, Boolean, Boolean) -> Unit
) {
    val role = userAccount?.userRole ?: UserRole.STUDENT
    val canPublish = role == UserRole.ADMINISTRATOR || role == UserRole.INSTRUCTOR
    var showPublish by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            if (canPublish) {
                FloatingActionButton(
                    onClick = { showPublish = true },
                    modifier = Modifier.testTag("publish_announcement_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Publish announcement")
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("Official Announcements", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            if (announcements.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.Campaign,
                        title = "No Announcements Published",
                        subtitle = "Training schedules, holiday notices, and campus announcements appear here."
                    )
                }
            } else {
                items(announcements, key = { it.id }) { ann ->
                    AnnouncementCard(ann)
                }
            }

            item {
                Text("My Alerts (${notifications.count { !it.isRead }} unread)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            if (notifications.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.Notifications,
                        title = "No Personal Alerts",
                        subtitle = "Fee receipts, enrolment confirmations, and academic alerts appear here."
                    )
                }
            } else {
                items(notifications, key = { it.id }) { notif ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { if (!notif.isRead) onMarkNotificationRead(notif.id) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (notif.isRead) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(notif.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                StatusPill(
                                    text = if (notif.isRead) "READ" else "NEW",
                                    containerColor = if (notif.isRead) MaterialTheme.colorScheme.surfaceVariant else JyEmerald,
                                    contentColor = if (notif.isRead) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSecondary
                                )
                            }
                            Text(notif.body, style = MaterialTheme.typography.bodySmall)
                            Text(Formatters.formatTimestamp(notif.createdAt?.toDate()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            item {
                var msgPref by remember(preferences) { mutableStateOf(preferences.notifyMessages) }
                var payPref by remember(preferences) { mutableStateOf(preferences.notifyPayments) }
                var acadPref by remember(preferences) { mutableStateOf(preferences.notifyAcademics) }
                var annPref by remember(preferences) { mutableStateOf(preferences.notifyAnnouncements) }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Notification Preferences", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        PrefSwitchRow("Chat & Direct Messages", msgPref) { msgPref = it }
                        PrefSwitchRow("Payments & Receipts", payPref) { payPref = it }
                        PrefSwitchRow("Attendance, Results & Certificates", acadPref) { acadPref = it }
                        PrefSwitchRow("School Announcements", annPref) { annPref = it }
                        Button(
                            onClick = { onUpdateNotificationPrefs(msgPref, payPref, acadPref, annPref) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Save Notification Preferences")
                        }
                    }
                }
            }
        }
    }

    if (showPublish) {
        var title by remember { mutableStateOf("") }
        var body by remember { mutableStateOf("") }
        var audience by remember { mutableStateOf("ALL") }
        var selectedCourseId by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showPublish = false },
            title = { Text("Publish Announcement") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Headline") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = body, onValueChange = { body = it }, label = { Text("Announcement Message") }, modifier = Modifier.fillMaxWidth())
                    Text("Audience:", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("ALL", "STUDENTS", "INSTRUCTORS", "COURSE").forEach { aud ->
                            FilterChip(selected = audience == aud, onClick = { audience = aud }, label = { Text(aud) })
                        }
                    }
                    if (audience == "COURSE" && courses.isNotEmpty()) {
                        courses.take(4).forEach { crs ->
                            FilterChip(
                                selected = selectedCourseId == crs.id,
                                onClick = { selectedCourseId = crs.id },
                                label = { Text("${crs.code} - ${crs.title}") }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onPublishAnnouncement(title, body, audience, selectedCourseId)
                        showPublish = false
                    },
                    enabled = title.isNotBlank() && body.isNotBlank()
                ) { Text("Publish") }
            },
            dismissButton = {
                TextButton(onClick = { showPublish = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun PrefSwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun ReportsScreen(
    onExportStudents: (Boolean) -> Unit,
    onExportEnrolments: (Boolean) -> Unit,
    onExportAttendance: (Boolean) -> Unit,
    onExportResults: (Boolean) -> Unit,
    onExportFinancial: (Boolean, Boolean) -> Unit,
    onExportGraduation: (Boolean) -> Unit
) {
    val reportItems = listOf(
        Triple("Student Registration Report", "All registered student biodata, IDs & contact details") { asExcel: Boolean -> onExportStudents(asExcel) },
        Triple("Course Enrolment Report", "Course intake, plans, completion & enrolment statuses") { asExcel: Boolean -> onExportEnrolments(asExcel) },
        Triple("Attendance Report", "Daily attendance status records across courses") { asExcel: Boolean -> onExportAttendance(asExcel) },
        Triple("Academic Results Report", "CA (40), Exam (60), Total Score, Grades & Remarks") { asExcel: Boolean -> onExportResults(asExcel) },
        Triple("Financial Payment Report", "Verified NGN receipts, Paystack, Flutterwave & OPay transfers") { asExcel: Boolean -> onExportFinancial(asExcel, false) },
        Triple("Outstanding Payments Report", "Enrolments with unpaid fee balances in NGN") { asExcel: Boolean -> onExportFinancial(asExcel, true) },
        Triple("Graduation & Certificates Report", "Issued certificate numbers, dates & awarded grades") { asExcel: Boolean -> onExportGraduation(asExcel) }
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Assessment, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("JY-COMPUTERS Analytics & Export Center", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Generate printable PDF reports or Excel-ready CSV spreadsheets.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        items(reportItems) { (title, desc, action) ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { action(false) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("PDF")
                        }
                        OutlinedButton(onClick = { action(true) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Excel (CSV)")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsAndContactScreen(
    userAccount: UserAccount?,
    preferences: UserPreferences,
    allUsers: List<UserAccount>,
    instructors: List<Instructor>,
    onSetDarkMode: (Boolean?) -> Unit,
    onSetPaymentBackendUrl: (String) -> Unit,
    onAssignRole: (UserAccount, UserRole, String) -> Unit,
    onToggleSuspend: (String, Boolean) -> Unit,
    onAddInstructor: (String, String, String, String) -> Unit
) {
    val isAdmin = userAccount?.userRole == UserRole.ADMINISTRATOR
    var backendUrl by remember(preferences.paymentBackendUrl) { mutableStateOf(preferences.paymentBackendUrl) }
    var showAddInstructor by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            ContactUsCard()
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DarkMode, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Theme & Appearance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = preferences.darkModeOverride == null, onClick = { onSetDarkMode(null) }, label = { Text("System") })
                        FilterChip(selected = preferences.darkModeOverride == false, onClick = { onSetDarkMode(false) }, label = { Text("Light") })
                        FilterChip(selected = preferences.darkModeOverride == true, onClick = { onSetDarkMode(true) }, label = { Text("Dark") })
                    }
                }
            }
        }

        if (isAdmin) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Settings, contentDescription = null, tint = JyEmerald)
                            Spacer(Modifier.width(8.dp))
                            Text("Paystack / Flutterwave Backend Endpoint", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            "Enter your Firebase Cloud Functions or verification server URL so Paystack/Flutterwave keys stay strictly on the server.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = backendUrl,
                            onValueChange = { backendUrl = it },
                            label = { Text("Backend Base URL (https://...)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(onClick = { onSetPaymentBackendUrl(backendUrl) }) {
                            Text("Save Endpoint")
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Instructors (${instructors.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    OutlinedButton(onClick = { showAddInstructor = true }) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add Instructor")
                    }
                }
            }

            items(instructors, key = { it.id }) { inst ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(inst.fullName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("${inst.specialization} • ${inst.phoneNumber}", style = MaterialTheme.typography.bodySmall)
                        Text(inst.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            item {
                Text("User Role & Access Management (${allUsers.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            items(allUsers, key = { it.uid }) { usr ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(usr.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(usr.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            StatusPill(
                                text = usr.role,
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        if (usr.uid != userAccount?.uid) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                UserRole.entries.forEach { targetRole ->
                                    FilterChip(
                                        selected = usr.userRole == targetRole,
                                        onClick = { onAssignRole(usr, targetRole, "Computer Training") },
                                        label = { Text(targetRole.name.take(5)) }
                                    )
                                }
                            }
                            OutlinedButton(onClick = { onToggleSuspend(usr.uid, !usr.isSuspended) }) {
                                Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (usr.isSuspended) "Lift Suspension" else "Suspend Messaging")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddInstructor) {
        var name by remember { mutableStateOf("") }
        var spec by remember { mutableStateOf("Software & Hardware Training") }
        var phone by remember { mutableStateOf("") }
        var email by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddInstructor = false },
            title = { Text("Add Faculty Instructor") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Full Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = spec, onValueChange = { spec = it }, label = { Text("Specialization") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone Number") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email Address") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAddInstructor(name, spec, phone, email)
                        showAddInstructor = false
                    },
                    enabled = name.isNotBlank() && email.contains("@")
                ) { Text("Save Instructor") }
            },
            dismissButton = {
                TextButton(onClick = { showAddInstructor = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun ContactUsScreen() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { ContactUsCard() }
    }
}

@Composable
fun ContactUsCard() {
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                JyLogoBadge(sizeDp = 50)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(InstitutionConfig.INSTITUTION_NAME, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Abuja Computer Training & Repair Center", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Default.LocationOn, contentDescription = null, tint = JyEmerald, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(InstitutionConfig.OFFICE_ADDRESS, style = MaterialTheme.typography.bodyMedium)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AccountBalance, contentDescription = null, tint = StatusInfo, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "OPay Bank Transfer: ${InstitutionConfig.OPAY_ACCOUNT_NAME} (${InstitutionConfig.OPAY_ACCOUNT_NUMBER})",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${InstitutionConfig.PRIMARY_PHONE}")))
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Call 0815...")
                }
                OutlinedButton(
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${InstitutionConfig.SECONDARY_PHONE}")))
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Call 0916...")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        val url = "https://wa.me/${InstitutionConfig.WHATSAPP_NUMBER}"
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("WhatsApp Us")
                }
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${InstitutionConfig.EMAIL_ADDRESS}"))
                        context.startActivity(intent)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Email")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(InstitutionConfig.WEBSITE_URL)))
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Website")
                }
                OutlinedButton(
                    onClick = {
                        val mapUri = Uri.parse("geo:0,0?q=${Uri.encode(InstitutionConfig.MAPS_QUERY)}")
                        context.startActivity(Intent(Intent.ACTION_VIEW, mapUri))
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Directions")
                }
            }
        }
    }
}
