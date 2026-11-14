package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.core.Formatters
import com.example.domain.model.Course
import com.example.domain.model.Enrolment
import com.example.domain.model.Student
import com.example.domain.model.StudentStatus
import com.example.domain.model.UserAccount
import com.example.domain.model.UserRole
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning

@Composable
fun StudentManagementScreen(
    currentUid: String,
    userAccount: UserAccount?,
    students: List<Student>,
    courses: List<Course>,
    enrolments: List<Enrolment>,
    onSaveStudent: (Student) -> Unit,
    onUpdateStatus: (String, StudentStatus) -> Unit,
    onDeleteStudent: (String) -> Unit,
    onExportPdf: () -> Unit,
    onExportExcel: () -> Unit
) {
    val role = userAccount?.userRole ?: UserRole.STUDENT
    val isAdmin = role == UserRole.ADMINISTRATOR
    val isStaff = role == UserRole.ADMINISTRATOR || role == UserRole.INSTRUCTOR

    var searchQuery by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("ALL") }
    var courseFilterId by remember { mutableStateOf("ALL") }
    var editingStudent by remember { mutableStateOf<Student?>(null) }
    var viewingStudent by remember { mutableStateOf<Student?>(null) }

    val filtered = remember(students, searchQuery, statusFilter, courseFilterId, enrolments) {
        students.filter { st ->
            val q = searchQuery.trim().lowercase()
            val matchesQuery = q.isEmpty() ||
                st.fullName.lowercase().contains(q) ||
                st.studentCode.lowercase().contains(q) ||
                st.phoneNumber.lowercase().contains(q) ||
                st.email.lowercase().contains(q)
            val matchesStatus = statusFilter == "ALL" || st.status == statusFilter
            val matchesCourse = courseFilterId == "ALL" || enrolments.any { it.studentId == st.id && it.courseId == courseFilterId }
            matchesQuery && matchesStatus && matchesCourse
        }
    }

    Scaffold(
        floatingActionButton = {
            if (isAdmin || students.none { it.userId == currentUid }) {
                FloatingActionButton(
                    onClick = {
                        editingStudent = Student(
                            id = if (isAdmin) "" else currentUid,
                            userId = currentUid,
                            studentCode = Formatters.generateStudentCode(),
                            fullName = if (isAdmin) "" else userAccount?.displayName.orEmpty(),
                            email = if (isAdmin) "" else userAccount?.email.orEmpty(),
                            registrationDate = Formatters.todayIsoDate()
                        )
                    },
                    modifier = Modifier.testTag("add_student_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Register student")
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
            if (isStaff) {
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Search name, ID, phone, or email") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val statuses = listOf("ALL", "ACTIVE", "INACTIVE", "GRADUATED", "ARCHIVED")
                        items(statuses) { st ->
                            FilterChip(
                                selected = statusFilter == st,
                                onClick = { statusFilter = st },
                                label = { Text(st) }
                            )
                        }
                    }
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = courseFilterId == "ALL",
                                onClick = { courseFilterId = "ALL" },
                                label = { Text("All Courses") }
                            )
                        }
                        items(courses, key = { it.id }) { crs ->
                            FilterChip(
                                selected = courseFilterId == crs.id,
                                onClick = { courseFilterId = crs.id },
                                label = { Text(crs.code) }
                            )
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
            }

            if (filtered.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.Person,
                        title = if (isStaff) "No Matching Students" else "Student Profile Not Registered",
                        subtitle = if (isStaff) "Tap + to register a new student or adjust filters." else "Tap + to complete your official JY-COMPUTERS student registration."
                    )
                }
            } else {
                items(filtered, key = { it.id }) { student ->
                    StudentCard(
                        student = student,
                        isAdmin = isAdmin,
                        canEdit = isAdmin || student.userId == currentUid,
                        onView = { viewingStudent = student },
                        onEdit = { editingStudent = student },
                        onArchive = { onUpdateStatus(student.id, StudentStatus.ARCHIVED) },
                        onDelete = { onDeleteStudent(student.id) }
                    )
                }
            }
        }
    }

    editingStudent?.let { draft ->
        StudentRegistrationDialog(
            initial = draft,
            isAdmin = isAdmin,
            onDismiss = { editingStudent = null },
            onSave = { updated ->
                onSaveStudent(updated)
                editingStudent = null
            }
        )
    }

    viewingStudent?.let { profile ->
        StudentProfileDetailDialog(
            student = profile,
            enrolments = enrolments.filter { it.studentId == profile.id || it.userId == profile.userId },
            onDismiss = { viewingStudent = null }
        )
    }
}

@Composable
private fun StudentCard(
    student: Student,
    isAdmin: Boolean,
    canEdit: Boolean,
    onView: () -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onView),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (student.passportPhotoUri.isNotBlank()) {
                AsyncImage(
                    model = student.passportPhotoUri,
                    contentDescription = "Passport photo of ${student.fullName}",
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = student.fullName.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(student.fullName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("${student.studentCode} • ${student.phoneNumber}", style = MaterialTheme.typography.bodySmall)
                Text(student.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                StatusPill(
                    text = student.status,
                    containerColor = if (student.status == StudentStatus.ACTIVE.name) StatusSuccess.copy(alpha = 0.15f) else StatusWarning.copy(alpha = 0.15f),
                    contentColor = if (student.status == StudentStatus.ACTIVE.name) StatusSuccess else StatusWarning
                )
            }
            if (canEdit) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit student")
                }
            }
            if (isAdmin) {
                IconButton(onClick = onArchive) {
                    Icon(Icons.Default.Archive, contentDescription = "Archive student")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete student", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun StudentRegistrationDialog(
    initial: Student,
    isAdmin: Boolean,
    onDismiss: () -> Unit,
    onSave: (Student) -> Unit
) {
    var fullName by remember { mutableStateOf(initial.fullName) }
    var passportUri by remember { mutableStateOf(initial.passportPhotoUri) }
    var gender by remember { mutableStateOf(initial.gender.ifBlank { "Male" }) }
    var dob by remember { mutableStateOf(initial.dateOfBirth) }
    var phone by remember { mutableStateOf(initial.phoneNumber) }
    var email by remember { mutableStateOf(initial.email) }
    var address by remember { mutableStateOf(initial.residentialAddress) }
    var nextOfKin by remember { mutableStateOf(initial.nextOfKin) }
    var nextOfKinPhone by remember { mutableStateOf(initial.nextOfKinPhone) }
    var status by remember { mutableStateOf(initial.status.ifBlank { StudentStatus.ACTIVE.name }) }
    var extraInfo by remember { mutableStateOf(initial.additionalInfo) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) passportUri = uri.toString()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Student Registration (${initial.studentCode})") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (passportUri.isNotBlank()) {
                        AsyncImage(
                            model = passportUri,
                            contentDescription = "Passport photo",
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                    OutlinedButton(
                        onClick = {
                            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (passportUri.isBlank()) "Select Passport Photo" else "Change Photo")
                    }
                }

                OutlinedTextField(value = fullName, onValueChange = { fullName = it }, label = { Text("Full Name *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Male", "Female", "Other").forEach { g ->
                        FilterChip(selected = gender == g, onClick = { gender = g }, label = { Text(g) })
                    }
                }
                OutlinedTextField(value = dob, onValueChange = { dob = it }, label = { Text("Date of Birth (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone Number *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email Address *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Residential Address") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = nextOfKin, onValueChange = { nextOfKin = it }, label = { Text("Next of Kin") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = nextOfKinPhone, onValueChange = { nextOfKinPhone = it }, label = { Text("Next of Kin Telephone") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (isAdmin) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("ACTIVE", "INACTIVE", "GRADUATED").forEach { st ->
                            FilterChip(selected = status == st, onClick = { status = st }, label = { Text(st) })
                        }
                    }
                }
                OutlinedTextField(value = extraInfo, onValueChange = { extraInfo = it }, label = { Text("Additional Information") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        initial.copy(
                            fullName = fullName,
                            passportPhotoUri = passportUri,
                            gender = gender,
                            dateOfBirth = dob,
                            phoneNumber = phone,
                            email = email,
                            residentialAddress = address,
                            nextOfKin = nextOfKin,
                            nextOfKinPhone = nextOfKinPhone,
                            status = status,
                            additionalInfo = extraInfo
                        )
                    )
                },
                enabled = fullName.trim().length >= 2 && phone.trim().length >= 5 && email.contains("@")
            ) {
                Text("Save Record")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun StudentProfileDetailDialog(
    student: Student,
    enrolments: List<Enrolment>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(student.fullName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Student ID: ${student.studentCode}", fontWeight = FontWeight.Bold)
                Text("Status: ${student.status}")
                Text("Gender: ${student.gender} • DOB: ${student.dateOfBirth.ifBlank { "N/A" }}")
                Text("Phone: ${student.phoneNumber}")
                Text("Email: ${student.email}")
                Text("Address: ${student.residentialAddress.ifBlank { "N/A" }}")
                Text("Next of Kin: ${student.nextOfKin.ifBlank { "N/A" }} (${student.nextOfKinPhone.ifBlank { "-" }})")
                Text("Registered: ${student.registrationDate}")
                Spacer(Modifier.height(6.dp))
                Text("Enrolled Courses (${enrolments.size}):", fontWeight = FontWeight.SemiBold)
                enrolments.forEach {
                    Text("• ${it.courseTitle} (${it.enrolmentStatus} / ${it.paymentStatus})", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
