package com.example.domain.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class StudentStatus { ACTIVE, INACTIVE, GRADUATED, ARCHIVED }
enum class PaymentStatus { UNPAID, PARTIAL, PAID, REFUNDED }
enum class EnrolmentStatus { PENDING, CONFIRMED, SUSPENDED, CANCELLED }
enum class CompletionStatus { NOT_STARTED, IN_PROGRESS, COMPLETED }
enum class AttendanceStatus { PRESENT, ABSENT, LATE, EXCUSED }

@IgnoreExtraProperties
data class Student(
    @DocumentId val id: String = "",
    val userId: String = "",
    val studentCode: String = "",
    val fullName: String = "",
    val passportPhotoUri: String = "",
    val gender: String = "Male",
    val dateOfBirth: String = "",
    val phoneNumber: String = "",
    val email: String = "",
    val residentialAddress: String = "",
    val nextOfKin: String = "",
    val nextOfKinPhone: String = "",
    val registrationDate: String = "",
    val status: String = StudentStatus.ACTIVE.name,
    val additionalInfo: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Course(
    @DocumentId val id: String = "",
    val code: String = "",
    val title: String = "",
    val description: String = "",
    val durationLabel: String = "",
    val durationMonths: Long = 1L,
    val feeNgn: Long = 0L,
    val schedule: String = "Mon - Fri | 9:00 AM - 12:00 PM",
    val instructorId: String = "",
    val instructorName: String = "JY-COMPUTERS Faculty",
    val isAvailable: Boolean = true,
    val isArchived: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

object PreloadedCourses {
    val OFFICIAL_CATALOG: List<Course> = listOf(
        Course(
            id = "crs_basic",
            code = "JY-101",
            title = "Computer Training - Basic Course",
            description = "Foundational computer literacy, Windows OS, Microsoft Word, Excel, PowerPoint, internet productivity, and safe digital workflows.",
            durationLabel = "1 Month",
            durationMonths = 1L,
            feeNgn = 25_000L,
            schedule = "Mon - Fri | 9:00 AM - 11:30 AM"
        ),
        Course(
            id = "crs_certificate",
            code = "JY-102",
            title = "Certificate Course",
            description = "Comprehensive office productivity, desktop publishing, spreadsheet modeling, and practical computer operations certification.",
            durationLabel = "2 Months",
            durationMonths = 2L,
            feeNgn = 47_000L,
            schedule = "Mon - Fri | 10:00 AM - 1:00 PM"
        ),
        Course(
            id = "crs_diploma_1",
            code = "JY-201",
            title = "Diploma 1 Course",
            description = "Intermediate ICT diploma covering advanced office suites, graphic design fundamentals, database management, and web basics.",
            durationLabel = "3 Months",
            durationMonths = 3L,
            feeNgn = 50_000L,
            schedule = "Mon - Fri | 12:00 PM - 3:00 PM"
        ),
        Course(
            id = "crs_diploma_2",
            code = "JY-202",
            title = "Diploma 2 Course",
            description = "Full-stack professional IT diploma including advanced data analysis, networking essentials, UI design, and capstone project.",
            durationLabel = "6 Months",
            durationMonths = 6L,
            feeNgn = 80_000L,
            schedule = "Mon - Fri | 9:00 AM - 1:00 PM"
        ),
        Course(
            id = "crs_repairs",
            code = "JY-301",
            title = "Computer Repairs",
            description = "Hands-on PC & laptop hardware assembly, component replacement, BIOS configuration, OS installation, and preventive maintenance.",
            durationLabel = "6 Months",
            durationMonths = 6L,
            feeNgn = 200_000L,
            schedule = "Mon - Thu | 10:00 AM - 2:00 PM"
        ),
        Course(
            id = "crs_adv_repairs",
            code = "JY-302",
            title = "Advanced Repairs",
            description = "Chip-level motherboard diagnostics, micro-soldering, schematic reading, power rail tracing, and enterprise workstation recovery.",
            durationLabel = "1 Year",
            durationMonths = 12L,
            feeNgn = 350_000L,
            schedule = "Mon - Fri | 11:00 AM - 3:00 PM"
        ),
        Course(
            id = "crs_troubleshooting",
            code = "JY-303",
            title = "Understanding Computer Troubleshooting",
            description = "Systematic hardware/software fault isolation, driver diagnostics, data recovery, network repair, and malware remediation.",
            durationLabel = "6 Months",
            durationMonths = 6L,
            feeNgn = 250_000L,
            schedule = "Tue - Fri | 10:00 AM - 2:00 PM"
        ),
        Course(
            id = "crs_coding",
            code = "JY-401",
            title = "Coding",
            description = "Software engineering bootcamp covering Python, JavaScript, web applications, databases, Git workflows, and algorithmic problem solving.",
            durationLabel = "6 Months",
            durationMonths = 6L,
            feeNgn = 450_000L,
            schedule = "Mon - Fri | 10:00 AM - 2:00 PM"
        ),
        Course(
            id = "crs_android",
            code = "JY-402",
            title = "Android App Development",
            description = "Modern Kotlin, Jetpack Compose, Material Design 3, Firebase cloud architecture, Room persistence, and Google Play deployment.",
            durationLabel = "6 Months (Configurable)",
            durationMonths = 6L,
            feeNgn = 450_000L,
            schedule = "Mon - Fri | 1:00 PM - 4:30 PM"
        )
    )
}

@IgnoreExtraProperties
data class Enrolment(
    @DocumentId val id: String = "",
    val reference: String = "",
    val userId: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val courseId: String = "",
    val courseTitle: String = "",
    val courseFeeNgn: Long = 0L,
    val amountPaidNgn: Long = 0L,
    val instructorId: String = "",
    val instructorName: String = "",
    val registrationDate: String = "",
    val startDate: String = "",
    val expectedCompletionDate: String = "",
    val paymentStatus: String = PaymentStatus.UNPAID.name,
    val enrolmentStatus: String = EnrolmentStatus.PENDING.name,
    val completionStatus: String = CompletionStatus.NOT_STARTED.name,
    val instalmentPlan: String = "Full Payment",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val balanceNgn: Long get() = (courseFeeNgn - amountPaidNgn).coerceAtLeast(0L)
}

@IgnoreExtraProperties
data class AttendanceRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val courseId: String = "",
    val courseTitle: String = "",
    val dateIso: String = "",
    val status: String = AttendanceStatus.PRESENT.name,
    val recordedByUid: String = "",
    val sessionId: String = "",
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AttendanceSession(
    @DocumentId val id: String = "",
    val sessionCode: String = "",
    val courseId: String = "",
    val courseTitle: String = "",
    val dateIso: String = "",
    val createdByUid: String = "",
    val createdByName: String = "",
    val isActive: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val qrPayload: String
        get() = AttendanceQrParser.buildPayload(id, courseId, dateIso)
}

data class ParsedAttendanceQr(
    val sessionId: String,
    val courseId: String,
    val dateIso: String
)

object AttendanceQrParser {
    private const val PREFIX = "JYSMS_ATT"

    fun buildPayload(sessionId: String, courseId: String, dateIso: String): String =
        "$PREFIX|$sessionId|$courseId|$dateIso"

    fun parse(rawValue: String): ParsedAttendanceQr? {
        val trimmed = rawValue.trim()
        val parts = trimmed.split("|")
        if (parts.size != 4 || parts[0] != PREFIX) return null
        val sessionId = parts[1].trim()
        val courseId = parts[2].trim()
        val dateIso = parts[3].trim()
        if (sessionId.isBlank() || courseId.isBlank() || dateIso.isBlank()) return null
        return ParsedAttendanceQr(sessionId = sessionId, courseId = courseId, dateIso = dateIso)
    }
}

@IgnoreExtraProperties
data class AcademicResult(
    @DocumentId val id: String = "",
    val userId: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val courseId: String = "",
    val courseTitle: String = "",
    val caScore: Long = 0L,
    val examScore: Long = 0L,
    val totalScore: Long = 0L,
    val grade: String = "F",
    val remark: String = "",
    val instructorComment: String = "",
    val enteredByUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Certificate(
    @DocumentId val id: String = "",
    val certificateNumber: String = "",
    val userId: String = "",
    val studentId: String = "",
    val studentCode: String = "",
    val studentFullName: String = "",
    val courseId: String = "",
    val courseTitle: String = "",
    val completionDate: String = "",
    val grade: String = "",
    val issuedByUid: String = "",
    val isRevoked: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CertificateVerification(
    @DocumentId val id: String = "",
    val certificateNumber: String = "",
    val studentFullName: String = "",
    val studentCode: String = "",
    val courseTitle: String = "",
    val completionDate: String = "",
    val isValid: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null
)
