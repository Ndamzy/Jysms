package com.example.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.R
import com.example.core.ExportUtils
import com.example.core.FlutterwaveGateway
import com.example.core.Formatters
import com.example.core.GatewayInitResult
import com.example.core.PaystackGateway
import com.example.core.unwrapFirestoreMessage
import com.example.data.local.AppPreferencesRepository
import com.example.data.local.JySmsDatabase
import com.example.data.local.UserPreferences
import com.example.data.repository.AcademicRepository
import com.example.data.repository.AuthRepository
import com.example.data.repository.CommunicationRepository
import com.example.data.repository.CourseEnrolmentRepository
import com.example.data.repository.PaymentRepository
import com.example.data.repository.StudentRepository
import com.example.domain.model.AcademicResult
import com.example.domain.model.ActivityLog
import com.example.domain.model.Announcement
import com.example.domain.model.AppNotification
import com.example.domain.model.AttendanceRecord
import com.example.domain.model.AttendanceSession
import com.example.domain.model.AttendanceStatus
import com.example.domain.model.BlockedUser
import com.example.domain.model.Certificate
import com.example.domain.model.CertificateVerification
import com.example.domain.model.ChatMessage
import com.example.domain.model.ChatReport
import com.example.domain.model.CompletionStatus
import com.example.domain.model.Conversation
import com.example.domain.model.Course
import com.example.domain.model.DirectoryMember
import com.example.domain.model.Enrolment
import com.example.domain.model.GroupChat
import com.example.domain.model.GroupMember
import com.example.domain.model.Instructor
import com.example.domain.model.MessageType
import com.example.domain.model.Payment
import com.example.domain.model.PaymentProvider
import com.example.domain.model.PaymentSubmission
import com.example.domain.model.PaymentTxStatus
import com.example.domain.model.PreloadedCourses
import com.example.domain.model.Student
import com.example.domain.model.StudentStatus
import com.example.domain.model.UserAccount
import com.example.domain.model.UserRole
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class MainSessionViewModel(
    val currentUserId: String,
    private val authRepository: AuthRepository,
    private val studentRepository: StudentRepository,
    private val courseEnrolmentRepository: CourseEnrolmentRepository,
    private val paymentRepository: PaymentRepository,
    private val academicRepository: AcademicRepository,
    private val communicationRepository: CommunicationRepository,
    private val preferencesRepository: AppPreferencesRepository
) : ViewModel() {

    private val paystackGateway = PaystackGateway()
    private val flutterwaveGateway = FlutterwaveGateway()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _gatewayInit = MutableStateFlow<GatewayInitResult?>(null)
    val gatewayInit: StateFlow<GatewayInitResult?> = _gatewayInit.asStateFlow()

    private val _verifiedCert = MutableStateFlow<CertificateVerification?>(null)
    val verifiedCert: StateFlow<CertificateVerification?> = _verifiedCert.asStateFlow()

    private val _certLookupDone = MutableStateFlow(false)
    val certLookupDone: StateFlow<Boolean> = _certLookupDone.asStateFlow()

    val preferences: StateFlow<UserPreferences> = preferencesRepository.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UserPreferences())

    val userAccount: StateFlow<UserAccount?> = authRepository.observeUserAccount(currentUserId)
        .safeCatch("userAccount", null)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    private val roleFlow: Flow<UserRole> = authRepository.observeUserAccount(currentUserId)
        .safeCatch("role", null)
        .flatMapLatest { acc -> flowOf(acc?.userRole ?: UserRole.STUDENT) }

    val courses: StateFlow<List<Course>> = roleFlow.flatMapLatest { role ->
        courseEnrolmentRepository.observeCourses(role)
            .safeCatch("courses", PreloadedCourses.OFFICIAL_CATALOG)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), PreloadedCourses.OFFICIAL_CATALOG)

    val students: StateFlow<List<Student>> = roleFlow.flatMapLatest { role ->
        studentRepository.observeStudents(currentUserId, role).safeCatch("students", emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val enrolments: StateFlow<List<Enrolment>> = roleFlow.flatMapLatest { role ->
        courseEnrolmentRepository.observeEnrolments(currentUserId, role).safeCatch("enrolments", emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val payments: StateFlow<List<Payment>> = roleFlow.flatMapLatest { role ->
        paymentRepository.observePayments(currentUserId, role).safeCatch("payments", emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val paymentSubmissions: StateFlow<List<PaymentSubmission>> = roleFlow.flatMapLatest { role ->
        paymentRepository.observePaymentSubmissions(currentUserId, role).safeCatch("submissions", emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val attendance: StateFlow<List<AttendanceRecord>> = roleFlow.flatMapLatest { role ->
        academicRepository.observeAttendance(currentUserId, role).safeCatch("attendance", emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val attendanceSessions: StateFlow<List<AttendanceSession>> = academicRepository.observeAttendanceSessions()
        .safeCatch("attendanceSessions", emptyList())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val results: StateFlow<List<AcademicResult>> = roleFlow.flatMapLatest { role ->
        academicRepository.observeResults(currentUserId, role).safeCatch("results", emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val certificates: StateFlow<List<Certificate>> = roleFlow.flatMapLatest { role ->
        academicRepository.observeCertificates(currentUserId, role).safeCatch("certificates", emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val directory: StateFlow<List<DirectoryMember>> = roleFlow.flatMapLatest { role ->
        communicationRepository.observeDirectory(role == UserRole.ADMINISTRATOR).safeCatch("directory", emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val instructors: StateFlow<List<Instructor>> = roleFlow.flatMapLatest { role ->
        authRepository.observeInstructors(role == UserRole.ADMINISTRATOR).safeCatch("instructors", emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val allUsers: StateFlow<List<UserAccount>> = roleFlow.flatMapLatest { role ->
        if (role == UserRole.ADMINISTRATOR) {
            authRepository.observeAllUsers().safeCatch("allUsers", emptyList())
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val conversations: StateFlow<List<Conversation>> =
        communicationRepository.observeConversations(currentUserId)
            .safeCatch("conversations", emptyList())
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val groupChats: StateFlow<List<GroupChat>> = roleFlow.flatMapLatest { role ->
        communicationRepository.observeGroupChats(role == UserRole.ADMINISTRATOR).safeCatch("groupChats", emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val groupMembers: StateFlow<List<GroupMember>> = roleFlow.flatMapLatest { role ->
        communicationRepository.observeGroupMemberships(currentUserId, role).safeCatch("groupMembers", emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val blockedUsers: StateFlow<List<BlockedUser>> =
        communicationRepository.observeBlockedUsers(currentUserId)
            .safeCatch("blockedUsers", emptyList())
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val chatReports: StateFlow<List<ChatReport>> = roleFlow.flatMapLatest { role ->
        communicationRepository.observeChatReports(currentUserId, role == UserRole.ADMINISTRATOR).safeCatch("chatReports", emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val announcements: StateFlow<List<Announcement>> = roleFlow.flatMapLatest { role ->
        communicationRepository.observeAnnouncements(role).safeCatch("announcements", emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val notifications: StateFlow<List<AppNotification>> =
        communicationRepository.observeNotifications(currentUserId)
            .safeCatch("notifications", emptyList())
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val activityLogs: StateFlow<List<ActivityLog>> = roleFlow.flatMapLatest { role ->
        if (role == UserRole.ADMINISTRATOR) {
            communicationRepository.observeActivityLogs().safeCatch("activityLogs", emptyList())
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    init {
        viewModelScope.launch {
            Firebase.auth.currentUser?.let { firebaseUser ->
                val synced = authRepository.syncSignedInUser(firebaseUser).getOrNull()
                if (synced?.userRole == UserRole.ADMINISTRATOR) {
                    courseEnrolmentRepository.seedDefaultCatalogAndGroupsIfAdmin()
                }
            }
        }
    }

    private fun <T> Flow<T>.safeCatch(tag: String, fallback: T): Flow<T> = catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        Log.w("MainSessionVM", "Stream error in $tag: ${error.unwrapFirestoreMessage("Stream interrupted")}", error)
        emit(fallback)
    }

    fun showBanner(message: String) {
        _statusBanner.value = message
    }

    fun clearBanner() {
        _statusBanner.value = null
    }

    fun clearGatewayInit() {
        _gatewayInit.value = null
    }

    fun setDarkMode(mode: Boolean?) {
        viewModelScope.launch { preferencesRepository.setDarkMode(mode) }
    }

    fun updateNotificationPrefs(messages: Boolean, payments: Boolean, academics: Boolean, announcements: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updateNotificationPrefs(messages, payments, academics, announcements)
            _statusBanner.value = "Notification preferences saved"
        }
    }

    fun setPaymentBackendUrl(url: String) {
        viewModelScope.launch {
            preferencesRepository.setPaymentBackendUrl(url)
            _statusBanner.value = "Payment gateway endpoint updated"
        }
    }

    fun saveStudent(student: Student) {
        viewModelScope.launch {
            val role = userAccount.value?.userRole ?: UserRole.STUDENT
            val isAdmin = role == UserRole.ADMINISTRATOR || role == UserRole.INSTRUCTOR
            val res = studentRepository.saveStudentProfile(student, isAdmin, students.value)
            if (res.isSuccess) {
                communicationRepository.logActivity(
                    currentUserId,
                    userAccount.value?.displayName ?: "Member",
                    "Saved student profile",
                    "STUDENT",
                    res.getOrDefault(""),
                    student.fullName
                )
                _statusBanner.value = "Student record saved successfully"
            } else {
                _statusBanner.value = res.exceptionOrNull()?.localizedMessage ?: "Failed to save student"
            }
        }
    }

    fun updateStudentStatus(studentId: String, status: StudentStatus) {
        viewModelScope.launch {
            val res = studentRepository.updateStudentStatus(studentId, status)
            _statusBanner.value = if (res.isSuccess) "Student status updated to ${status.name}" else "Unable to update student status"
        }
    }

    fun deleteStudent(studentId: String) {
        viewModelScope.launch {
            val res = studentRepository.deleteStudent(studentId)
            _statusBanner.value = if (res.isSuccess) "Student record deleted" else "Delete failed"
        }
    }

    fun saveCourse(course: Course) {
        viewModelScope.launch {
            val res = courseEnrolmentRepository.saveCourse(course)
            if (res.isSuccess) {
                communicationRepository.logActivity(currentUserId, userAccount.value?.displayName ?: "Admin", "Saved course", "COURSE", course.code, course.title)
                _statusBanner.value = "Course saved: ${course.title}"
            } else {
                _statusBanner.value = res.exceptionOrNull()?.localizedMessage ?: "Failed to save course"
            }
        }
    }

    fun archiveOrDeleteCourse(courseId: String, archiveOnly: Boolean) {
        viewModelScope.launch {
            val res = courseEnrolmentRepository.deleteOrArchiveCourse(courseId, archiveOnly)
            _statusBanner.value = if (res.isSuccess) {
                if (archiveOnly) "Course archived" else "Course deleted"
            } else {
                "Course update failed"
            }
        }
    }

    fun enrolStudentInCourse(student: Student, course: Course, instalmentPlan: String) {
        viewModelScope.launch {
            val alreadyEnrolled = enrolments.value.any {
                it.studentId == student.id && it.courseId == course.id && it.enrolmentStatus != "CANCELLED"
            }
            if (alreadyEnrolled) {
                _statusBanner.value = "${student.fullName} is already enrolled in ${course.title}"
                return@launch
            }
            val res = courseEnrolmentRepository.createEnrolment(student, course, instalmentPlan)
            _statusBanner.value = if (res.isSuccess) {
                "Enrolment submitted for ${course.title}. Proceed to payment."
            } else {
                res.exceptionOrNull()?.localizedMessage ?: "Enrolment failed"
            }
        }
    }

    fun confirmEnrolment(enrolment: Enrolment) {
        viewModelScope.launch {
            val res = courseEnrolmentRepository.confirmEnrolmentAndJoinGroup(enrolment)
            _statusBanner.value = if (res.isSuccess) {
                "Enrolment confirmed & added to ${enrolment.courseTitle} group chat"
            } else {
                "Confirmation failed"
            }
        }
    }

    fun updateCompletionStatus(enrolmentId: String, status: CompletionStatus) {
        viewModelScope.launch {
            val res = courseEnrolmentRepository.updateCompletionStatus(enrolmentId, status)
            _statusBanner.value = if (res.isSuccess) "Completion status updated" else "Update failed"
        }
    }

    fun initiateOnlinePayment(enrolment: Enrolment, amountNgn: Long, provider: PaymentProvider) {
        viewModelScope.launch {
            val reference = Formatters.generateReference(if (provider == PaymentProvider.PAYSTACK) "PSK" else "FLW")
            val recordRes = paymentRepository.recordPendingGatewayPayment(
                enrolment = enrolment,
                amountNgn = amountNgn,
                provider = provider,
                reference = reference,
                channel = "card_or_transfer"
            )
            if (recordRes.isFailure) {
                _statusBanner.value = "Could not create pending transaction"
                return@launch
            }
            val gateway = if (provider == PaymentProvider.PAYSTACK) paystackGateway else flutterwaveGateway
            val init = gateway.initializeCheckout(
                email = userAccount.value?.email ?: "student@jy-computers.com",
                amountNgn = amountNgn.toInt(),
                reference = reference,
                courseTitle = enrolment.courseTitle,
                backendBaseUrl = preferences.value.paymentBackendUrl
            )
            _gatewayInit.value = init
        }
    }

    fun verifyGatewayPaymentOnServer(payment: Payment) {
        viewModelScope.launch {
            val gateway = if (payment.provider == PaymentProvider.FLUTTERWAVE.name) flutterwaveGateway else paystackGateway
            val verify = gateway.verifyOnBackend(
                reference = payment.reference,
                expectedAmountNgn = payment.amountNgn.toInt(),
                backendBaseUrl = preferences.value.paymentBackendUrl
            )
            if (verify.verified) {
                val enr = enrolments.value.firstOrNull { it.id == payment.enrolmentId }
                if (enr != null && userAccount.value?.userRole == UserRole.ADMINISTRATOR) {
                    paymentRepository.adminUpdatePaymentStatus(currentUserId, payment, PaymentTxStatus.SUCCESSFUL)
                }
                _statusBanner.value = verify.message
            } else {
                _statusBanner.value = verify.message
            }
        }
    }

    fun submitOpayTransfer(enrolment: Enrolment, amountNgn: Long, transferRef: String, proofUri: String) {
        viewModelScope.launch {
            val res = paymentRepository.submitOpayBankTransfer(enrolment, amountNgn, transferRef, proofUri)
            _statusBanner.value = if (res.isSuccess) {
                "OPay transfer submitted (${res.getOrNull()}). Awaiting Admin verification."
            } else {
                res.exceptionOrNull()?.localizedMessage ?: "Submission failed"
            }
        }
    }

    fun verifyOpaySubmission(submission: PaymentSubmission, approve: Boolean, notes: String) {
        viewModelScope.launch {
            val enr = enrolments.value.firstOrNull { it.id == submission.enrolmentId }
            val res = paymentRepository.adminVerifyOpaySubmission(currentUserId, submission, enr, approve, notes)
            if (res.isSuccess) {
                communicationRepository.logActivity(
                    currentUserId,
                    userAccount.value?.displayName ?: "Admin",
                    if (approve) "Verified OPay transfer" else "Rejected OPay transfer",
                    "PAYMENT",
                    submission.submissionRef,
                    "${submission.studentName} - ${Formatters.formatNgn(submission.amountNgn)}"
                )
                _statusBanner.value = if (approve) "Payment verified & balance updated" else "Payment submission rejected"
            } else {
                _statusBanner.value = res.exceptionOrNull()?.localizedMessage ?: "Verification error"
            }
        }
    }

    fun recordVerifiedPayment(enrolment: Enrolment, amountNgn: Long, provider: PaymentProvider, reference: String) {
        viewModelScope.launch {
            val res = paymentRepository.adminRecordVerifiedPayment(
                adminUid = currentUserId,
                enrolment = enrolment,
                amountNgn = amountNgn,
                provider = provider,
                reference = reference.ifBlank { Formatters.generateReference("ADM") }
            )
            _statusBanner.value = if (res.isSuccess) "Payment recorded & enrolment updated" else "Failed to record payment"
        }
    }

    fun recordAttendance(enrolment: Enrolment, dateIso: String, status: AttendanceStatus, notes: String) {
        viewModelScope.launch {
            val res = academicRepository.recordAttendance(currentUserId, enrolment, dateIso, status, notes)
            _statusBanner.value = if (res.isSuccess) "Attendance marked: ${enrolment.studentName} (${status.name})" else "Failed to mark attendance"
        }
    }

    fun generateQrAttendanceSession(course: Course, dateIso: String) {
        viewModelScope.launch {
            val staffName = userAccount.value?.displayName ?: "Staff"
            val res = academicRepository.createOrActivateQrSession(currentUserId, staffName, course, dateIso)
            if (res.isSuccess) {
                val sess = res.getOrThrow()
                _statusBanner.value = "Live QR session ready for ${sess.courseTitle} (${sess.dateIso})"
            } else {
                _statusBanner.value = res.exceptionOrNull()?.localizedMessage ?: "Failed to create QR session"
            }
        }
    }

    fun toggleQrAttendanceSession(session: AttendanceSession, isActive: Boolean) {
        viewModelScope.launch {
            val res = academicRepository.setQrSessionActive(session.id, isActive)
            _statusBanner.value = if (res.isSuccess) {
                if (isActive) "QR session reopened for ${session.courseTitle}"
                else "QR session closed for ${session.courseTitle}"
            } else {
                res.exceptionOrNull()?.localizedMessage ?: "Unable to update QR session"
            }
        }
    }

    fun markAttendanceFromQr(
        rawPayload: String,
        onSuccess: (AttendanceRecord) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val res = academicRepository.markAttendanceViaQrPayload(currentUserId, enrolments.value, rawPayload)
            res.onSuccess { record ->
                _statusBanner.value = "Attendance marked PRESENT for ${record.courseTitle} (${record.dateIso})"
                onSuccess(record)
            }.onFailure { err ->
                val message = err.unwrapFirestoreMessage("Could not verify QR attendance")
                onError(message)
            }
        }
    }

    fun saveAcademicResult(enrolment: Enrolment, caScore: Int, examScore: Int, comment: String) {
        viewModelScope.launch {
            val res = academicRepository.saveResult(currentUserId, enrolment, caScore, examScore, comment)
            _statusBanner.value = if (res.isSuccess) "Result saved for ${enrolment.studentName}" else "Failed to save result"
        }
    }

    fun issueCertificate(enrolment: Enrolment, grade: String) {
        viewModelScope.launch {
            val student = students.value.firstOrNull { it.id == enrolment.studentId || it.userId == enrolment.userId }
            val res = academicRepository.issueCertificate(currentUserId, enrolment, student, grade)
            if (res.isSuccess) {
                communicationRepository.logActivity(
                    currentUserId,
                    userAccount.value?.displayName ?: "Admin",
                    "Issued certificate",
                    "CERTIFICATE",
                    res.getOrNull()?.certificateNumber.orEmpty(),
                    "${enrolment.studentName} - ${enrolment.courseTitle}"
                )
                _statusBanner.value = "Certificate issued: ${res.getOrNull()?.certificateNumber}"
            } else {
                _statusBanner.value = "Certificate issuance failed"
            }
        }
    }

    fun verifyCertificate(certNumber: String) {
        viewModelScope.launch {
            _certLookupDone.value = false
            val res = academicRepository.verifyCertificateByNumber(certNumber)
            _verifiedCert.value = res.getOrNull()
            _certLookupDone.value = true
            if (res.isFailure) {
                _statusBanner.value = res.exceptionOrNull()?.localizedMessage ?: "Verification failed"
            }
        }
    }

    fun startOrOpenConversation(other: DirectoryMember, onOpened: (String) -> Unit) {
        viewModelScope.launch {
            val myName = userAccount.value?.displayName ?: "Student"
            val res = communicationRepository.getOrCreateConversation(currentUserId, myName, other)
            if (res.isSuccess) {
                onOpened(res.getOrThrow())
            } else {
                _statusBanner.value = "Could not open conversation"
            }
        }
    }

    fun observeDirectMessages(conversationId: String): Flow<List<ChatMessage>> =
        communicationRepository.observeDirectMessages(conversationId).safeCatch("dm_$conversationId", emptyList())

    fun observeGroupMessages(groupId: String): Flow<List<ChatMessage>> =
        communicationRepository.observeGroupMessages(groupId).safeCatch("grp_$groupId", emptyList())

    fun sendDirectMessage(
        conversation: Conversation,
        text: String,
        messageType: MessageType = MessageType.TEXT,
        attachmentName: String = "",
        attachmentUrl: String = ""
    ) {
        if (userAccount.value?.isSuspended == true) {
            _statusBanner.value = "Your messaging privileges are suspended by an administrator."
            return
        }
        viewModelScope.launch {
            val myName = userAccount.value?.displayName ?: "Member"
            communicationRepository.sendDirectMessage(conversation, currentUserId, myName, text, messageType, attachmentName, attachmentUrl)
        }
    }

    fun sendGroupMessage(
        groupId: String,
        text: String,
        messageType: MessageType = MessageType.TEXT,
        attachmentName: String = "",
        attachmentUrl: String = ""
    ) {
        if (userAccount.value?.isSuspended == true) {
            _statusBanner.value = "Your messaging privileges are suspended by an administrator."
            return
        }
        viewModelScope.launch {
            val myName = userAccount.value?.displayName ?: "Member"
            val res = communicationRepository.sendGroupMessage(groupId, currentUserId, myName, text, messageType, attachmentName, attachmentUrl)
            if (res.isFailure) {
                _statusBanner.value = res.exceptionOrNull()?.localizedMessage ?: "Unable to post message"
            }
        }
    }

    fun markConversationRead(conversation: Conversation) {
        viewModelScope.launch { communicationRepository.markConversationRead(conversation, currentUserId) }
    }

    fun updateTyping(conversationId: String, isTyping: Boolean) {
        viewModelScope.launch {
            communicationRepository.updateTypingStatus(conversationId, if (isTyping) currentUserId else "")
        }
    }

    fun removeGroupMessage(groupId: String, messageId: String) {
        viewModelScope.launch {
            val res = communicationRepository.removeGroupMessage(groupId, messageId)
            _statusBanner.value = if (res.isSuccess) "Message removed by moderator" else "Failed to remove message"
        }
    }

    fun blockUser(targetUid: String) {
        viewModelScope.launch {
            val res = communicationRepository.blockUser(currentUserId, targetUid)
            _statusBanner.value = if (res.isSuccess) "User blocked" else "Block failed"
        }
    }

    fun reportMessage(reportedUid: String, chatId: String, messageId: String, reason: String) {
        viewModelScope.launch {
            val res = communicationRepository.reportUserOrMessage(currentUserId, reportedUid, chatId, messageId, reason)
            _statusBanner.value = if (res.isSuccess) "Report submitted to administration" else "Report failed"
        }
    }

    fun resolveReport(reportId: String) {
        viewModelScope.launch {
            communicationRepository.resolveChatReport(reportId)
            _statusBanner.value = "Report marked resolved"
        }
    }

    fun publishAnnouncement(title: String, body: String, audience: String, courseId: String = "") {
        viewModelScope.launch {
            val name = userAccount.value?.displayName ?: "Administration"
            val res = communicationRepository.publishAnnouncement(currentUserId, name, title, body, audience, courseId)
            _statusBanner.value = if (res.isSuccess) "Announcement published" else "Failed to publish announcement"
        }
    }

    fun markNotificationRead(notificationId: String) {
        viewModelScope.launch { communicationRepository.markNotificationRead(notificationId) }
    }

    fun assignUserRole(target: UserAccount, role: UserRole, specialization: String) {
        viewModelScope.launch {
            val res = authRepository.assignRoleAndInstructor(currentUserId, target, role, specialization)
            _statusBanner.value = if (res.isSuccess) "${target.displayName} updated to ${role.name}" else "Role update failed"
        }
    }

    fun toggleUserSuspension(targetUid: String, suspend: Boolean) {
        viewModelScope.launch {
            val res = authRepository.setUserSuspended(targetUid, suspend)
            _statusBanner.value = if (res.isSuccess) {
                if (suspend) "User suspended" else "User suspension lifted"
            } else {
                "Action failed"
            }
        }
    }

    fun addInstructor(name: String, specialization: String, phone: String, email: String) {
        viewModelScope.launch {
            val res = authRepository.addInstructorProfile(
                Instructor(fullName = name, specialization = specialization, phoneNumber = phone, email = email)
            )
            _statusBanner.value = if (res.isSuccess) "Instructor added" else "Failed to add instructor"
        }
    }

    fun exportStudents(context: Context, asExcel: Boolean) {
        val headers = listOf("Student ID", "Full Name", "Gender", "Phone", "Email", "Status", "Reg Date")
        val rows = students.value.map {
            listOf(it.studentCode, it.fullName, it.gender, it.phoneNumber, it.email, it.status, it.registrationDate)
        }
        shareTableExport(context, "JY_Students_Report", "Student Registration Report", headers, rows, asExcel)
    }

    fun exportEnrolments(context: Context, asExcel: Boolean) {
        val headers = listOf("Reference", "Student", "Course", "Fee (NGN)", "Paid (NGN)", "Pay Status", "Enrolment", "Completion")
        val rows = enrolments.value.map {
            listOf(it.reference, it.studentName, it.courseTitle, it.courseFeeNgn.toString(), it.amountPaidNgn.toString(), it.paymentStatus, it.enrolmentStatus, it.completionStatus)
        }
        shareTableExport(context, "JY_Enrolments_Report", "Course Enrolment Report", headers, rows, asExcel)
    }

    fun exportAttendance(context: Context, asExcel: Boolean) {
        val headers = listOf("Date", "Student", "Course", "Status", "Notes")
        val rows = attendance.value.map {
            listOf(it.dateIso, it.studentName, it.courseTitle, it.status, it.notes)
        }
        shareTableExport(context, "JY_Attendance_Report", "Attendance Report", headers, rows, asExcel)
    }

    fun exportResults(context: Context, asExcel: Boolean) {
        val headers = listOf("Student", "Course", "CA (40)", "Exam (60)", "Total", "Grade", "Remark")
        val rows = results.value.map {
            listOf(it.studentName, it.courseTitle, it.caScore.toString(), it.examScore.toString(), it.totalScore.toString(), it.grade, it.remark)
        }
        shareTableExport(context, "JY_Results_Report", "Academic Results Report", headers, rows, asExcel)
    }

    fun exportFinancial(context: Context, asExcel: Boolean, outstandingOnly: Boolean = false) {
        if (outstandingOnly) {
            val headers = listOf("Ref", "Student", "Course", "Fee (NGN)", "Paid (NGN)", "Balance (NGN)")
            val rows = enrolments.value.filter { it.balanceNgn > 0L }.map {
                listOf(it.reference, it.studentName, it.courseTitle, it.courseFeeNgn.toString(), it.amountPaidNgn.toString(), it.balanceNgn.toString())
            }
            shareTableExport(context, "JY_Outstanding_Payments", "Outstanding Balances Report", headers, rows, asExcel)
        } else {
            val headers = listOf("Receipt", "Reference", "Student", "Course", "Amount (NGN)", "Provider", "Status")
            val rows = payments.value.map {
                listOf(it.receiptNumber, it.reference, it.studentName, it.courseTitle, it.amountNgn.toString(), it.provider, it.status)
            }
            shareTableExport(context, "JY_Financial_Report", "Financial Payments Report", headers, rows, asExcel)
        }
    }

    fun exportGraduation(context: Context, asExcel: Boolean) {
        val headers = listOf("Cert No", "Student ID", "Full Name", "Course", "Completed", "Grade")
        val rows = certificates.value.map {
            listOf(it.certificateNumber, it.studentCode, it.studentFullName, it.courseTitle, it.completionDate, it.grade)
        }
        shareTableExport(context, "JY_Graduation_Report", "Graduation & Certificates Report", headers, rows, asExcel)
    }

    private fun shareTableExport(
        context: Context,
        fileName: String,
        title: String,
        headers: List<String>,
        rows: List<List<String>>,
        asExcel: Boolean
    ) {
        runCatching {
            if (asExcel) {
                val uri = ExportUtils.generateExcelCsv(context, fileName, headers, rows)
                ExportUtils.shareExportUri(context, uri, "text/csv", "Export Excel CSV")
            } else {
                val uri = ExportUtils.generateTableReportPdf(context, fileName, title, headers, rows)
                ExportUtils.shareExportUri(context, uri, "application/pdf", "Export PDF Report")
            }
        }.onFailure { e ->
            _statusBanner.value = e.localizedMessage ?: "Export failed"
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            authRepository.setOnlineStatus(currentUserId, isOnline = false)
        }
    }

    companion object {
        fun factory(currentUserId: String): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val app = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                val dao = JySmsDatabase.getInstance(app).jySmsDao()
                val prefs = AppPreferencesRepository(app)
                return MainSessionViewModel(
                    currentUserId = currentUserId,
                    authRepository = AuthRepository(db),
                    studentRepository = StudentRepository(db),
                    courseEnrolmentRepository = CourseEnrolmentRepository(db, dao),
                    paymentRepository = PaymentRepository(db),
                    academicRepository = AcademicRepository(db, dao),
                    communicationRepository = CommunicationRepository(db, dao),
                    preferencesRepository = prefs
                ) as T
            }
        }
    }
}
