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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.Formatters
import com.example.domain.model.CompletionStatus
import com.example.domain.model.Course
import com.example.domain.model.Enrolment
import com.example.domain.model.EnrolmentStatus
import com.example.domain.model.Instructor
import com.example.domain.model.Student
import com.example.domain.model.UserAccount
import com.example.domain.model.UserRole
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.JyEmerald
import com.example.ui.theme.StatusInfo
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning

@Composable
fun CoursesScreen(
    currentUid: String,
    userAccount: UserAccount?,
    courses: List<Course>,
    students: List<Student>,
    instructors: List<Instructor>,
    onSaveCourse: (Course) -> Unit,
    onArchiveOrDeleteCourse: (String, Boolean) -> Unit,
    onEnrolStudent: (Student, Course, String) -> Unit
) {
    val role = userAccount?.userRole ?: UserRole.STUDENT
    val isAdmin = role == UserRole.ADMINISTRATOR
    var editingCourse by remember { mutableStateOf<Course?>(null) }
    var enrolCourse by remember { mutableStateOf<Course?>(null) }
    var query by remember { mutableStateOf("") }

    val filtered = remember(courses, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) courses else courses.filter {
            it.title.lowercase().contains(q) || it.code.lowercase().contains(q)
        }
    }

    Scaffold(
        floatingActionButton = {
            if (isAdmin) {
                FloatingActionButton(onClick = { editingCourse = Course(code = "JY-${courses.size + 501}") }) {
                    Icon(Icons.Default.Add, contentDescription = "Add course")
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
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search JY-COMPUTERS courses") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (filtered.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.MenuBook,
                        title = "No Courses Found",
                        subtitle = "Try another keyword or add a new training programme."
                    )
                }
            } else {
                items(filtered, key = { it.id }) { course ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                StatusPill(
                                    text = "${course.code} • ${course.durationLabel}",
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = Formatters.formatNgn(course.feeNgn),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = JyEmerald
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(course.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                course.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                                Text(
                                    "${course.schedule} • Instructor: ${course.instructorName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isAdmin) {
                                    IconButton(onClick = { editingCourse = course }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit course")
                                    }
                                    IconButton(onClick = { onArchiveOrDeleteCourse(course.id, true) }) {
                                        Icon(Icons.Default.Archive, contentDescription = "Archive course")
                                    }
                                    IconButton(onClick = { onArchiveOrDeleteCourse(course.id, false) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete course", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                                if (course.isAvailable) {
                                    Button(onClick = { enrolCourse = course }) {
                                        Text(if (isAdmin) "Enrol Student" else "Enrol Now")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    editingCourse?.let { draft ->
        CourseEditorDialog(
            initial = draft,
            instructors = instructors,
            onDismiss = { editingCourse = null },
            onSave = {
                onSaveCourse(it)
                editingCourse = null
            }
        )
    }

    enrolCourse?.let { targetCourse ->
        val candidateStudents = if (isAdmin) students else students.filter { it.userId == currentUid }
        EnrolConfirmDialog(
            course = targetCourse,
            candidateStudents = candidateStudents,
            fallbackUid = currentUid,
            fallbackName = userAccount?.displayName ?: "Student",
            fallbackEmail = userAccount?.email.orEmpty(),
            onDismiss = { enrolCourse = null },
            onConfirm = { student, plan ->
                onEnrolStudent(student, targetCourse, plan)
                enrolCourse = null
            }
        )
    }
}

@Composable
private fun CourseEditorDialog(
    initial: Course,
    instructors: List<Instructor>,
    onDismiss: () -> Unit,
    onSave: (Course) -> Unit
) {
    var code by remember { mutableStateOf(initial.code) }
    var title by remember { mutableStateOf(initial.title) }
    var description by remember { mutableStateOf(initial.description) }
    var durationLabel by remember { mutableStateOf(initial.durationLabel) }
    var durationMonths by remember { mutableStateOf(initial.durationMonths.toString()) }
    var feeNgn by remember { mutableStateOf(initial.feeNgn.toString()) }
    var schedule by remember { mutableStateOf(initial.schedule) }
    var instructorName by remember { mutableStateOf(initial.instructorName) }
    var isAvailable by remember { mutableStateOf(initial.isAvailable) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id.isBlank()) "Create Course" else "Edit ${initial.code}") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("Course Code") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Course Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = durationLabel, onValueChange = { durationLabel = it }, label = { Text("Duration Label (e.g. 6 Months)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = durationMonths, onValueChange = { durationMonths = it.filter(Char::isDigit) }, label = { Text("Duration Months") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = feeNgn, onValueChange = { feeNgn = it.filter(Char::isDigit) }, label = { Text("Course Fee (NGN)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = schedule, onValueChange = { schedule = it }, label = { Text("Class Schedule") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = instructorName, onValueChange = { instructorName = it }, label = { Text("Assigned Instructor") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (instructors.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        instructors.take(3).forEach { inst ->
                            FilterChip(
                                selected = instructorName == inst.fullName,
                                onClick = { instructorName = inst.fullName },
                                label = { Text(inst.fullName) }
                            )
                        }
                    }
                }
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Available for Enrolment", modifier = Modifier.weight(1f))
                    Switch(checked = isAvailable, onCheckedChange = { isAvailable = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        initial.copy(
                            code = code,
                            title = title,
                            description = description,
                            durationLabel = durationLabel,
                            durationMonths = durationMonths.toLongOrNull() ?: 1L,
                            feeNgn = feeNgn.toLongOrNull() ?: 0L,
                            schedule = schedule,
                            instructorName = instructorName,
                            isAvailable = isAvailable
                        )
                    )
                },
                enabled = code.isNotBlank() && title.isNotBlank()
            ) {
                Text("Save Course")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun EnrolConfirmDialog(
    course: Course,
    candidateStudents: List<Student>,
    fallbackUid: String,
    fallbackName: String,
    fallbackEmail: String,
    onDismiss: () -> Unit,
    onConfirm: (Student, String) -> Unit
) {
    var selectedStudent by remember(candidateStudents) {
        mutableStateOf(
            candidateStudents.firstOrNull() ?: Student(
                id = fallbackUid,
                userId = fallbackUid,
                studentCode = Formatters.generateStudentCode(),
                fullName = fallbackName,
                email = fallbackEmail
            )
        )
    }
    var plan by remember { mutableStateOf("Full Payment") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enrol in ${course.title}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Course Fee: ${Formatters.formatNgn(course.feeNgn)} (${course.durationLabel})", fontWeight = FontWeight.Bold, color = JyEmerald)
                if (candidateStudents.size > 1) {
                    Text("Select Student:", style = MaterialTheme.typography.labelMedium)
                    candidateStudents.take(5).forEach { st ->
                        FilterChip(
                            selected = selectedStudent.id == st.id,
                            onClick = { selectedStudent = st },
                            label = { Text("${st.fullName} (${st.studentCode})") }
                        )
                    }
                } else {
                    Text("Student: ${selectedStudent.fullName}")
                }
                Text("Payment Plan:", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Full Payment", "2 Instalments", "3 Instalments").forEach { p ->
                        FilterChip(selected = plan == p, onClick = { plan = p }, label = { Text(p) })
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedStudent, plan) }) {
                Text("Confirm Enrolment")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun EnrolmentsScreen(
    userAccount: UserAccount?,
    enrolments: List<Enrolment>,
    onConfirmEnrolment: (Enrolment) -> Unit,
    onUpdateCompletion: (String, CompletionStatus) -> Unit,
    onGoToPay: () -> Unit
) {
    val role = userAccount?.userRole ?: UserRole.STUDENT
    val isAdmin = role == UserRole.ADMINISTRATOR
    val isStaff = role == UserRole.ADMINISTRATOR || role == UserRole.INSTRUCTOR

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (enrolments.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Default.School,
                    title = "No Enrolments Yet",
                    subtitle = "Select a course from the Catalogue to start your training journey."
                )
            }
        } else {
            items(enrolments, key = { it.id }) { enr ->
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
                            Text(enr.reference, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            StatusPill(
                                text = enr.enrolmentStatus,
                                containerColor = if (enr.enrolmentStatus == EnrolmentStatus.CONFIRMED.name) StatusSuccess.copy(alpha = 0.15f) else StatusWarning.copy(alpha = 0.15f),
                                contentColor = if (enr.enrolmentStatus == EnrolmentStatus.CONFIRMED.name) StatusSuccess else StatusWarning
                            )
                        }
                        Text(enr.courseTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Student: ${enr.studentName} • Plan: ${enr.instalmentPlan}", style = MaterialTheme.typography.bodySmall)
                        Text("Dates: ${enr.startDate} -> ${enr.expectedCompletionDate}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatusPill(
                                text = "Pay: ${enr.paymentStatus}",
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            StatusPill(
                                text = enr.completionStatus,
                                containerColor = StatusInfo.copy(alpha = 0.14f),
                                contentColor = StatusInfo
                            )
                        }
                        Text(
                            "Fee: ${Formatters.formatNgn(enr.courseFeeNgn)} | Paid: ${Formatters.formatNgn(enr.amountPaidNgn)} | Balance: ${Formatters.formatNgn(enr.balanceNgn)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (enr.balanceNgn > 0L && !isAdmin) {
                                Button(onClick = onGoToPay) { Text("Pay Balance") }
                            }
                            if (isAdmin && enr.enrolmentStatus != EnrolmentStatus.CONFIRMED.name) {
                                Button(onClick = { onConfirmEnrolment(enr) }) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Confirm & Add Group")
                                }
                            }
                            if (isStaff && enr.completionStatus != CompletionStatus.COMPLETED.name) {
                                OutlinedButton(onClick = { onUpdateCompletion(enr.id, CompletionStatus.COMPLETED) }) {
                                    Text("Mark Completed")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
