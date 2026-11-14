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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CardMembership
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.ExportUtils
import com.example.core.Formatters
import com.example.domain.model.AcademicResult
import com.example.domain.model.AttendanceRecord
import com.example.domain.model.AttendanceSession
import com.example.domain.model.AttendanceStatus
import com.example.domain.model.Certificate
import com.example.domain.model.CertificateVerification
import com.example.domain.model.Course
import com.example.domain.model.Enrolment
import com.example.domain.model.UserAccount
import com.example.domain.model.UserRole
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.KpiStatCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.JyEmerald
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.StatusInfo
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning

@Composable
fun AttendanceScreen(
    userAccount: UserAccount?,
    courses: List<Course>,
    enrolments: List<Enrolment>,
    attendance: List<AttendanceRecord>,
    attendanceSessions: List<AttendanceSession>,
    onMarkAttendance: (Enrolment, String, AttendanceStatus, String) -> Unit,
    onGenerateQrSession: (Course, String) -> Unit,
    onToggleQrSession: (AttendanceSession, Boolean) -> Unit,
    onSubmitQrAttendance: (String, (AttendanceRecord) -> Unit, (String) -> Unit) -> Unit,
    onExportPdf: () -> Unit,
    onExportExcel: () -> Unit
) {
    val role = userAccount?.userRole ?: UserRole.STUDENT
    val isStaff = role == UserRole.ADMINISTRATOR || role == UserRole.INSTRUCTOR
    var selectedCourseId by remember { mutableStateOf("ALL") }
    var dateIso by remember { mutableStateOf(Formatters.todayIsoDate()) }
    var noteTarget by remember { mutableStateOf<Pair<Enrolment, AttendanceStatus>?>(null) }
    var showQrScanner by remember { mutableStateOf(false) }
    var enlargedSession by remember { mutableStateOf<AttendanceSession?>(null) }

    val qrTargetCourse = remember(courses, selectedCourseId) {
        if (selectedCourseId != "ALL") {
            courses.firstOrNull { it.id == selectedCourseId } ?: courses.firstOrNull()
        } else {
            courses.firstOrNull()
        }
    }
    val activeCourseSession = remember(attendanceSessions, qrTargetCourse, dateIso) {
        val targetId = qrTargetCourse?.id ?: return@remember null
        attendanceSessions.firstOrNull { it.courseId == targetId && it.dateIso == dateIso }
    }
    val enrolledCourseIds = remember(enrolments) { enrolments.map { it.courseId }.toSet() }
    val liveSessionsForStudent = remember(attendanceSessions, enrolledCourseIds, dateIso) {
        attendanceSessions.count { it.isActive && it.dateIso == dateIso && it.courseId in enrolledCourseIds }
    }

    val totalCount = attendance.size
    val presentCount = attendance.count {
        it.status == AttendanceStatus.PRESENT.name || it.status == AttendanceStatus.LATE.name
    }
    val pct = if (totalCount == 0) 0 else (presentCount * 100) / totalCount

    val filteredEnrolments = remember(enrolments, selectedCourseId) {
        if (selectedCourseId == "ALL") enrolments else enrolments.filter { it.courseId == selectedCourseId }
    }
    val filteredRecords = remember(attendance, selectedCourseId) {
        if (selectedCourseId == "ALL") attendance else attendance.filter { it.courseId == selectedCourseId }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                KpiStatCard(
                    title = "Attendance Rate",
                    value = "$pct%",
                    subtitle = "$presentCount / $totalCount classes",
                    icon = Icons.Default.FactCheck,
                    accentColor = JyEmerald,
                    modifier = Modifier.weight(1f)
                )
                KpiStatCard(
                    title = "Absent Sessions",
                    value = attendance.count { it.status == AttendanceStatus.ABSENT.name }.toString(),
                    subtitle = "${attendance.count { it.status == AttendanceStatus.EXCUSED.name }} excused",
                    icon = Icons.Default.CheckCircle,
                    accentColor = StatusWarning,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = selectedCourseId == "ALL",
                        onClick = { selectedCourseId = "ALL" },
                        label = { Text("All Courses") }
                    )
                }
                items(courses, key = { it.id }) { crs ->
                    FilterChip(
                        selected = selectedCourseId == crs.id,
                        onClick = { selectedCourseId = crs.id },
                        label = { Text(crs.code) }
                    )
                }
            }
        }

        if (!isStaff) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(32.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Daily QR Check-In",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = if (liveSessionsForStudent > 0) {
                                        "$liveSessionsForStudent live class QR session(s) open today ($dateIso)"
                                    } else {
                                        "Scan your instructor's class QR code to mark today's attendance."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Button(
                            onClick = { showQrScanner = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("scan_qr_attendance_button")
                        ) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.qr_scan_attendance))
                        }
                    }
                }
            }
        }

        if (isStaff) {
            item {
                OutlinedTextField(
                    value = dateIso,
                    onValueChange = { dateIso = it },
                    label = { Text("Session Date (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("qr_session_card"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Icons.Default.QrCode2,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Column {
                                    Text(
                                        text = "Class QR Attendance",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = qrTargetCourse?.let { "${it.code} • ${it.title}" } ?: "Select a course chip above",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (activeCourseSession != null) {
                                Switch(
                                    checked = activeCourseSession.isActive,
                                    onCheckedChange = { checked -> onToggleQrSession(activeCourseSession, checked) },
                                    modifier = Modifier.testTag("toggle_qr_session_switch")
                                )
                            }
                        }

                        if (activeCourseSession != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                QrCodeImage(
                                    payload = activeCourseSession.qrPayload,
                                    contentDescription = "Class attendance QR code for ${activeCourseSession.courseTitle}",
                                    modifier = Modifier
                                        .size(116.dp)
                                        .clickable { enlargedSession = activeCourseSession }
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    StatusPill(
                                        text = if (activeCourseSession.isActive) "LIVE QR ACTIVE" else "SESSION CLOSED",
                                        containerColor = if (activeCourseSession.isActive) {
                                            StatusSuccess.copy(alpha = 0.15f)
                                        } else {
                                            ErrorRed.copy(alpha = 0.14f)
                                        },
                                        contentColor = if (activeCourseSession.isActive) StatusSuccess else ErrorRed
                                    )
                                    Text(
                                        text = "Code: ${activeCourseSession.sessionCode}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Date: ${activeCourseSession.dateIso}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    OutlinedButton(
                                        onClick = { enlargedSession = activeCourseSession },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("enlarge_qr_button")
                                    ) {
                                        Text("Show Full QR")
                                    }
                                }
                            }
                        } else if (qrTargetCourse != null) {
                            Button(
                                onClick = { onGenerateQrSession(qrTargetCourse, dateIso) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("generate_qr_session_button")
                            ) {
                                Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Generate Today's QR Code")
                            }
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onExportPdf, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Export PDF")
                    }
                    OutlinedButton(onClick = onExportExcel, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Export Excel")
                    }
                }
            }
            item {
                Text("Mark Enrolled Students ($dateIso)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            if (filteredEnrolments.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.FactCheck,
                        title = "No Enrolled Students",
                        subtitle = "Enrol students into courses to mark daily attendance."
                    )
                }
            } else {
                items(filteredEnrolments, key = { "mark_${it.id}" }) { enr ->
                    val existingToday = attendance.firstOrNull {
                        it.studentId == enr.studentId && it.courseId == enr.courseId && it.dateIso == dateIso
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(enr.studentName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text(enr.courseTitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (existingToday != null) {
                                    StatusPill(
                                        text = existingToday.status,
                                        containerColor = StatusInfo.copy(alpha = 0.14f),
                                        contentColor = StatusInfo
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                AttendanceStatus.entries.forEach { st ->
                                    FilterChip(
                                        selected = existingToday?.status == st.name,
                                        onClick = { noteTarget = enr to st },
                                        label = { Text(st.name.take(4)) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Text("Attendance Log", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        if (filteredRecords.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Default.FactCheck,
                    title = "No Attendance Recorded",
                    subtitle = "Marked class sessions appear here automatically."
                )
            }
        } else {
            items(filteredRecords, key = { it.id }) { rec ->
                val pillColor = when (rec.status) {
                    AttendanceStatus.PRESENT.name -> StatusSuccess
                    AttendanceStatus.ABSENT.name -> ErrorRed
                    AttendanceStatus.LATE.name -> StatusWarning
                    else -> StatusInfo
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(rec.studentName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("${rec.courseTitle} • ${rec.dateIso}", style = MaterialTheme.typography.bodySmall)
                            if (rec.notes.isNotBlank()) {
                                Text("Note: ${rec.notes}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        StatusPill(
                            text = rec.status,
                            containerColor = pillColor.copy(alpha = 0.15f),
                            contentColor = pillColor
                        )
                    }
                }
            }
        }
    }

    noteTarget?.let { (enr, st) ->
        var notes by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { noteTarget = null },
            title = { Text("Mark ${st.name}: ${enr.studentName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${enr.courseTitle} • $dateIso", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Session Remarks (optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    onMarkAttendance(enr, dateIso.ifBlank { Formatters.todayIsoDate() }, st, notes)
                    noteTarget = null
                }) {
                    Text("Save Attendance")
                }
            },
            dismissButton = {
                TextButton(onClick = { noteTarget = null }) { Text("Cancel") }
            }
        )
    }

    enlargedSession?.let { session ->
        AlertDialog(
            onDismissRequest = { enlargedSession = null },
            title = { Text(session.courseTitle) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    QrCodeImage(
                        payload = session.qrPayload,
                        contentDescription = "Full attendance QR code for ${session.courseTitle}",
                        modifier = Modifier.size(256.dp)
                    )
                    Text(
                        text = "Session: ${session.sessionCode} • ${session.dateIso}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (session.isActive) {
                            "Students can scan this QR code with JY-SMS to mark today's attendance."
                        } else {
                            "This session is currently closed. Toggle the switch to reopen."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(onClick = { enlargedSession = null }) {
                    Text("Close")
                }
            }
        )
    }

    if (showQrScanner) {
        QrAttendanceScannerDialog(
            onSubmitQrPayload = onSubmitQrAttendance,
            onDismiss = { showQrScanner = false }
        )
    }
}

@Composable
fun ResultsScreen(
    userAccount: UserAccount?,
    courses: List<Course>,
    enrolments: List<Enrolment>,
    results: List<AcademicResult>,
    onSaveResult: (Enrolment, Int, Int, String) -> Unit,
    onExportPdf: () -> Unit,
    onExportExcel: () -> Unit
) {
    val role = userAccount?.userRole ?: UserRole.STUDENT
    val isStaff = role == UserRole.ADMINISTRATOR || role == UserRole.INSTRUCTOR
    var selectedCourseId by remember { mutableStateOf("ALL") }
    var editingEnrolment by remember { mutableStateOf<Enrolment?>(null) }

    val filteredResults = remember(results, selectedCourseId) {
        if (selectedCourseId == "ALL") results else results.filter { it.courseId == selectedCourseId }
    }
    val filteredEnrolments = remember(enrolments, selectedCourseId) {
        if (selectedCourseId == "ALL") enrolments else enrolments.filter { it.courseId == selectedCourseId }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = selectedCourseId == "ALL",
                        onClick = { selectedCourseId = "ALL" },
                        label = { Text("All Courses") }
                    )
                }
                items(courses, key = { it.id }) { crs ->
                    FilterChip(
                        selected = selectedCourseId == crs.id,
                        onClick = { selectedCourseId = crs.id },
                        label = { Text(crs.code) }
                    )
                }
            }
        }

        if (isStaff) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onExportPdf, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Results PDF")
                    }
                    OutlinedButton(onClick = onExportExcel, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Results Excel")
                    }
                }
            }
            item {
                Text("Enter / Update Scores", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            items(filteredEnrolments, key = { "res_enr_${it.id}" }) { enr ->
                val existing = results.firstOrNull { it.studentId == enr.studentId && it.courseId == enr.courseId }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(enr.studentName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(enr.courseTitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (existing != null) {
                                Text(
                                    "Current: ${existing.totalScore}% (${existing.grade} - ${existing.remark})",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = JyEmerald
                                )
                            }
                        }
                        Button(onClick = { editingEnrolment = enr }) {
                            Text(if (existing == null) "Enter Score" else "Update")
                        }
                    }
                }
            }
        }

        item {
            Text("Published Results & Performance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        if (filteredResults.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Default.Assessment,
                    title = "No Results Published",
                    subtitle = "Continuous Assessment (40%) and Exam (60%) scores will appear here."
                )
            }
        } else {
            items(filteredResults, key = { it.id }) { res ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(res.studentName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            StatusPill(
                                text = "Grade ${res.grade} (${res.totalScore}%)",
                                containerColor = if (res.totalScore >= 45) StatusSuccess.copy(alpha = 0.15f) else ErrorRed.copy(alpha = 0.15f),
                                contentColor = if (res.totalScore >= 45) StatusSuccess else ErrorRed
                            )
                        }
                        Text(res.courseTitle, style = MaterialTheme.typography.bodySmall)
                        Text(
                            "CA: ${res.caScore}/40 • Exam: ${res.examScore}/60 • Remark: ${res.remark}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (res.instructorComment.isNotBlank()) {
                            Text("Instructor Note: ${res.instructorComment}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }

    editingEnrolment?.let { target ->
        val existing = results.firstOrNull { it.studentId == target.studentId && it.courseId == target.courseId }
        var caText by remember { mutableStateOf((existing?.caScore ?: 30L).toString()) }
        var examText by remember { mutableStateOf((existing?.examScore ?: 50L).toString()) }
        var comment by remember { mutableStateOf(existing?.instructorComment.orEmpty()) }
        val ca = (caText.toIntOrNull() ?: 0).coerceIn(0, 40)
        val exam = (examText.toIntOrNull() ?: 0).coerceIn(0, 60)
        val total = ca + exam
        val (grade, remark) = Formatters.computeGrade(total)

        AlertDialog(
            onDismissRequest = { editingEnrolment = null },
            title = { Text("Enter Result: ${target.studentName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(target.courseTitle, style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = caText,
                        onValueChange = { caText = it.filter(Char::isDigit) },
                        label = { Text("CA Score (0 - 40)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = examText,
                        onValueChange = { examText = it.filter(Char::isDigit) },
                        label = { Text("Exam Score (0 - 60)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Total: $total/100 • Grade: $grade ($remark)", fontWeight = FontWeight.Bold, color = JyEmerald)
                    OutlinedTextField(
                        value = comment,
                        onValueChange = { comment = it },
                        label = { Text("Instructor Comment") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    onSaveResult(target, ca, exam, comment)
                    editingEnrolment = null
                }) {
                    Text("Publish Result")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingEnrolment = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun CertificatesScreen(
    userAccount: UserAccount?,
    enrolments: List<Enrolment>,
    certificates: List<Certificate>,
    verifiedCert: CertificateVerification?,
    certLookupDone: Boolean,
    onIssueCertificate: (Enrolment, String) -> Unit,
    onVerifyCertificate: (String) -> Unit
) {
    val context = LocalContext.current
    val isAdmin = userAccount?.userRole == UserRole.ADMINISTRATOR
    var verifyInput by remember { mutableStateOf("") }
    var showIssueDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Verified, contentDescription = null, tint = JyEmerald)
                        Spacer(Modifier.width(8.dp))
                        Text("Certificate Verification Portal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    OutlinedTextField(
                        value = verifyInput,
                        onValueChange = { verifyInput = it },
                        label = { Text("Enter Certificate ID (e.g. JY-CERT-...)") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { onVerifyCertificate(verifyInput) },
                        enabled = verifyInput.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("verify_certificate_button")
                    ) {
                        Text("Verify Certificate Authenticity")
                    }
                    if (certLookupDone) {
                        if (verifiedCert != null && verifiedCert.isValid) {
                            StatusPill(
                                text = "VALID: ${verifiedCert.studentFullName} • ${verifiedCert.courseTitle} (${verifiedCert.completionDate})",
                                containerColor = StatusSuccess.copy(alpha = 0.16f),
                                contentColor = StatusSuccess
                            )
                        } else {
                            StatusPill(
                                text = "Certificate ID not found or revoked",
                                containerColor = ErrorRed.copy(alpha = 0.16f),
                                contentColor = ErrorRed
                            )
                        }
                    }
                }
            }
        }

        if (isAdmin) {
            item {
                Button(
                    onClick = { showIssueDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("issue_certificate_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Issue Official Certificate")
                }
            }
        }

        item {
            Text("Issued Certificates (${certificates.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        if (certificates.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Default.CardMembership,
                    title = "No Certificates Issued Yet",
                    subtitle = "Completed students receive verifiable JY-COMPUTERS PDF certificates here."
                )
            }
        } else {
            items(certificates, key = { it.id }) { cert ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(cert.certificateNumber, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            StatusPill(
                                text = if (cert.isRevoked) "REVOKED" else "VERIFIED",
                                containerColor = if (cert.isRevoked) ErrorRed.copy(alpha = 0.15f) else StatusSuccess.copy(alpha = 0.15f),
                                contentColor = if (cert.isRevoked) ErrorRed else StatusSuccess
                            )
                        }
                        Text(cert.studentFullName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Programme: ${cert.courseTitle}", style = MaterialTheme.typography.bodyMedium)
                        Text("Completed: ${cert.completionDate} • Grade: ${cert.grade.ifBlank { "Distinction" }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Button(
                            onClick = {
                                val uri = ExportUtils.generateCertificatePdf(context, cert)
                                ExportUtils.shareExportUri(context, uri, "application/pdf", "Download Certificate PDF")
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Download / Print PDF Certificate")
                        }
                    }
                }
            }
        }
    }

    if (showIssueDialog) {
        var selectedEnrolment by remember(enrolments) { mutableStateOf(enrolments.firstOrNull()) }
        var grade by remember { mutableStateOf("Distinction") }
        AlertDialog(
            onDismissRequest = { showIssueDialog = false },
            title = { Text("Issue Official Certificate") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (enrolments.isEmpty()) {
                        Text("No student enrolments available to graduate.")
                    } else {
                        Text("Select Enrolled Student:", style = MaterialTheme.typography.labelMedium)
                        enrolments.take(8).forEach { enr ->
                            FilterChip(
                                selected = selectedEnrolment?.id == enr.id,
                                onClick = { selectedEnrolment = enr },
                                label = { Text("${enr.studentName} - ${enr.courseTitle}") }
                            )
                        }
                        OutlinedTextField(
                            value = grade,
                            onValueChange = { grade = it },
                            label = { Text("Awarded Grade / Classification") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedEnrolment?.let { onIssueCertificate(it, grade) }
                        showIssueDialog = false
                    },
                    enabled = selectedEnrolment != null
                ) {
                    Text("Issue Certificate")
                }
            },
            dismissButton = {
                TextButton(onClick = { showIssueDialog = false }) { Text("Cancel") }
            }
        )
    }
}
